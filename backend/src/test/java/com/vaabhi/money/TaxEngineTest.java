package com.vaabhi.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vaabhi.money.TaxEngine.RateLookup;
import com.vaabhi.money.TaxEngine.SupplyType;
import com.vaabhi.money.TaxEngine.TaxInput;
import com.vaabhi.money.TaxEngine.TaxResult;
import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/** The pure GST engine: slabs, discount spread, supply type, shipping and GST off. */
class TaxEngineTest {

    private static final long GARMENTS = 1;
    private static final long FLAT_18 = 2;

    /** Garments: 5 % up to 2500 a piece, 18 % above; class 2 is a flat 18 %; no class means no rate. */
    private static final RateLookup RATES = (classId, unitNet, inclusive) -> {
        if (classId == null) {
            return null;
        }
        if (classId == FLAT_18) {
            return new TaxRate("6205", new BigDecimal("18"), TaxClass.Kind.GOODS);
        }
        var rate = unitNet.compareTo(new BigDecimal("2500")) > 0 ? "18" : "5";
        return new TaxRate("6204", new BigDecimal(rate), TaxClass.Kind.GOODS);
    };

    @Test
    void exactlyAtLimitIsLowerRate() {
        var result = compute(List.of(goods(1, "2500.00")), "2500.00", false, true);

        assertThat(result.lines().getFirst().gstRate()).isEqualByComparingTo("5");
        assertThat(result.taxableRupees()).isEqualByComparingTo("2500.00");
        assertThat(result.cgstRupees().add(result.sgstRupees())).isEqualByComparingTo("125.00");
    }

    @Test
    void justAboveLimitIsHigherRate() {
        var result = compute(List.of(goods(1, "2500.01")), "2500.01", false, true);

        assertThat(result.lines().getFirst().gstRate()).isEqualByComparingTo("18");
    }

    @Test
    void discountCanMoveLineToLowerSlab() {
        var result = compute(List.of(goods(1, "2600.00")), "2400.00", false, true);

        var line = result.lines().getFirst();
        assertThat(line.discountRupees()).isEqualByComparingTo("200.00");
        assertThat(line.gstRate()).isEqualByComparingTo("5");
    }

    @Test
    void discountSpreadSumsToCharged() {
        var result =
                compute(List.of(goods(1, "1000.00"), goods(1, "500.00"), goods(1, "300.00")), "1700.00", false, false);

        var discounts = result.lines().stream()
                .map(TaxEngine.TaxedLine::discountRupees)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var lineTaxable = result.lines().stream()
                .map(TaxEngine.TaxedLine::taxableRupees)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(discounts).isEqualByComparingTo("100.00");
        assertThat(lineTaxable).isEqualByComparingTo("1700.00");
        assertThat(result.taxableRupees()).isEqualByComparingTo("1700.00");
        assertThat(result.totalRupees()).isEqualByComparingTo("1700.00");
    }

    @Test
    void interStateIsIgst() {
        var result = compute(List.of(goods(1, "1000.00")), "1000.00", false, true, "36", "29");

        assertThat(result.supplyType()).isEqualTo(SupplyType.INTER);
        assertThat(result.cgstRupees()).isEqualByComparingTo("0.00");
        assertThat(result.sgstRupees()).isEqualByComparingTo("0.00");
        assertThat(result.igstRupees()).isEqualByComparingTo("50.00");
        assertThat(result.totalRupees()).isEqualByComparingTo("1050.00");
    }

    @Test
    void sameStateSplitsWithOddPaisaToSgst() {
        var result = compute(List.of(goods(1, "105.01")), "105.01", true, true, "36", "36");

        var line = result.lines().getFirst();
        var gst = line.amountRupees().subtract(line.taxableRupees());
        assertThat(line.cgstRupees().add(line.sgstRupees())).isEqualByComparingTo(gst);
        var diff = line.sgstRupees().subtract(line.cgstRupees());
        assertThat(diff.compareTo(BigDecimal.ZERO) == 0 || diff.compareTo(new BigDecimal("0.01")) == 0)
                .isTrue();
        assertThat(result.supplyType()).isEqualTo(SupplyType.INTRA);
    }

