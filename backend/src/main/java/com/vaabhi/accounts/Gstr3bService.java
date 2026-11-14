package com.vaabhi.accounts;

import com.vaabhi.money.Gstr1;
import com.vaabhi.money.Gstr1Service;
import com.vaabhi.shared.rupees.Rupees;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

/**
 * GSTR-3B for a month: outward supplies and inter-state supplies from GSTR-1, input credit from supplier bills and
 * Razorpay gateway fees, and how that credit is set off against the output tax.
 */
@Service
public class Gstr3bService {

    private static final String PLACE_OF_SUPPLY = "Place Of Supply";
    private static final String TAXABLE_DESCRIPTION =
            "Outward taxable supplies (other than zero rated, nil rated and exempted)";
    private static final String NIL_DESCRIPTION = "Other outward supplies (nil rated, exempted)";
    private static final String ITC_DESCRIPTION = "All other ITC";

    private final Gstr1Service gstr1;
    private final InputGstReport inputGst;

    Gstr3bService(Gstr1Service gstr1, InputGstReport inputGst) {
        this.gstr1 = gstr1;
        this.inputGst = inputGst;
    }

    public Gstr3b month(YearMonth month) {
        var g = gstr1.month(month);

        // 3.1: taxable outward supplies net of credit notes, and the nil-rated part.
        var taxable = new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        var nil = new BigDecimal[] {BigDecimal.ZERO};
        for (var row : g.b2b()) {
            addOutward(taxable, nil, row, 1);
        }
        for (var row : g.b2cl()) {
            addOutward(taxable, nil, row, 1);
        }
        for (var row : g.b2cs()) {
            addOutward(taxable, nil, row, 1);
        }
        for (var row : g.cdnr()) {
            addOutward(taxable, nil, row, -1);
        }
        for (var row : g.cdnur()) {
            addOutward(taxable, nil, row, -1);
        }
        var outward = List.of(
                new Gstr3bRow(
                        "3.1(a)",
                        TAXABLE_DESCRIPTION,
                        Rupees.of(taxable[0]),
                        Rupees.of(taxable[1]),
                        Rupees.of(taxable[2]),
                        Rupees.of(taxable[3])),
                new Gstr3bRow("3.1(c)", NIL_DESCRIPTION, Rupees.of(nil[0]), Rupees.ZERO, Rupees.ZERO, Rupees.ZERO));

        // 3.2: inter-state supplies to unregistered buyers, place by place, net of credit notes.
        Map<String, BigDecimal[]> places = new TreeMap<>();
        for (var row : g.b2cl()) {
            addPlace(places, row, 1);
        }
        for (var row : g.b2cs()) {
            addPlace(places, row, 1);
        }
        for (var row : g.cdnur()) {
            addPlace(places, row, -1);
        }
        List<Gstr3bRow> interState = new ArrayList<>();
        places.forEach((place, sums) -> {
            if (sums[0].signum() == 0 && sums[1].signum() == 0) {
                return;
            }
            interState.add(
                    new Gstr3bRow("3.2", place, Rupees.of(sums[0]), Rupees.of(sums[1]), Rupees.ZERO, Rupees.ZERO));
        });

        // 4(A)(5): input credit on supplier bills plus the GST inside Razorpay fees (an IGST).
        var months = inputGst.months(month.atDay(1), month.atEndOfMonth());
        Gstr3bRow itc;
        if (months.isEmpty()) {
            itc = new Gstr3bRow("4(A)(5)", ITC_DESCRIPTION, Rupees.ZERO, Rupees.ZERO, Rupees.ZERO, Rupees.ZERO);
        } else {
            var input = months.getFirst();
            itc = new Gstr3bRow(
                    "4(A)(5)",
                    ITC_DESCRIPTION,
                    Rupees.of(input.taxableRupees()),
                    Rupees.of(input.igstRupees().add(input.gatewayGstRupees())),
                    Rupees.of(input.cgstRupees()),
                    Rupees.of(input.sgstRupees()));
        }

        var setOff = setOff(taxable[1], taxable[2], taxable[3], itc.igstRupees(), itc.cgstRupees(), itc.sgstRupees());
        return new Gstr3b(month, g.gstOn(), outward, interState, itc, setOff);
    }

    /** Adds one GSTR-1 row to the 3.1 totals; credit notes are netted off, never added. */
    private static void addOutward(BigDecimal[] taxable, BigDecimal[] nil, Gstr1.Gstr1Row row, int sign) {
        var rowTaxable = signed(row.taxable(), sign);
        var rowIgst = signed(row.igst(), sign);
        var rowCgst = signed(row.cgst(), sign);
        var rowSgst = signed(row.sgst(), sign);
        if (rowIgst.add(rowCgst).add(rowSgst).signum() == 0) {
            nil[0] = nil[0].add(rowTaxable);
        } else {
            taxable[0] = taxable[0].add(rowTaxable);
            taxable[1] = taxable[1].add(rowIgst);
            taxable[2] = taxable[2].add(rowCgst);
            taxable[3] = taxable[3].add(rowSgst);
        }
    }

