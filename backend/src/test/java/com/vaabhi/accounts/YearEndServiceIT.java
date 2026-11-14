package com.vaabhi.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vaabhi.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Closing stock, the close checks, close and reopen of a financial year, against real Postgres. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class YearEndServiceIT extends BooksFixtures {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Autowired
    private YearEndService yearEnd;

    @Autowired
    private JournalService journal;

    @Autowired
    private PeriodService periods;

    private void startBooksAt(LocalDate fyStart) {
        jdbc.sql("UPDATE books_setting SET start_date = :d WHERE id = 1")
                .param("d", fyStart)
                .update();
    }

    private long stockItem() {
        return jdbc.sql("INSERT INTO stock_item (name, unit, cost_paise) VALUES (:name, 'PCS', 5000) RETURNING id")
                .param("name", unique("Year end item "))
                .query(Long.class)
                .single();
    }

    private void movement(long item, String kind, String quantity, OffsetDateTime at) {
        jdbc.sql("""
                        INSERT INTO stock_movement (item_id, kind, quantity, moved_at)
                        VALUES (:item, :kind, CAST(:quantity AS numeric), :at)
                        """)
                .param("item", item)
                .param("kind", kind)
                .param("quantity", quantity)
                .param("at", at)
                .update();
    }

    private void clearProblems() {
        jdbc.sql("DELETE FROM posting_problem").update();
    }

    private boolean locked(YearMonth month) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM period_lock WHERE month = :month)")
                .param("month", month.atDay(1))
                .query(Boolean.class)
                .single();
    }

    private BigDecimal balance(long account, LocalDate asOf) {
        return jdbc.sql("""
                        SELECT COALESCE(SUM(l.debit_rupees - l.credit_rupees), 0)
                        FROM journal_line l
                        JOIN journal_entry e ON e.id = l.entry_id
                        WHERE l.account_id = :account AND e.entry_date <= :asOf
                        """)
                .param("account", account)
                .param("asOf", asOf)
                .query(BigDecimal.class)
                .single();
    }

    private int openProfitAndLossAccounts(LocalDate asOf) {
        return jdbc.sql("""
                        SELECT COUNT(*) FROM (
                            SELECT a.id
                            FROM account a
                            JOIN journal_line l ON l.account_id = a.id
                            JOIN journal_entry e ON e.id = l.entry_id
                            WHERE a.type IN ('INCOME', 'EXPENSE') AND e.entry_date <= :asOf
                            GROUP BY a.id
                            HAVING SUM(l.debit_rupees) - SUM(l.credit_rupees) <> 0
                        ) open_accounts
                        """)
                .param("asOf", asOf)
                .query(Integer.class)
                .single();
    }

    private void post(LocalDate date, List<NewEntryLine> lines) {
        journal.post(new NewEntry(date, "MANUAL", null, "MANUAL", "Year end test", null, null, lines), "test");
    }

    private static NewEntryLine debit(long account, String amount) {
        return new NewEntryLine(account, null, new BigDecimal(amount), BigDecimal.ZERO, null);
    }

    private static NewEntryLine credit(long account, String amount) {
        return new NewEntryLine(account, null, BigDecimal.ZERO, new BigDecimal(amount), null);
    }

    private static YearEndStockForm stockForm(List<YearEndStockItemInput> lines, String finishedGoods) {
        return new YearEndStockForm(lines, new BigDecimal(finishedGoods), null);
    }

    private void reopenIfClosed(LocalDate fyStart) {
        if (yearEnd.get(fyStart).closed()) {
            yearEnd.reopen(fyStart, "test");
        }
    }

    @Test
    void prefillUsesTheOnHandAtTheEndOfTheYear() {
        var fyStart = LocalDate.of(2081, 4, 1);
        var end = FiscalYears.end(fyStart);
        try {
            startBooksAt(fyStart);
            long item = stockItem();
            movement(item, "IN", "10", end.minusDays(21).atTime(10, 0).atZone(IST).toOffsetDateTime());
            movement(item, "OUT", "-4", end.plusDays(2).atTime(10, 0).atZone(IST).toOffsetDateTime());

            var view = yearEnd.get(fyStart);

            assertThat(view.closed()).isFalse();
            assertThat(view.stockSaved()).isFalse();
            var line = view.stock().stream()
                    .filter(candidate -> candidate.stockItemId() == item)
                    .findFirst()
                    .orElseThrow();
            assertThat(line.quantity()).isEqualByComparingTo("10");
            assertThat(line.rateRupees()).isEqualByComparingTo("50.00");
            assertThat(line.valueRupees()).isEqualByComparingTo("500.00");
        } finally {
            reopenIfClosed(fyStart);
        }
    }

    @Test
    void closePostsStockAndClosingEntriesThenReopensAndClosesAgain() {
        var fyStart = LocalDate.of(2082, 4, 1);
        var end = FiscalYears.end(fyStart);
        try {
            startBooksAt(fyStart);
            long item = stockItem();
            movement(item, "IN", "10", end.minusDays(21).atTime(10, 0).atZone(IST).toOffsetDateTime());
            post(
                    fyStart.plusMonths(1),
                    List.of(debit(accountId("DEBTORS"), "1000"), credit(accountId("SALES"), "1000")));
            post(
                    fyStart.plusMonths(1).plusDays(2),
                    List.of(debit(accountId("EXPENSE_RENT"), "300"), credit(accountId("CASH"), "300")));
            yearEnd.saveStock(
                    fyStart,
                    stockForm(
                            List.of(new YearEndStockItemInput(item, new BigDecimal("10"), new BigDecimal("50"))),
                            "200"),
                    "test");
            clearProblems();
            var salesBeforeClose = balance(accountId("SALES"), end);

            var before = yearEnd.get(fyStart);
            var closed = yearEnd.close(fyStart, "test");

            assertThat(closed.closed()).isTrue();
            var stockEntryId = closed.runs().getFirst().stockEntryId();
            if (before.adjustmentRupees().signum() != 0) {
                assertThat(stockEntryId).isNotNull();
                assertThat(netInEntry(stockEntryId, accountId("STOCK")))
                        .isEqualByComparingTo(before.adjustmentRupees());
            }
            assertThat(openProfitAndLossAccounts(end)).isZero();
            for (var month : FiscalYears.months(fyStart)) {
                assertThat(locked(month)).as(month.toString()).isTrue();
            }
            var firstRunId = closed.runs().getFirst().id();

            var reopened = yearEnd.reopen(fyStart, "test");

            assertThat(reopened.closed()).isFalse();
            assertThat(locked(YearMonth.from(end))).isFalse();
            assertThat(locked(YearMonth.of(fyStart.getYear(), 4))).isTrue();
            assertThat(balance(accountId("SALES"), end)).isEqualByComparingTo(salesBeforeClose);

            var reclosed = yearEnd.close(fyStart, "test");

            assertThat(reclosed.closed()).isTrue();
            assertThat(reclosed.runs().getFirst().id()).isNotEqualTo(firstRunId);
        } finally {
            reopenIfClosed(fyStart);
        }
    }

    @Test
    void closingUnlocksALockedMarchAndLocksItAgain() {
        var fyStart = LocalDate.of(2083, 4, 1);
        var end = FiscalYears.end(fyStart);
        try {
            startBooksAt(fyStart);
            periods.lock(YearMonth.from(end), "test");
            clearProblems();
            yearEnd.saveStock(fyStart, stockForm(List.of(), "0"), "test");

            var closed = yearEnd.close(fyStart, "test");

            assertThat(closed.closed()).isTrue();
            assertThat(locked(YearMonth.from(end))).isTrue();
        } finally {
            reopenIfClosed(fyStart);
        }
    }

    @Test
    void closeAndReopenAYearWithNothingInIt() {
        var fyStart = LocalDate.of(2084, 4, 1);
        try {
            startBooksAt(fyStart);
            clearProblems();
            yearEnd.saveStock(fyStart, stockForm(List.of(), "0"), "test");

            var closed = yearEnd.close(fyStart, "test");

            assertThat(closed.closed()).isTrue();
            for (var month : FiscalYears.months(fyStart)) {
                assertThat(locked(month)).as(month.toString()).isTrue();
            }

            var reopened = yearEnd.reopen(fyStart, "test");

            assertThat(reopened.closed()).isFalse();
        } finally {
            reopenIfClosed(fyStart);
        }
    }

    @Test
    void closeNeedsSavedStockAndSavingNeedsAnOpenYear() {
        var fyStart = LocalDate.of(2085, 4, 1);
        try {
            startBooksAt(fyStart);
            clearProblems();

            assertThatThrownBy(() -> yearEnd.close(fyStart, "test"))
                    .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(e.getReason()).contains("Save the closing stock list first");
                    });

            yearEnd.saveStock(fyStart, stockForm(List.of(), "0"), "test");
            var closed = yearEnd.close(fyStart, "test");

            assertThat(closed.closed()).isTrue();
            assertThatThrownBy(() -> yearEnd.saveStock(fyStart, stockForm(List.of(), "0"), "test"))
                    .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(e.getReason()).contains("is closed");
                    });
        } finally {
            reopenIfClosed(fyStart);
        }
    }
}
