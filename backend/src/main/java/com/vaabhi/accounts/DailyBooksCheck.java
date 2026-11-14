package com.vaabhi.accounts;

import com.vaabhi.shared.rupees.Rupees;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Once a day, checks that the trial balance balances and that each live invoice's Debtors balance equals what the
 * invoice still has due. Anything off becomes a posting problem of type CHECK; problems that no longer hold clear.
 */
@Component
public class DailyBooksCheck {

    static final String TYPE = "CHECK";

    private static final Logger log = LoggerFactory.getLogger(DailyBooksCheck.class);
    private static final ZoneId SHOP_ZONE = ZoneId.of("Asia/Kolkata");
    private static final String SHOP_DAY = "CAST(%s AT TIME ZONE 'Asia/Kolkata' AS date)";

    private final JdbcClient jdbc;
    private final BooksSettingsService settings;
    private final JournalService journal;
    private final PostingProblemService problems;
    private final Clock clock;

    DailyBooksCheck(
            JdbcClient jdbc,
            BooksSettingsService settings,
            JournalService journal,
            PostingProblemService problems,
            Clock clock) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.journal = journal;
        this.problems = problems;
        this.clock = clock;
    }

    record DebtorsRow(long id, String number, BigDecimal debtorsRupees, BigDecimal dueRupees) {}

    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Kolkata")
    void scheduledCheck() {
        try {
            run();
        } catch (RuntimeException e) {
            log.warn("Daily books check failed", e);
        }
    }

    /** Runs the checks now and returns how many problems it found. */
    public int run() {
        var start = settings.get().startDate();
        if (start == null) {
            return 0;
        }
        problems.clearType(TYPE);
        int found = 0;
        var trial = journal.trialBalance(LocalDate.now(clock.withZone(SHOP_ZONE)));
        if (!trial.balanced()) {
            problems.record(
                    TYPE,
                    0,
                    "TRIAL_BAL",
                    "The trial balance is out by ₹"
                            + trial.totalDebitRupees()
                                    .subtract(trial.totalCreditRupees())
                                    .abs()
                                    .toPlainString());
            found++;
        }
        for (var row : debtorsMismatches(start)) {
            problems.record(
                    TYPE,
                    row.id(),
                    "DEBTORS",
                    "Debtors for " + row.number() + " is ₹"
                            + Rupees.of(row.debtorsRupees()).toPlainString() + " but ₹"
                            + Rupees.of(row.dueRupees()).toPlainString() + " is due");
            found++;
        }
        return found;
    }

    /**
     * Order numbers of live invoices (issued on or after the start) whose order took money before the start date.
     * The owner settles these with the overall Debtors and advances opening amounts.
     */
    // Sonar java:S2077: the SQL is assembled only from fixed fragments in this class; every value is a bound parameter.
    @SuppressWarnings("java:S2077")
    public List<String> preStartOrders(LocalDate start) {
        var sql = """
                SELECT COALESCE(o.number, w.number) AS number
                FROM invoice i
                LEFT JOIN shop_order o ON o.id = i.order_id
                LEFT JOIN web_order w ON w.id = i.web_order_id
                WHERE i.cancelled_at IS NULL AND i.issued_on >= :start
                  AND (EXISTS (SELECT 1 FROM order_payment p
                               WHERE p.order_id = i.order_id AND %1$s < :start)
                       OR (w.paid_at IS NOT NULL AND %2$s < :start))
                ORDER BY 1
                """.formatted(SHOP_DAY.formatted("p.received_at"), SHOP_DAY.formatted("w.paid_at"));
        return jdbc.sql(sql).param("start", start).query(String.class).list();
    }

    /**
     * Live invoices (issued on or after the start, already posted) whose Debtors balance on the order differs from
     * what is really due: total, less payments on the order, plus refunds, less credit notes. Only money moved on or
     * after the start counts; what was received or refunded before it is part of the opening balances.
     */
    // Sonar java:S2077: the SQL is assembled only from fixed fragments in this class; every value is a bound parameter.
    @SuppressWarnings("java:S2077")
    private List<DebtorsRow> debtorsMismatches(LocalDate start) {
        var sql = """
                SELECT * FROM (
                    SELECT i.id, i.number, COALESCE(d.balance, 0) AS debtors_rupees,
                           i.total_rupees
                           - COALESCE((SELECT SUM(p.amount_rupees) FROM order_payment p
                                       WHERE p.order_id = i.order_id AND %1$s >= :start), 0)
                           - CASE WHEN w.paid_at IS NOT NULL AND %2$s >= :start THEN w.total_rupees ELSE 0 END
                           + CASE WHEN w.refund_status = 'PROCESSED' AND w.refunded_at IS NOT NULL
                                       AND %3$s >= :start
                                  THEN COALESCE(w.refunded_rupees, 0) ELSE 0 END
                           + COALESCE((SELECT SUM(r.refund_rupees) FROM web_order_return r
                                       WHERE r.order_id = i.web_order_id AND r.refunded_at IS NOT NULL
                                         AND %4$s >= :start
                                         AND r.refund_rupees > 0
                                         AND ((r.refund_method = 'RAZORPAY' AND r.refund_status = 'PROCESSED')
                                              OR r.refund_method = 'MANUAL')), 0)
                           - COALESCE((SELECT SUM(n.total_rupees) FROM credit_note n
                                       WHERE n.invoice_id = i.id), 0) AS due_rupees
                    FROM invoice i
                    LEFT JOIN web_order w ON w.id = i.web_order_id
                    LEFT JOIN (SELECT e.order_key, SUM(l.debit_rupees - l.credit_rupees) AS balance
                               FROM journal_line l JOIN journal_entry e ON e.id = l.entry_id
                               WHERE l.account_id = (SELECT id FROM account WHERE system_key = 'DEBTORS')
                                 AND e.order_key IS NOT NULL
                               GROUP BY e.order_key) d
                           ON d.order_key = CASE WHEN i.order_id IS NOT NULL THEN 'SHOP:' || i.order_id
                                                 ELSE 'WEB:' || i.web_order_id END
                    WHERE i.cancelled_at IS NULL AND i.issued_on >= :start
                      AND EXISTS (SELECT 1 FROM journal_entry x
                                  WHERE x.source_type = 'INVOICE' AND x.source_id = i.id
                                    AND x.source_step = 'ISSUE')
                ) m
                WHERE m.debtors_rupees <> m.due_rupees
                ORDER BY m.id
                """.formatted(
                        SHOP_DAY.formatted("p.received_at"),
                        SHOP_DAY.formatted("w.paid_at"),
                        SHOP_DAY.formatted("w.refunded_at"),
                        SHOP_DAY.formatted("r.refunded_at"));
        return jdbc.sql(sql).param("start", start).query(DebtorsRow.class).list();
    }
}
