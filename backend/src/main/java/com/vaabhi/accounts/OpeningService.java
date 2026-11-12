package com.vaabhi.accounts;

import com.vaabhi.shared.rupees.Rupees;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** What each balance-sheet account held on the books start date, posted as one opening entry. */
@Service
public class OpeningService {

    private static final String LIVE_ENTRY = """
            SELECT e.id FROM journal_entry e
            WHERE e.source_type = 'OPENING' AND e.source_step = 'OPENING'
              AND NOT EXISTS (SELECT 1 FROM journal_entry r WHERE r.reverses_id = e.id)
            ORDER BY e.id DESC LIMIT 1
            """;

    private final JdbcClient jdbc;
    private final BooksSettingsService settings;
    private final JournalService journal;
    private final ChartService chart;

    OpeningService(JdbcClient jdbc, BooksSettingsService settings, JournalService journal, ChartService chart) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.journal = journal;
        this.chart = chart;
    }

    @Transactional(readOnly = true)
    public OpeningBalances get() {
        var live = jdbc.sql(LIVE_ENTRY).query(Long.class).optional().orElse(null);
        var entry = live == null ? null : journal.get(live);
        var lines = new ArrayList<OpeningBalanceLine>();
        for (var account : chart.list()) {
            if (!account.type().onBalanceSheet()
                    || !account.active()
                    || "OPENING_BALANCE".equals(account.systemKey())) {
                continue;
            }
            var debit = Rupees.ZERO;
            var credit = Rupees.ZERO;
            if (entry != null) {
                for (var line : entry.lines()) {
                    if (line.accountId() == account.id()) {
                        debit = debit.add(line.debitRupees());
                        credit = credit.add(line.creditRupees());
                    }
                }
            }
            lines.add(new OpeningBalanceLine(account.id(), debit, credit));
        }
        return new OpeningBalances(
                settings.get().startDate(),
                entry == null ? null : entry.id(),
                entry == null ? null : entry.number(),
                lines);
    }

    /** Replaces the opening entry: reverses the live one, posts the new amounts with the remainder to equity. */
    @Transactional
    public OpeningBalances save(List<OpeningBalanceLine> lines, @Nullable String actor) {
        var startDate = settings.get().startDate();
        if (startDate == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Set the books start date first");
        }
        long openingEquity = chart.idOf("OPENING_BALANCE");
        var entryLines = new ArrayList<NewEntryLine>();
        var net = Rupees.ZERO;
        for (var line : lines) {
            var debit = Rupees.of(line.debitRupees());
            var credit = Rupees.of(line.creditRupees());
            if (debit.signum() == 0 && credit.signum() == 0) {
                continue;
            }
            if (debit.signum() < 0 || credit.signum() < 0 || (debit.signum() > 0 && credit.signum() > 0)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Each line needs a debit or a credit, not both");
            }
            var account = chart.get(line.accountId());
            if (!account.type().onBalanceSheet() || line.accountId() == openingEquity) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Opening balances go on asset, liability and equity accounts");
            }
            entryLines.add(new NewEntryLine(line.accountId(), null, debit, credit, null));
            net = net.add(debit).subtract(credit);
        }
        var live = jdbc.sql(LIVE_ENTRY).query(Long.class).optional().orElse(null);
        if (live != null) {
            journal.reverse(live, actor);
        }
        if (!entryLines.isEmpty()) {
            BigDecimal remainderDebit = net.signum() < 0 ? net.negate() : Rupees.ZERO;
            BigDecimal remainderCredit = net.signum() > 0 ? net : Rupees.ZERO;
            if (net.signum() != 0) {
                entryLines.add(new NewEntryLine(openingEquity, null, remainderDebit, remainderCredit, null));
            }
            journal.post(
                    new NewEntry(startDate, "OPENING", null, "OPENING", "Opening balances", null, null, entryLines),
                    actor);
        }
        return get();
    }
}