    /** Adds one inter-state row to its place of supply; only rows that charged IGST count. */
    private static void addPlace(Map<String, BigDecimal[]> places, Gstr1.Gstr1Row row, int sign) {
        if (row.igst().signum() <= 0) {
            return;
        }
        var place = String.valueOf(row.columns().get(PLACE_OF_SUPPLY));
        var sums = places.computeIfAbsent(place, p -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
        sums[0] = sums[0].add(signed(row.taxable(), sign));
        sums[1] = sums[1].add(signed(row.igst(), sign));
    }

    /** Credit-note rows are positive in GSTR-1; net them off by their absolute amounts. */
    private static BigDecimal signed(BigDecimal amount, int sign) {
        return sign > 0 ? amount : amount.abs().negate();
    }

    /**
     * Sets the input credit off against the output tax. IGST credit pays IGST first, then CGST, then SGST; CGST credit
     * pays CGST then IGST; SGST credit pays SGST then IGST. CGST credit never pays SGST and vice versa.
     */
    public static List<Gstr3bSetOff> setOff(
            BigDecimal outIgst,
            BigDecimal outCgst,
            BigDecimal outSgst,
            BigDecimal itcIgst,
            BigDecimal itcCgst,
            BigDecimal itcSgst) {
        var outputIgst = atLeastZero(outIgst);
        var outputCgst = atLeastZero(outCgst);
        var outputSgst = atLeastZero(outSgst);
        var remainingIgst = outputIgst;
        var remainingCgst = outputCgst;
        var remainingSgst = outputSgst;
        var availableIgst = atLeastZero(itcIgst);
        var availableCgst = atLeastZero(itcCgst);
        var availableSgst = atLeastZero(itcSgst);

        var used = Rupees.min(availableIgst, remainingIgst);
        availableIgst = availableIgst.subtract(used);
        remainingIgst = remainingIgst.subtract(used);
        used = Rupees.min(availableIgst, remainingCgst);
        availableIgst = availableIgst.subtract(used);
        remainingCgst = remainingCgst.subtract(used);
        used = Rupees.min(availableIgst, remainingSgst);
        availableIgst = availableIgst.subtract(used);
        remainingSgst = remainingSgst.subtract(used);

        used = Rupees.min(availableCgst, remainingCgst);
        availableCgst = availableCgst.subtract(used);
        remainingCgst = remainingCgst.subtract(used);
        used = Rupees.min(availableCgst, remainingIgst);
        availableCgst = availableCgst.subtract(used);
        remainingIgst = remainingIgst.subtract(used);

        used = Rupees.min(availableSgst, remainingSgst);
        availableSgst = availableSgst.subtract(used);
        remainingSgst = remainingSgst.subtract(used);
        used = Rupees.min(availableSgst, remainingIgst);
        availableSgst = availableSgst.subtract(used);
        remainingIgst = remainingIgst.subtract(used);

        return List.of(
                new Gstr3bSetOff(
                        "IGST",
                        outputIgst,
                        Rupees.of(outputIgst.subtract(remainingIgst)),
                        Rupees.of(remainingIgst),
                        Rupees.of(availableIgst)),
                new Gstr3bSetOff(
                        "CGST",
                        outputCgst,
                        Rupees.of(outputCgst.subtract(remainingCgst)),
                        Rupees.of(remainingCgst),
                        Rupees.of(availableCgst)),
                new Gstr3bSetOff(
                        "SGST",
                        outputSgst,
                        Rupees.of(outputSgst.subtract(remainingSgst)),
                        Rupees.of(remainingSgst),
                        Rupees.of(availableSgst)));
    }

    private static BigDecimal atLeastZero(BigDecimal amount) {
        return amount.signum() < 0 ? Rupees.ZERO : Rupees.of(amount);
    }

    /** The month as a CSV the owner can open in a spreadsheet. */
    public String csv(YearMonth month) {
        var g = month(month);
        var out = new StringBuilder();
        line(out, "section", "description", "taxable", "igst", "cgst", "sgst");
        for (var row : g.outward()) {
            rowCsv(out, row);
        }
        for (var row : g.interState()) {
            rowCsv(out, row);
        }
        rowCsv(out, g.itc());
        for (var row : g.setOff()) {
            var igst = "IGST".equals(row.head()) ? amount(row.cashRupees()) : amount(Rupees.ZERO);
            var cgst = "CGST".equals(row.head()) ? amount(row.cashRupees()) : amount(Rupees.ZERO);
            var sgst = "SGST".equals(row.head()) ? amount(row.cashRupees()) : amount(Rupees.ZERO);
            line(out, "6.1", row.head() + " cash", "", igst, cgst, sgst);
        }
        return out.toString();
    }

    private static void rowCsv(StringBuilder out, Gstr3bRow row) {
        line(
                out,
                row.section(),
                row.description(),
                amount(row.taxableRupees()),
                amount(row.igstRupees()),
                amount(row.cgstRupees()),
                amount(row.sgstRupees()));
    }

    private static String amount(BigDecimal amount) {
        return Rupees.of(amount).toPlainString();
    }

    private static void line(StringBuilder out, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(field(cells[i]));
        }
        out.append('\n');
    }

    private static String field(String text) {
        if (text.contains(",") || text.contains("\"")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
