package com.vaabhi.accounts;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vaabhi.TestcontainersConfiguration;
import com.vaabhi.shared.rupees.Rupees;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Auto-matching and the match, post, ignore and undo actions on a bank line. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class BankMatchingIT extends BooksFixtures {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final String HEADER = "Date,Narration,Ref,Debit,Credit,Balance";

    @Autowired
    private JournalService journal;

    @Autowired
    private BankImportService imports;

    @Autowired
    private BankMatcher matcher;

    @Autowired
    private BankLineService lines;

    @Autowired
    private PeriodService periods;

    /** A day in the current books month, counting from its first day. */
    private static LocalDate day(int offset) {
        return LocalDate.now(IST).withDayOfMonth(1).plusDays(offset);
    }

    /** An amount no other test uses, so auto-matching cannot pick up their Bank movements. */
    private static BigDecimal amount() {
        return new BigDecimal(500000 + next() * 7).movePointLeft(2);
    }

    private static ColumnMapping mapping() {
        return new ColumnMapping(
                0,
                Map.of("date", 0, "description", 1, "reference", 2, "debit", 3, "credit", 4, "balance", 5),
                "yyyy-MM-dd",
                AmountStyle.DEBIT_CREDIT);
    }

    private static String row(
            String date,
            String narration,
            String reference,
            @Nullable BigDecimal debit,
            @Nullable BigDecimal credit,
            String balance) {
        return String.join(
                ",",
                date,
                narration,
                reference,
                debit == null ? "" : debit.toPlainString(),
                credit == null ? "" : credit.toPlainString(),
                balance);
    }

    private static byte[] csv(String... rows) {
        return (HEADER + "\n" + String.join("\n", rows) + "\n").getBytes(UTF_8);
    }

    /** A description no earlier test or run used, so the account's dedupe keys never collide with them. */
    private static String description() {
        return unique("UPI ") + "-" + System.nanoTime();
    }

    private long receipt(LocalDate date, BigDecimal amount) {
        return journal.post(
                        new NewEntry(
                                date,
                                "MANUAL",
                                null,
                                "MANUAL",
                                "Test receipt " + next(),
                                null,
                                null,
                                List.of(
                                        new NewEntryLine(accountId("BANK"), null, amount, Rupees.ZERO, null),
                                        new NewEntryLine(accountId("SALES"), null, Rupees.ZERO, amount, null))),
                        null)
                .id();
    }

    private BankLineView importLine(
            LocalDate date, @Nullable String reference, @Nullable BigDecimal debit, @Nullable BigDecimal credit) {
        var summary = imports.importStatement(
                "matching.csv",
                csv(row(date.toString(), description(), reference == null ? "" : reference, debit, credit, "")),
                mapping(),
                null,
                null,
                null);
        return imports.detail(summary.statementId(), null).lines().getFirst();
    }

    @Test
    void autoMatchesExactAmountWithinThreeDays() {
        var amount = amount();
        long receipt = receipt(day(1), amount);
        var line = importLine(day(3), null, null, amount);
        assertThat(line.status()).isEqualTo("MATCHED");
        assertThat(line.journalEntryId()).isEqualTo(receipt);
        assertThat(line.journalNumber()).isNotNull();
    }

    @Test
    void twoCandidatesStayUnmatched() {
        var amount = amount();
        long first = receipt(day(1), amount);
        long second = receipt(day(1), amount);
        var line = importLine(day(2), null, null, amount);
        assertThat(line.status()).isEqualTo("UNMATCHED");
        var sameAmount = matcher.candidates(line.id()).stream()
                .filter(candidate -> candidate.amountRupees().compareTo(amount) == 0)
                .map(MatchCandidate::journalEntryId)
                .toList();
        assertThat(sameAmount).contains(first, second);
    }

    @Test
    void reversedEntryIsNeverACandidate() {
        var amount = amount();
        long receipt = receipt(day(1), amount);
        var reversal = journal.reverse(receipt, null);
        var line = importLine(day(2), null, null, amount);
        assertThat(line.status()).isEqualTo("UNMATCHED");
        var candidates = matcher.candidates(line.id());
        assertThat(candidates).noneMatch(candidate -> candidate.journalEntryId() == receipt);
        assertThat(candidates).noneMatch(candidate -> candidate.journalEntryId() == reversal.id());
        assertThat(candidates).noneMatch(candidate -> candidate.amountRupees().compareTo(amount) == 0);
    }

    @Test
    void matchWithDifferencePostsFee() {
        var amount = amount();
        var lineAmount = amount.subtract(new BigDecimal("20.00"));
        long receipt = receipt(day(1), amount);
        var line = importLine(day(2), null, null, lineAmount);
        assertThat(line.status()).isEqualTo("UNMATCHED");

        long fees = accountId("EXPENSE_GATEWAY_FEES");
        var matched = lines.match(line.id(), new BankMatchForm(receipt, fees), null);

        assertThat(matched.status()).isEqualTo("MATCHED");
        assertThat(matched.journalEntryId()).isEqualTo(receipt);
        long differenceId = jdbc.sql("SELECT difference_entry_id FROM bank_line WHERE id = :id")
                .param("id", line.id())
                .query(Long.class)
                .single();
        assertThat(netInEntry(differenceId, fees)).isEqualByComparingTo("20.00");
        assertThat(netInEntry(differenceId, accountId("BANK"))).isEqualByComparingTo("-20.00");
    }

    @Test
    void matchWithoutDifferenceAccountIs400() {
        var amount = amount();
        long receipt = receipt(day(1), amount);
        var line = importLine(day(2), null, null, amount.subtract(new BigDecimal("20.00")));
        assertThatThrownBy(() -> lines.match(line.id(), new BankMatchForm(receipt, null), null))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
        assertThat(imports.line(line.id()).status()).isEqualTo("UNMATCHED");
    }

    @Test
    void postAsBankCharges() {
        var amount = amount();
        var line = importLine(day(2), null, amount, null);
        long bankCharges = accountId("EXPENSE_BANK_CHARGES");

        var posted = lines.post(line.id(), new BankPostForm(bankCharges, null), null);

        assertThat(posted.status()).isEqualTo("POSTED");
        assertThat(posted.journalEntryId()).isNotNull();
        long entryId = posted.journalEntryId();
        assertThat(netInEntry(entryId, bankCharges)).isEqualByComparingTo(amount);
        assertThat(netInEntry(entryId, accountId("BANK"))).isEqualByComparingTo(amount.negate());
    }

    @Test
    void undoPostReverses() {
        var amount = amount();
        var line = importLine(day(2), null, amount, null);
        long bankCharges = accountId("EXPENSE_BANK_CHARGES");
        var posted = lines.post(line.id(), new BankPostForm(bankCharges, null), null);

        var undone = lines.undo(line.id(), null);

        assertThat(undone.status()).isEqualTo("UNMATCHED");
        assertThat(undone.journalEntryId()).isNull();
        assertThat(posted.journalEntryId()).isNotNull();
        long postedEntryId = posted.journalEntryId();
        long reversalId = jdbc.sql("SELECT id FROM journal_entry WHERE reverses_id = :id")
                .param("id", postedEntryId)
                .query(Long.class)
                .single();
        var total = netInEntry(postedEntryId, bankCharges).add(netInEntry(reversalId, bankCharges));
        assertThat(total).isEqualByComparingTo("0.00");
    }

    @Test
    void undoInLockedPeriodIs409AndKeepsStatus() {
        var amount = amount();
        var date = day(2);
        var line = importLine(date, null, amount, null);
        var posted = lines.post(line.id(), new BankPostForm(accountId("EXPENSE_BANK_CHARGES"), null), null);
        assertThat(posted.status()).isEqualTo("POSTED");
        periods.lock(YearMonth.from(date), null);
        try {
            assertThatThrownBy(() -> lines.undo(line.id(), null))
                    .isInstanceOfSatisfying(
                            ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
        } finally {
            periods.unlock(YearMonth.from(date));
        }
        assertThat(imports.line(line.id()).status()).isEqualTo("POSTED");
    }

    @Test
    void ignoreNeedsReason() {
        var line = importLine(day(2), null, null, amount());
        assertThatThrownBy(() -> lines.ignore(line.id(), new BankIgnoreForm("  "), null))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
        assertThat(imports.line(line.id()).status()).isEqualTo("UNMATCHED");
    }
}
