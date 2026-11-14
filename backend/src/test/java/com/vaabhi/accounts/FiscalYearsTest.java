package com.vaabhi.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class FiscalYearsTest {

    @Test
    void yearStartsOnFirstOfApril() {
        assertThat(FiscalYears.startOf(LocalDate.of(2027, 3, 31))).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(FiscalYears.startOf(LocalDate.of(2026, 4, 1))).isEqualTo(LocalDate.of(2026, 4, 1));
    }

    @Test
    void labelsAndParses() {
        assertThat(FiscalYears.label(LocalDate.of(2026, 4, 1))).isEqualTo("2026-27");
        assertThat(FiscalYears.label(LocalDate.of(1999, 4, 1))).isEqualTo("1999-00");
        assertThat(FiscalYears.parse("2026-27")).isEqualTo(LocalDate.of(2026, 4, 1));
    }

    @Test
    void rejectsBadLabels() {
        for (String label : new String[] {"2026-28", "x"}) {
            assertThatExceptionOfType(ResponseStatusException.class)
                    .isThrownBy(() -> FiscalYears.parse(label))
                    .satisfies(e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
    }

    @Test
    void monthsRunAprilToMarch() {
        var months = FiscalYears.months(LocalDate.of(2026, 4, 1));

        assertThat(months).hasSize(12);
        assertThat(months.getFirst()).isEqualTo(YearMonth.of(2026, 4));
        assertThat(months.getLast()).isEqualTo(YearMonth.of(2027, 3));
    }
}
