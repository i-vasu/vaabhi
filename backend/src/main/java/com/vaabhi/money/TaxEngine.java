package com.vaabhi.money;

import com.vaabhi.shared.rupees.Rupees;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Turns invoice lines into taxed lines: spreads the order discount over the goods, looks up each piece's rate
 * (which applies any price slab), splits GST into CGST/SGST or IGST, and totals it. Pure: no database, no Spring.
 */
final class TaxEngine {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final String SHIPPING_SAC = "9965";

    private TaxEngine() {}

    /** Taxable value, CGST, SGST and what the customer pays, for an amount at a GST rate (percent). */
    record Tax(BigDecimal taxableRupees, BigDecimal cgstRupees, BigDecimal sgstRupees, BigDecimal totalRupees) {}

    record TaxInput(
            String description,
            int quantity,
            BigDecimal unitRupees,
            @Nullable Long taxClassId,
            @Nullable Long webOrderLineId,
            boolean shipping) {}

    record TaxedLine(
            TaxInput input,
            BigDecimal amountRupees,
            BigDecimal discountRupees,
            @Nullable String hsnCode,
            BigDecimal gstRate,
            BigDecimal taxableRupees,
            BigDecimal cgstRupees,
            BigDecimal sgstRupees,
            BigDecimal igstRupees) {}

    enum SupplyType {
        INTRA,
        INTER
    }

    record TaxResult(
            List<TaxedLine> lines,
            SupplyType supplyType,
            BigDecimal taxableRupees,
            BigDecimal cgstRupees,
            BigDecimal sgstRupees,
            BigDecimal igstRupees,
            BigDecimal totalRupees,
            BigDecimal highestRate) {}

    /** Finds the code and rate for a piece; null when it has no tax class and there is no default. */
    interface RateLookup {
        @Nullable
        TaxRate resolve(@Nullable Long classId, BigDecimal unitNetRupees, boolean inclusive);
    }

    /**
     * Splits {@code amountRupees} into taxable value and GST. When prices include tax the amount stays what the
     * customer pays; otherwise GST is added on top. The tax is halved into CGST and SGST (SGST takes the odd paisa).
     */
    static Tax lineTax(BigDecimal amountRupees, BigDecimal ratePercent, boolean inclusive) {
        var amount = Rupees.of(amountRupees);
        if (ratePercent.signum() == 0) {
            return new Tax(amount, Rupees.ZERO, Rupees.ZERO, amount);
        }
        BigDecimal taxable;
        BigDecimal gst;
        if (inclusive) {
            taxable = amount.multiply(HUNDRED).divide(HUNDRED.add(ratePercent), Rupees.SCALE, RoundingMode.HALF_UP);
            gst = amount.subtract(taxable);
        } else {
            taxable = amount;
            gst = Rupees.percentOf(amount, ratePercent);
        }
        var cgst = gst.divide(TWO, Rupees.SCALE, RoundingMode.DOWN);
        return new Tax(taxable, cgst, gst.subtract(cgst), taxable.add(gst));
    }

