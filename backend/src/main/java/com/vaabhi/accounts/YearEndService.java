package com.vaabhi.accounts;

import com.vaabhi.shared.rupees.Rupees;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The year-end: the closing stock list, the checks that must pass before closing, the close itself (stock and closing
 * entries) and reopening a closed year.
 */
@Service
public class YearEndService {

    private static final ZoneId SHOP_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private final JdbcClient jdbc;
    private final JournalService journal;
    private final JournalSweeper sweeper;
    private final PostingProblemService problems;
    private final PeriodService periods;
    private final BooksSettingsService settings;
    private final ChartService chart;
    private final StatementService statements;

    YearEndService(
            JdbcClient jdbc,
            JournalService journal,
            JournalSweeper sweeper,
            PostingProblemService problems,
            PeriodService periods,
            BooksSettingsService settings,
            ChartService chart,
            StatementService statements) {
        this.jdbc = jdbc;
        this.journal = journal;
        this.sweeper = sweeper;
        this.problems = problems;
        this.periods = periods;
        this.settings = settings;
        this.chart = chart;
        this.statements = statements;
    }

    record FiscalYearRow(
            BigDecimal finishedGoodsRupees,
            @Nullable String stockNote,
            @Nullable OffsetDateTime stockSavedAt,
            boolean closed) {}

    record RunRow(
            long id, @Nullable Long stockEntryId, @Nullable Long closingEntryId) {}

    record PrefillRow(long id, String name, String unit, long costPaise, BigDecimal quantity) {}

    record BalanceRow(long accountId, BigDecimal net) {}

    @Transactional(readOnly = true)
    public YearEnd get(LocalDate fyStart) {
        var end = FiscalYears.end(fyStart);
        var row = fiscalYear(fyStart);
        boolean closed = row != null && row.closed();
        boolean stockSaved = row != null && row.stockSavedAt() != null;
        var finishedGoods = row == null ? Rupees.ZERO : Rupees.of(row.finishedGoodsRupees());
        var stock = stockSaved ? savedStock(fyStart) : prefillStock(end);
        var closingStock =
                Rupees.of(Rupees.sum(stock, ClosingStockLine::valueRupees).add(finishedGoods));
        var stockBalance = stockBalance(fyStart, end, closed);
        var adjustment = Rupees.of(closingStock.subtract(stockBalance));
        var profit = statements.profitAndLoss(fyStart, end).profitRupees();
        var profitRupees = closed ? Rupees.of(profit) : Rupees.of(profit.add(adjustment));
        boolean laterClosed = jdbc.sql("SELECT EXISTS (SELECT 1 FROM fiscal_year WHERE fy_start > :fy AND closed)")
                .param("fy", fyStart)
                .query(Boolean.class)
                .single();
        return new YearEnd(
                FiscalYears.label(fyStart),
                fyStart,
                end,
                closed,
                stockSaved,
                closed && !laterClosed,
                stock,
                finishedGoods,
                row == null ? null : row.stockNote(),
                closingStock,
                stockBalance,
                adjustment,
                profitRupees,
                checks(fyStart, end, stockSaved),
                runs(fyStart));
    }

    /** The saved lines of a year's closing stock list, empty when it has none. */
    @Transactional(readOnly = true)
    public List<ClosingStockLine> savedStock(LocalDate fyStart) {
        return jdbc.sql("""
                        SELECT l.stock_item_id, i.name, i.unit, l.quantity, l.rate_rupees, l.value_rupees
                        FROM closing_stock_line l
                        JOIN stock_item i ON i.id = l.stock_item_id
                        WHERE l.fy_start = :fy
                        ORDER BY i.name
                        """).param("fy", fyStart).query(ClosingStockLine.class).list();
    }