    @Test
    void unknownBuyerStateIsIntra() {
        var result = compute(List.of(goods(1, "1000.00")), "1000.00", false, true, "36", null);

        assertThat(result.supplyType()).isEqualTo(SupplyType.INTRA);
        assertThat(result.igstRupees()).isEqualByComparingTo("0.00");
    }

    @Test
    void shippingTakesHighestRate() {
        var lines = List.of(
                goods(1, "1000.00"),
                new TaxInput("Kurta", 1, new BigDecimal("1000.00"), FLAT_18, null, false),
                new TaxInput("Shipping", 1, new BigDecimal("100.00"), null, null, true));

        var result = compute(lines, "2100.00", false, true);

        var shipping = result.lines().get(2);
        assertThat(shipping.gstRate()).isEqualByComparingTo("18");
        assertThat(shipping.hsnCode()).isEqualTo("9965");
        assertThat(result.highestRate()).isEqualByComparingTo("18");
    }

    @Test
    void gstOffGivesZeroTaxButKeepsHsn() {
        var result = compute(List.of(goods(2, "500.00")), "1000.00", false, false);

        var line = result.lines().getFirst();
        assertThat(line.hsnCode()).isEqualTo("6204");
        assertThat(line.gstRate()).isEqualByComparingTo("0");
        assertThat(result.cgstRupees()).isEqualByComparingTo("0.00");
        assertThat(result.sgstRupees()).isEqualByComparingTo("0.00");
        assertThat(result.igstRupees()).isEqualByComparingTo("0.00");
        assertThat(result.totalRupees()).isEqualByComparingTo("1000.00");
    }

    @Test
    void noClassWithGstOnFails() {
        var lines = List.of(new TaxInput("Kurta", 1, new BigDecimal("100.00"), null, null, false));

        assertThatThrownBy(() -> TaxEngine.compute(lines, new BigDecimal("100.00"), null, null, true, false, RATES))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Set a default tax class");
    }

    @Test
    void noClassWithGstOffIsZero() {
        var lines = List.of(new TaxInput("Kurta", 1, new BigDecimal("100.00"), null, null, false));

        var result = TaxEngine.compute(lines, new BigDecimal("100.00"), null, null, false, false, RATES);

        var line = result.lines().getFirst();
        assertThat(line.hsnCode()).isNull();
        assertThat(line.gstRate()).isEqualByComparingTo("0");
        assertThat(result.totalRupees()).isEqualByComparingTo("100.00");
    }

    @Test
    void singleRateMatchesOldTax() {
        var result = compute(List.of(goods(1, "1050.00")), "1050.00", true, true);

        var line = result.lines().getFirst();
        var expected = TaxEngine.lineTax(new BigDecimal("1050"), new BigDecimal("5"), true);
        assertThat(expected)
                .isEqualTo(new TaxEngine.Tax(
                        new BigDecimal("1000.00"),
                        new BigDecimal("25.00"),
                        new BigDecimal("25.00"),
                        new BigDecimal("1050.00")));
        assertThat(line.taxableRupees()).isEqualTo(expected.taxableRupees());
        assertThat(line.cgstRupees()).isEqualTo(expected.cgstRupees());
        assertThat(line.sgstRupees()).isEqualTo(expected.sgstRupees());
        assertThat(result.totalRupees()).isEqualTo(expected.totalRupees());
    }

    private static TaxInput goods(int quantity, String unit) {
        return new TaxInput("Kurta", quantity, new BigDecimal(unit), GARMENTS, null, false);
    }

    private static TaxResult compute(List<TaxInput> lines, String charged, boolean inclusive, boolean gstApplies) {
        return compute(lines, charged, inclusive, gstApplies, null, null);
    }

    private static TaxResult compute(
            List<TaxInput> lines,
            String charged,
            boolean inclusive,
            boolean gstApplies,
            @Nullable String sellerState,
            @Nullable String buyerState) {
        return TaxEngine.compute(lines, new BigDecimal(charged), sellerState, buyerState, gstApplies, inclusive, RATES);
    }
}
