package com.vaabhi.accounts;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class DepreciationMathTest {

    private static final BigDecimal FIVE_PERCENT = new BigDecimal("5");

    private static void assertAmounts(List<DepreciationMath.MonthCharge> charges, String... amounts) {
        assertThat(charges).hasSize(amounts.length);
        for (int i = 0; i < amounts.length; i++) {
            assertThat(charges.get(i).amount()).isEqualByComparingTo(amounts[i]);
        }
    }

    @Test
    void firstMonthProRata() {
        var charges = DepreciationMath.schedule(
                new BigDecimal("120000"),
                FIVE_PERCENT,
                10,
                BigDecimal.ZERO,
                LocalDate.of(2026, 4, 16),
                LocalDate.of(2026, 4, 1),
                YearMonth.of(2026, 6),
                null);

        assertThat(charges)
                .extracting(DepreciationMath.MonthCharge::month)
                .containsExactly(YearMonth.of(2026, 4), YearMonth.of(2026, 5), YearMonth.of(2026, 6));
        assertAmounts(charges, "475.00", "950.00", "950.00");
    }

    @Test
    void disposalMonthProRata() {
        var charges = DepreciationMath.schedule(
                new BigDecimal("120000"),
                FIVE_PERCENT,
                10,
                BigDecimal.ZERO,
                LocalDate.of(2026, 4, 16),
                LocalDate.of(2026, 4, 1),
                YearMonth.of(2026, 12),
                LocalDate.of(2026, 7, 10));

        assertThat(charges).hasSize(4);
        assertThat(charges.getLast().month()).isEqualTo(YearMonth.of(2026, 7));
        assertThat(charges.getLast().amount()).isEqualByComparingTo("306.45");
    }

    @Test
    void capsAtResidual() {
        var charges = DepreciationMath.schedule(
                new BigDecimal("1000"),
                FIVE_PERCENT,
                1,
                BigDecimal.ZERO,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 1),
                YearMonth.of(2027, 6),
                null);

        assertThat(charges).hasSize(12);
        assertThat(charges.getLast().month()).isEqualTo(YearMonth.of(2027, 3));
        for (int i = 0; i < 11; i++) {
            assertThat(charges.get(i).amount()).isEqualByComparingTo("79.17");
        }
        assertThat(charges.get(11).amount()).isEqualByComparingTo("79.13");
        assertThat(charges.stream()
                        .map(DepreciationMath.MonthCharge::amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("950.00");
    }

    @Test
    void openingAccdepCountsTowardCap() {
        var charges = DepreciationMath.schedule(
                new BigDecimal("120000"),
                FIVE_PERCENT,
                10,
                new BigDecimal("113500"),
                LocalDate.of(2020, 1, 1),
                LocalDate.of(2026, 4, 1),
                YearMonth.of(2026, 6),
                null);

        assertAmounts(charges, "500.00");
        assertThat(charges.getFirst().month()).isEqualTo(YearMonth.of(2026, 4));
    }

    @Test
    void openingAlreadyFullyDepreciated() {
        var charges = DepreciationMath.schedule(
                new BigDecimal("120000"),
                FIVE_PERCENT,
                10,
                new BigDecimal("114000"),
                LocalDate.of(2020, 1, 1),
                LocalDate.of(2026, 4, 1),
                YearMonth.of(2026, 6),
                null);

        assertThat(charges).isEmpty();
    }

    @Test
    void startsAtBooksStartNotPutToUse() {
        var charges = DepreciationMath.schedule(
                new BigDecimal("120000"),
                FIVE_PERCENT,
                10,
                BigDecimal.ZERO,
                LocalDate.of(2025, 1, 10),
                LocalDate.of(2026, 4, 16),
                YearMonth.of(2026, 6),
                null);

        assertThat(charges.getFirst().month()).isEqualTo(YearMonth.of(2026, 4));
        assertAmounts(charges, "475.00", "950.00", "950.00");
    }
}
