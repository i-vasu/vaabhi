package com.vaabhi.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Matches a statement's lines to journal Bank movements. */
@Service
public class BankMatcher {

    private static final int AUTO_WINDOW_DAYS = 3;
    private static final int MANUAL_WINDOW_DAYS = 15;
    private static final int CANDIDATE_LIMIT = 20;

    /**
     * Only an entry with a Bank line, not reversed and not itself a reversal, and not already linked to a bank line
     * can be matched. BANK_LINE / MATCH entries are the differences a match posted, never candidates.
     */
    private static final String CANDIDATE_FILTER = """
              AND EXISTS (SELECT 1 FROM journal_line bl
                          JOIN account ba ON ba.id = bl.account_id
                          WHERE bl.entry_id = e.id AND ba.system_key = 'BANK')
              AND e.reverses_id IS NULL
              AND NOT EXISTS (SELECT 1 FROM journal_entry r WHERE r.reverses_id = e.id)
              AND NOT EXISTS (SELECT 1 FROM bank_line b WHERE b.journal_entry_id = e.id)
              AND NOT EXISTS (SELECT 1 FROM bank_line b WHERE b.difference_entry_id = e.id)
              AND NOT (e.source_type = 'BANK_LINE' AND e.source_step = 'MATCH')
            """;

    private final JdbcClient jdbc;