    /**
     * Computes the taxed lines for an invoice. {@code chargedRupees} is what the customer was charged for all
     * lines; anything below the sum of the lines is a discount, spread over the goods (not shipping) by amount.
     * Shipping takes the highest goods rate. When a line's GST has an odd paisa, SGST takes it on the 1st, 3rd, ...
     * such line and CGST on the 2nd, 4th, ... (in line order), so the invoice's CGST and SGST differ by at most
     * 0.01. The supply is INTER only when both state codes are known and differ.
     */
    static TaxResult compute(
            List<TaxInput> lines,
            BigDecimal chargedRupees,
            @Nullable String sellerStateCode,
            @Nullable String buyerStateCode,
            boolean gstApplies,
            boolean pricesIncludeTax,
            RateLookup rates) {
        var amounts = new ArrayList<BigDecimal>();
        for (var line : lines) {
            amounts.add(Rupees.times(line.unitRupees(), line.quantity()));
        }
        var gross = Rupees.sum(amounts, a -> a);
        var discount = Rupees.max(Rupees.ZERO, gross.subtract(Rupees.of(chargedRupees)));
        var discounts = spreadDiscount(lines, amounts, discount);

        boolean inter = sellerStateCode != null && buyerStateCode != null && !sellerStateCode.equals(buyerStateCode);
        var supplyType = inter ? SupplyType.INTER : SupplyType.INTRA;

        var hsns = new ArrayList<@Nullable String>();
        var goodsRates = new ArrayList<BigDecimal>();
        var highest = BigDecimal.ZERO;
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            if (line.shipping()) {
                hsns.add(null);
                goodsRates.add(BigDecimal.ZERO);
                continue;
            }
            var net = amounts.get(i).subtract(discounts.get(i));
            var unitNet = net.divide(
                    BigDecimal.valueOf(Math.max(line.quantity(), 1)), Rupees.SCALE + 2, RoundingMode.HALF_UP);
            var found = rates.resolve(line.taxClassId(), unitNet, pricesIncludeTax);
            String hsn = null;
            var rate = BigDecimal.ZERO;
            if (found != null) {
                hsn = found.hsnCode();
                rate = gstApplies ? found.ratePercent() : BigDecimal.ZERO;
            } else if (gstApplies) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Set a default tax class in Settings → Billing");
            }
            if (rate.compareTo(highest) > 0) {
                highest = rate;
            }
            hsns.add(hsn);
            goodsRates.add(rate);
        }

        var result = new ArrayList<TaxedLine>();
        var oddPaisa = new int[1];
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            result.add(
                    line.shipping()
                            ? taxedLine(
                                    line,
                                    amounts.get(i),
                                    discounts.get(i),
                                    SHIPPING_SAC,
                                    highest,
                                    pricesIncludeTax,
                                    inter,
                                    oddPaisa)
                            : taxedLine(
                                    line,
                                    amounts.get(i),
                                    discounts.get(i),
                                    hsns.get(i),
                                    goodsRates.get(i),
                                    pricesIncludeTax,
                                    inter,
                                    oddPaisa));
        }

        var taxable = Rupees.sum(result, TaxedLine::taxableRupees);
        var cgst = Rupees.sum(result, TaxedLine::cgstRupees);
        var sgst = Rupees.sum(result, TaxedLine::sgstRupees);
        var igst = Rupees.sum(result, TaxedLine::igstRupees);
        var total = taxable.add(cgst).add(sgst).add(igst);
        return new TaxResult(List.copyOf(result), supplyType, taxable, cgst, sgst, igst, total, highest);
    }

    private static TaxedLine taxedLine(
            TaxInput line,
            BigDecimal amount,
            BigDecimal discount,
            @Nullable String hsn,
            BigDecimal rate,
            boolean inclusive,
            boolean inter,
            int[] oddPaisa) {
        var tax = lineTax(amount.subtract(discount), rate, inclusive);
        var gst = tax.cgstRupees().add(tax.sgstRupees());
        if (!inter && gst.movePointRight(Rupees.SCALE).toBigInteger().testBit(0) && ++oddPaisa[0] % 2 == 0) {
            var cgst = tax.sgstRupees();
            return new TaxedLine(
                    line, amount, discount, hsn, rate, tax.taxableRupees(), cgst, gst.subtract(cgst), Rupees.ZERO);
        }
        if (inter) {
            return new TaxedLine(
                    line,
                    amount,
                    discount,
                    hsn,
                    rate,
                    tax.taxableRupees(),
                    Rupees.ZERO,
                    Rupees.ZERO,
                    tax.cgstRupees().add(tax.sgstRupees()));
        }
        return new TaxedLine(
                line,
                amount,
                discount,
                hsn,
                rate,
                tax.taxableRupees(),
                tax.cgstRupees(),
                tax.sgstRupees(),
                Rupees.ZERO);
    }

    /**
     * Each goods line's share of the discount, by amount, to the paisa; the last goods line takes the remainder.
     * Goods take at most their total; any overflow goes onto the shipping lines (capped at their amount).
     */
    private static List<BigDecimal> spreadDiscount(
            List<TaxInput> lines, List<BigDecimal> amounts, BigDecimal discount) {
        var shares = new ArrayList<BigDecimal>();
        int last = -1;
        var goodsTotal = BigDecimal.ZERO;
        for (int i = 0; i < lines.size(); i++) {
            shares.add(Rupees.ZERO);
            if (!lines.get(i).shipping()) {
                last = i;
                goodsTotal = goodsTotal.add(amounts.get(i));
            }
        }
        if (discount.signum() == 0) {
            return shares;
        }
        var goodsDiscount = Rupees.min(discount, goodsTotal);
        var given = BigDecimal.ZERO;
        if (last >= 0 && goodsDiscount.signum() > 0) {
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).shipping()) {
                    continue;
                }
                var share = i == last
                        ? goodsDiscount.subtract(given)
                        : goodsDiscount.multiply(amounts.get(i)).divide(goodsTotal, Rupees.SCALE, RoundingMode.HALF_UP);
                share = Rupees.max(Rupees.ZERO, Rupees.min(share, amounts.get(i)));
                shares.set(i, share);
                given = given.add(share);
            }
        }
        var overflow = discount.subtract(given);
        for (int i = 0; i < lines.size() && overflow.signum() > 0; i++) {
            if (!lines.get(i).shipping()) {
                continue;
            }
            var share = Rupees.min(overflow, amounts.get(i));
            shares.set(i, share);
            overflow = overflow.subtract(share);
        }
        return shares;
    }
}
