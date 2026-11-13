package com.vaabhi.accounts;

import com.vaabhi.shared.rupees.Rupees;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** The actions on one bank line: match it to an entry, post it as an entry, ignore it, or undo that. */
@Service
public class BankLineService {

    private static final int MAX_REASON = 200;
    private static final int MAX_NARRATION = 200;
    private static final int MAX_ACTOR = 20;

    private final JdbcClient jdbc;
    private final BankImportService imports;
    private final BankMatcher matcher;
    private final JournalService journal;
    private final ChartService chart;

    BankLineService(
            JdbcClient jdbc,
            BankImportService imports,
            BankMatcher matcher,
            JournalService journal,
            ChartService chart) {
        this.jdbc = jdbc;
        this.imports = imports;
        this.matcher = matcher;
        this.journal = journal;
        this.chart = chart;
    }

    /** Links the line to an entry, posting the amount difference to {@code differenceAccountId} when they differ. */
    @Transactional
    public BankLineView match(long lineId, BankMatchForm form, @Nullable String actor) {
        var line = imports.line(lineId);
        requireUnmatched(line);
        var entryBank = matcher.bankAmount(form.journalEntryId());
        if (entryBank == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That entry can't be matched");
        }
        var entryNumber = journal.get(form.journalEntryId()).number();
        var lineSigned = line.creditRupees().subtract(line.debitRupees());
        if (entryBank.signum() != lineSigned.signum()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That entry moves money the other way");
        }
        Long differenceId = null;
        if (entryBank.compareTo(lineSigned) != 0) {
            if (form.differenceAccountId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick an account for the difference");
            }
            differenceId = postDifference(
                    line, form.differenceAccountId(), entryBank.subtract(lineSigned), entryNumber, actor);
        }
        jdbc.sql("""
                        UPDATE bank_line
                        SET status = 'MATCHED', journal_entry_id = :entry, difference_entry_id = :difference,
                            matched_by = :by, matched_at = now()
                        WHERE id = :id
                        """)
                .param("entry", form.journalEntryId())
                .param("difference", differenceId)
                .param("by", matchedBy(actor))
                .param("id", lineId)
                .update();
        return imports.line(lineId);
    }