    BankMatcher(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    record AutoLine(
            long id, LocalDate date, @Nullable String reference, BigDecimal debitRupees, BigDecimal creditRupees) {}

    record CandidateLine(long id, LocalDate date, BigDecimal debitRupees, BigDecimal creditRupees) {}

    /** Matches the statement's unmatched lines one at a time, so a matched entry is not offered to a later line. */
    @Transactional
    public int autoMatch(long statementId) {
        var lines = jdbc.sql("""
                        SELECT id, line_date AS date, reference, debit_rupees, credit_rupees
                        FROM bank_line
                        WHERE statement_id = :statement AND status = 'UNMATCHED'
                        ORDER BY line_date, id
                        """)
                .param("statement", statementId)
                .query(AutoLine.class)
                .list();
        int matched = 0;
        for (var line : lines) {
            Long entryId = null;
            if (line.reference() != null && !line.reference().isBlank()) {
                entryId = only(referenceCandidates(line));
            }
            if (entryId == null) {
                entryId = only(exactAmountCandidates(line));
            }
            if (entryId != null) {
                markMatched(line.id(), entryId);
                matched++;
            }
        }
        return matched;
    }

    /** Entries a line could be matched to by hand: same side, near in date, closest amount first. 404 if no line. */
    @Transactional(readOnly = true)
    // Sonar java:S2077: the SQL is assembled only from fixed fragments in this class; every value is a bound parameter.
    @SuppressWarnings("java:S2077")
    public List<MatchCandidate> candidates(long lineId) {
        var line = jdbc.sql("""
                        SELECT id, line_date AS date, debit_rupees, credit_rupees
                        FROM bank_line WHERE id = :id
                        """)
                .param("id", lineId)
                .query(CandidateLine.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Line not found"));
        var signed = line.creditRupees().subtract(line.debitRupees());
        String sign = signed.signum() > 0 ? "> 0" : "< 0";
        return jdbc.sql("""
                        SELECT e.id AS journal_entry_id, e.number, e.entry_date AS date, e.narration,
                               ABS(SUM(l.debit_rupees - l.credit_rupees)) AS amount_rupees
                        FROM journal_entry e
                        JOIN journal_line l ON l.entry_id = e.id
                        JOIN account a ON a.id = l.account_id AND a.system_key = 'BANK'
                        WHERE e.entry_date BETWEEN :from AND :to
                        """ + CANDIDATE_FILTER + """
                        GROUP BY e.id, e.number, e.entry_date, e.narration
                        HAVING SUM(l.debit_rupees - l.credit_rupees) """ + sign + "\n" + """
                        ORDER BY ABS(SUM(l.debit_rupees - l.credit_rupees) - :amount),
                                 ABS(e.entry_date - CAST(:lineDate AS date)), e.id
                        LIMIT :limit
                        """)
                .param("from", line.date().minusDays(MANUAL_WINDOW_DAYS))
                .param("to", line.date().plusDays(MANUAL_WINDOW_DAYS))
                .param("amount", signed)
                .param("lineDate", line.date())
                .param("limit", CANDIDATE_LIMIT)
                .query(MatchCandidate.class)
                .list();
    }

    /** The entry's Bank amount (credit less debit) when it can be matched, else null. */
    @Transactional(readOnly = true)
    @Nullable
    BigDecimal bankAmount(long journalEntryId) {
        return jdbc.sql("""
                        SELECT SUM(l.debit_rupees - l.credit_rupees)
                        FROM journal_entry e
                        JOIN journal_line l ON l.entry_id = e.id
                        JOIN account a ON a.id = l.account_id AND a.system_key = 'BANK'
                        WHERE e.id = :id
                        """ + CANDIDATE_FILTER + """
                        GROUP BY e.id
                        """)
                .param("id", journalEntryId)
                .query(BigDecimal.class)
                .optional()
                .orElse(null);
    }

    /** Entries the Razorpay or COD remittance the line's reference names posted; at most two are needed. */
    // Sonar java:S2077: the SQL is assembled only from fixed fragments in this class; every value is a bound parameter.
    @SuppressWarnings("java:S2077")
    private List<Long> referenceCandidates(AutoLine line) {
        var amount = line.creditRupees().subtract(line.debitRupees());
        String sign = amount.signum() > 0 ? "> 0" : "< 0";
        return jdbc.sql("""
                        SELECT e.id
                        FROM journal_entry e
                        WHERE ((e.source_type = 'RAZORPAY_SETTLEMENT'
                                AND e.source_id IN (SELECT seq FROM razorpay_settlement WHERE utr = :ref))
                            OR (e.source_type = 'COD_REMITTANCE'
                                AND e.source_id IN (SELECT id FROM cod_remittance WHERE utr = :ref)))
                          AND (SELECT SUM(sl.debit_rupees - sl.credit_rupees)
                               FROM journal_line sl
                               JOIN account sa ON sa.id = sl.account_id
                               WHERE sl.entry_id = e.id AND sa.system_key = 'BANK') """ + sign + "\n" + CANDIDATE_FILTER + """
                        LIMIT 2
                        """)
                .param("ref", line.reference())
                .query(Long.class)
                .list();
    }

    /** Entries with the line's signed amount on the Bank account within the auto-match window. */
    // Sonar java:S2077: the SQL is assembled only from fixed fragments in this class; every value is a bound parameter.
    @SuppressWarnings("java:S2077")
    private List<Long> exactAmountCandidates(AutoLine line) {
        var amount = line.creditRupees().subtract(line.debitRupees());
        String sign = amount.signum() > 0 ? "> 0" : "< 0";
        return jdbc.sql("""
                        SELECT e.id
                        FROM journal_entry e
                        JOIN journal_line l ON l.entry_id = e.id
                        JOIN account a ON a.id = l.account_id AND a.system_key = 'BANK'
                        WHERE e.entry_date BETWEEN :from AND :to
                        """ + CANDIDATE_FILTER + """
                        GROUP BY e.id
                        HAVING SUM(l.debit_rupees - l.credit_rupees) """ + sign + """
                         AND SUM(l.debit_rupees - l.credit_rupees) = :amount
                        LIMIT 2
                        """)
                .param("from", line.date().minusDays(AUTO_WINDOW_DAYS))
                .param("to", line.date().plusDays(AUTO_WINDOW_DAYS))
                .param("amount", amount)
                .query(Long.class)
                .list();
    }

    private void markMatched(long lineId, long entryId) {
        jdbc.sql("""
                        UPDATE bank_line
                        SET status = 'MATCHED', journal_entry_id = :entry, matched_by = 'auto', matched_at = now()
                        WHERE id = :id
                        """).param("entry", entryId).param("id", lineId).update();
    }

    private static @Nullable Long only(List<Long> ids) {
        return ids.size() == 1 ? ids.getFirst() : null;
    }
}