    @Transactional
    public YearEnd saveStock(LocalDate fyStart, YearEndStockForm form, @Nullable String actor) {
        var label = FiscalYears.label(fyStart);
        var row = fiscalYear(fyStart);
        if (row != null && row.closed()) {
            throw conflict(label + " is closed. Reopen it to change the stock list");
        }
        var existing = new HashSet<Long>();
        if (!form.lines().isEmpty()) {
            var ids = form.lines().stream()
                    .map(YearEndStockItemInput::stockItemId)
                    .distinct()
                    .toList();
            existing.addAll(jdbc.sql("SELECT id FROM stock_item WHERE id IN (:ids)")
                    .param("ids", ids)
                    .query(Long.class)
                    .list());
        }
        var seen = new HashSet<Long>();
        for (var line : form.lines()) {
            if (!existing.contains(line.stockItemId())) {
                throw bad("Stock item " + line.stockItemId() + " not found");
            }
            if (!seen.add(line.stockItemId())) {
                throw bad("Stock item " + line.stockItemId() + " is listed twice");
            }
        }
        jdbc.sql("""
                        INSERT INTO fiscal_year (fy_start, finished_goods_rupees, stock_note, stock_saved_by,
                                                 stock_saved_at)
                        VALUES (:fy, :finishedGoods, :note, :by, now())
                        ON CONFLICT (fy_start) DO UPDATE
                        SET finished_goods_rupees = EXCLUDED.finished_goods_rupees,
                            stock_note = EXCLUDED.stock_note,
                            stock_saved_by = EXCLUDED.stock_saved_by,
                            stock_saved_at = now()
                        """)
                .param("fy", fyStart)
                .param("finishedGoods", form.finishedGoodsRupees())
                .param("note", form.note())
                .param("by", actor)
                .update();
        jdbc.sql("DELETE FROM closing_stock_line WHERE fy_start = :fy")
                .param("fy", fyStart)
                .update();
        for (var line : form.lines()) {
            jdbc.sql("""
                            INSERT INTO closing_stock_line (fy_start, stock_item_id, quantity, rate_rupees, value_rupees)
                            VALUES (:fy, :item, :quantity, :rate, :value)
                            """)
                    .param("fy", fyStart)
                    .param("item", line.stockItemId())
                    .param("quantity", line.quantity())
                    .param("rate", line.rateRupees())
                    .param("value", Rupees.of(line.quantity().multiply(line.rateRupees())))
                    .update();
        }
        return get(fyStart);
    }

    @Transactional
    public YearEnd close(LocalDate fyStart, @Nullable String actor) {
        var end = FiscalYears.end(fyStart);
        var label = FiscalYears.label(fyStart);
        var row = fiscalYear(fyStart);
        if (row != null && row.closed()) {
            throw conflict(label + " is already closed");
        }

        sweeper.sweepNow();
        var yearEnd = get(fyStart);
        for (var check : yearEnd.checks()) {
            if (!check.ok()) {
                throw conflict(check.message() == null ? check.label() : check.message());
            }
        }

        // list() stops at this month, so a year closing out of order is read from the table.
        var lockedMonths = new HashSet<LocalDate>();
        for (var status : periods.list()) {
            if (status.locked()) {
                lockedMonths.add(status.month());
            }
        }
        lockedMonths.addAll(jdbc.sql("SELECT month FROM period_lock WHERE month BETWEEN :from AND :to")
                .param("from", fyStart)
                .param("to", end)
                .query(LocalDate.class)
                .list());
        var march = YearMonth.from(end);
        if (lockedMonths.contains(march.atDay(1))) {
            periods.unlock(march);
        }

        long runId = jdbc.sql("INSERT INTO year_close_run (fy_start, closed_by) VALUES (:fy, :by) RETURNING id")
                .param("fy", fyStart)
                .param("by", actor)
                .query(Long.class)
                .single();

        var adjustment = yearEnd.adjustmentRupees();
        Long stockEntryId = null;
        if (adjustment.signum() != 0) {
            var stockId = chart.idOf("STOCK");
            var changeId = chart.idOf("CHANGE_IN_INVENTORIES");
            var amount = adjustment.abs();
            List<NewEntryLine> lines = adjustment.signum() > 0
                    ? List.of(
                            new NewEntryLine(stockId, null, amount, Rupees.ZERO, null),
                            new NewEntryLine(changeId, null, Rupees.ZERO, amount, null))
                    : List.of(
                            new NewEntryLine(changeId, null, amount, Rupees.ZERO, null),
                            new NewEntryLine(stockId, null, Rupees.ZERO, amount, null));
            stockEntryId = journal.post(
                            new NewEntry(end, "YEAR_END", runId, "STOCK", "Closing stock " + label, null, null, lines),
                            actor)
                    .id();
        }

        Long closingEntryId = closingEntry(end, label, runId, actor);

        for (var month : FiscalYears.months(fyStart)) {
            periods.lock(month, actor);
        }

        jdbc.sql("UPDATE fiscal_year SET closed = TRUE WHERE fy_start = :fy")
                .param("fy", fyStart)
                .update();
        jdbc.sql("""
                        UPDATE year_close_run SET stock_entry_id = :stock, closing_entry_id = :closing
                        WHERE id = :id
                        """)
                .param("stock", stockEntryId)
                .param("closing", closingEntryId)
                .param("id", runId)
                .update();
        return get(fyStart);
    }