    /** Posts the line as a new entry against {@code accountId}: bank credit debits Bank, bank debit credits it. */
    @Transactional
    public BankLineView post(long lineId, BankPostForm form, @Nullable String actor) {
        var line = imports.line(lineId);
        requireUnmatched(line);
        long bankId = chart.idOf("BANK");
        if (form.accountId() == bankId) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick an account other than Bank");
        }
        var amount = line.creditRupees().signum() > 0 ? line.creditRupees() : line.debitRupees();
        var lines = line.creditRupees().signum() > 0
                ? List.of(
                        new NewEntryLine(bankId, null, amount, Rupees.ZERO, null),
                        new NewEntryLine(form.accountId(), null, Rupees.ZERO, amount, null))
                : List.of(
                        new NewEntryLine(form.accountId(), null, amount, Rupees.ZERO, null),
                        new NewEntryLine(bankId, null, Rupees.ZERO, amount, null));
        var narration = form.narration() == null || form.narration().isBlank()
                ? line.description()
                : form.narration().trim();
        var entry = journal.post(
                new NewEntry(
                        line.date(),
                        "BANK_LINE",
                        sourceId(lineId, "POST"),
                        "POST",
                        abbreviate(narration, MAX_NARRATION),
                        null,
                        null,
                        lines),
                actor);
        jdbc.sql("""
                        UPDATE bank_line
                        SET status = 'POSTED', journal_entry_id = :entry, matched_by = :by, matched_at = now()
                        WHERE id = :id
                        """)
                .param("entry", entry.id())
                .param("by", matchedBy(actor))
                .param("id", lineId)
                .update();
        return imports.line(lineId);
    }

    /** Marks the line as not a money movement, keeping the reason for the report. */
    @Transactional
    public BankLineView ignore(long lineId, BankIgnoreForm form, @Nullable String actor) {
        var line = imports.line(lineId);
        requireUnmatched(line);
        var reason = form.reason() == null ? "" : form.reason().trim();
        if (reason.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Give a reason");
        }
        if (reason.length() > MAX_REASON) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Keep the reason under 200 characters");
        }
        jdbc.sql("UPDATE bank_line SET status = 'IGNORED', ignore_reason = :reason WHERE id = :id")
                .param("reason", reason)
                .param("id", lineId)
                .update();
        return imports.line(lineId);
    }

    /** Reverses whatever the action posted and returns the line to unmatched; a locked month refuses with 409. */
    @Transactional
    public BankLineView undo(long lineId, @Nullable String actor) {
        var line = imports.line(lineId);
        switch (line.status()) {
            case "MATCHED" -> {
                var differenceId = differenceEntryId(lineId);
                if (differenceId != null) {
                    journal.reverseOn(differenceId, line.date(), actor);
                }
            }
            case "POSTED" -> {
                if (line.journalEntryId() != null) {
                    journal.reverseOn(line.journalEntryId(), line.date(), actor);
                }
            }
            case "IGNORED" -> {}
            default ->
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This line is not matched, posted or ignored");
        }
        jdbc.sql("""
                        UPDATE bank_line
                        SET status = 'UNMATCHED', journal_entry_id = NULL, difference_entry_id = NULL,
                            matched_by = NULL, matched_at = NULL, ignore_reason = NULL
                        WHERE id = :id
                        """).param("id", lineId).update();
        return imports.line(lineId);
    }

    /** Posts the difference so the Bank balance moves by the line's amount: positive means bank got less. */
    private long postDifference(
            BankLineView line,
            long differenceAccountId,
            BigDecimal difference,
            String entryNumber,
            @Nullable String actor) {
        long bankId = chart.idOf("BANK");
        var amount = Rupees.of(difference.abs());
        var lines = difference.signum() > 0
                ? List.of(
                        new NewEntryLine(differenceAccountId, null, amount, Rupees.ZERO, null),
                        new NewEntryLine(bankId, null, Rupees.ZERO, amount, null))
                : List.of(
                        new NewEntryLine(bankId, null, amount, Rupees.ZERO, null),
                        new NewEntryLine(differenceAccountId, null, Rupees.ZERO, amount, null));
        return journal.post(
                        new NewEntry(
                                line.date(),
                                "BANK_LINE",
                                sourceId(line.id(), "MATCH"),
                                "MATCH",
                                "Difference on " + entryNumber,
                                null,
                                null,
                                lines),
                        actor)
                .id();
    }

    /**
     * The reversed original still holds the (source_type, source_id, source_step) slot, so a redo posts with no
     * source id.
     */
    private @Nullable Long sourceId(long lineId, String step) {
        boolean taken = jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM journal_entry
                                       WHERE source_type = 'BANK_LINE' AND source_id = :id AND source_step = :step)
                        """)
                .param("id", lineId)
                .param("step", step)
                .query(Boolean.class)
                .single();
        return taken ? null : lineId;
    }

    private @Nullable Long differenceEntryId(long lineId) {
        return jdbc.sql("SELECT difference_entry_id FROM bank_line WHERE id = :id")
                .param("id", lineId)
                .query(Long.class)
                .optional()
                .orElse(null);
    }

    private static void requireUnmatched(BankLineView line) {
        if (!"UNMATCHED".equals(line.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This line is already " + line.status().toLowerCase(Locale.ROOT));
        }
    }

    private static String matchedBy(@Nullable String actor) {
        if (actor == null || actor.isBlank()) {
            return "owner";
        }
        var trimmed = actor.trim();
        return abbreviate(trimmed, MAX_ACTOR);
    }

    private static String abbreviate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