    @Transactional
    public YearEnd reopen(LocalDate fyStart, @Nullable String actor) {
        var end = FiscalYears.end(fyStart);
        var label = FiscalYears.label(fyStart);
        var row = fiscalYear(fyStart);
        if (row == null || !row.closed()) {
            throw conflict(label + " isn't closed");
        }
        var later = jdbc.sql("""
                        SELECT fy_start FROM fiscal_year
                        WHERE fy_start > :fy AND closed
                        ORDER BY fy_start DESC LIMIT 1
                        """)
                .param("fy", fyStart)
                .query(LocalDate.class)
                .optional()
                .orElse(null);
        if (later != null) {
            throw conflict("Reopen " + FiscalYears.label(later) + " first");
        }
        periods.unlock(YearMonth.from(end));
        var run = openRun(fyStart);
        if (run != null) {
            if (run.closingEntryId() != null) {
                journal.reverseOn(run.closingEntryId(), end, actor);
            }
            if (run.stockEntryId() != null) {
                journal.reverseOn(run.stockEntryId(), end, actor);
            }
            jdbc.sql("UPDATE year_close_run SET reopened_by = :by, reopened_at = now() WHERE id = :id")
                    .param("by", actor)
                    .param("id", run.id())
                    .update();
        }
        jdbc.sql("UPDATE fiscal_year SET closed = FALSE WHERE fy_start = :fy")
                .param("fy", fyStart)
                .update();
        return get(fyStart);
    }

    /** Posts the entry that closes every income and expense balance into retained earnings, or null when there is none. */
    private @Nullable Long closingEntry(LocalDate end, String label, long runId, @Nullable String actor) {
        var balances = jdbc.sql("""
                        SELECT a.id AS account_id, SUM(l.debit_rupees) - SUM(l.credit_rupees) AS net
                        FROM account a
                        JOIN journal_line l ON l.account_id = a.id
                        JOIN journal_entry e ON e.id = l.entry_id
                        WHERE a.type IN ('INCOME', 'EXPENSE') AND e.entry_date <= :end
                        GROUP BY a.id
                        HAVING SUM(l.debit_rupees) - SUM(l.credit_rupees) <> 0
                        ORDER BY a.id
                        """).param("end", end).query(BalanceRow.class).list();
        if (balances.isEmpty()) {
            return null;
        }
        var lines = new ArrayList<NewEntryLine>();
        var debits = Rupees.ZERO;
        var credits = Rupees.ZERO;
        for (var balance : balances) {
            var net = Rupees.of(balance.net());
            if (net.signum() > 0) {
                lines.add(new NewEntryLine(balance.accountId(), null, Rupees.ZERO, net, null));
                credits = credits.add(net);
            } else {
                var amount = net.negate();
                lines.add(new NewEntryLine(balance.accountId(), null, amount, Rupees.ZERO, null));
                debits = debits.add(amount);
            }
        }
        var difference = credits.subtract(debits);
        var retainedId = chart.idOf("RETAINED_EARNINGS");
        if (difference.signum() > 0) {
            lines.add(new NewEntryLine(retainedId, null, difference, Rupees.ZERO, null));
        } else if (difference.signum() < 0) {
            lines.add(new NewEntryLine(retainedId, null, Rupees.ZERO, difference.negate(), null));
        }
        if (lines.size() < 2) {
            return null;
        }
        return journal.post(
                        new NewEntry(end, "YEAR_END", runId, "CLOSE", "Closing entry " + label, null, null, lines),
                        actor)
                .id();
    }

    private List<YearEndCheck> checks(LocalDate fyStart, LocalDate end, boolean stockSaved) {
        var startDate = settings.get().startDate();
        boolean startOk = startDate != null && !startDate.isAfter(end);
        boolean previousOk;
        if (startDate == null || !FiscalYears.startOf(startDate).isBefore(fyStart)) {
            previousOk = true;
        } else {
            previousOk = jdbc.sql("SELECT EXISTS (SELECT 1 FROM fiscal_year WHERE fy_start = :fy AND closed)")
                    .param("fy", fyStart.minusYears(1))
                    .query(Boolean.class)
                    .single();
        }
        int problemCount = problems.count();
        boolean problemsOk = problemCount == 0;
        boolean trialOk = journal.trialBalance(end).balanced();
        return List.of(
                new YearEndCheck(
                        "START_DATE", "Books start date", startOk, startOk ? null : "Set the books start date first"),
                new YearEndCheck(
                        "PREVIOUS_YEAR",
                        "Previous year closed",
                        previousOk,
                        previousOk ? null : "Close " + FiscalYears.label(fyStart.minusYears(1)) + " first"),
                new YearEndCheck(
                        "PROBLEMS",
                        "Every document posted",
                        problemsOk,
                        problemsOk
                                ? null
                                : problemCount + " documents aren't posted yet. Fix them under Posting problems"),
                new YearEndCheck(
                        "TRIAL_BALANCE",
                        "Trial balance agrees",
                        trialOk,
                        trialOk ? null : "The trial balance at " + DAY_MONTH_YEAR.format(end) + " doesn't balance"),
                new YearEndCheck(
                        "STOCK_SAVED",
                        "Closing stock saved",
                        stockSaved,
                        stockSaved ? null : "Save the closing stock list first"));
    }

    private List<ClosingStockLine> prefillStock(LocalDate end) {
        var cutoff = end.atTime(23, 59, 59).atZone(SHOP_ZONE).toOffsetDateTime();
        return jdbc.sql("""
                        SELECT i.id, i.name, i.unit, i.cost_paise, SUM(m.quantity) AS quantity
                        FROM stock_item i
                        JOIN stock_movement m ON m.item_id = i.id
                        WHERE m.moved_at <= :cutoff
                        GROUP BY i.id
                        HAVING SUM(m.quantity) > 0
                        ORDER BY i.name
                        """).param("cutoff", cutoff).query(PrefillRow.class).list().stream()
                .map(row -> {
                    var rate = BigDecimal.valueOf(row.costPaise(), 2);
                    var value = Rupees.of(row.quantity().multiply(rate));
                    return new ClosingStockLine(row.id(), row.name(), row.unit(), row.quantity(), rate, value);
                })
                .toList();
    }

    private BigDecimal stockBalance(LocalDate fyStart, LocalDate end, boolean closed) {
        var stockId = chart.idOf("STOCK");
        var balance = jdbc.sql("""
                        SELECT COALESCE(SUM(l.debit_rupees - l.credit_rupees), 0)
                        FROM journal_line l
                        JOIN journal_entry e ON e.id = l.entry_id
                        WHERE l.account_id = :account AND e.entry_date <= :end
                        """)
                .param("account", stockId)
                .param("end", end)
                .query(BigDecimal.class)
                .single();
        if (closed) {
            var run = openRun(fyStart);
            if (run != null && run.stockEntryId() != null) {
                balance = balance.subtract(jdbc.sql("""
                                SELECT COALESCE(SUM(debit_rupees - credit_rupees), 0)
                                FROM journal_line WHERE entry_id = :entry AND account_id = :account
                                """)
                        .param("entry", run.stockEntryId())
                        .param("account", stockId)
                        .query(BigDecimal.class)
                        .single());
            }
        }
        return Rupees.of(balance);
    }

    private List<YearCloseRunView> runs(LocalDate fyStart) {
        return jdbc.sql("""
                        SELECT id, stock_entry_id, closing_entry_id, closed_by, closed_at, reopened_by, reopened_at
                        FROM year_close_run
                        WHERE fy_start = :fy
                        ORDER BY closed_at DESC, id DESC
                        """).param("fy", fyStart).query(YearCloseRunView.class).list();
    }

    private @Nullable FiscalYearRow fiscalYear(LocalDate fyStart) {
        return jdbc.sql("""
                        SELECT finished_goods_rupees, stock_note, stock_saved_at, closed
                        FROM fiscal_year WHERE fy_start = :fy
                        """)
                .param("fy", fyStart)
                .query(FiscalYearRow.class)
                .optional()
                .orElse(null);
    }

    private @Nullable RunRow openRun(LocalDate fyStart) {
        return jdbc.sql("""
                        SELECT id, stock_entry_id, closing_entry_id
                        FROM year_close_run WHERE fy_start = :fy AND reopened_at IS NULL
                        """).param("fy", fyStart).query(RunRow.class).optional().orElse(null);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
