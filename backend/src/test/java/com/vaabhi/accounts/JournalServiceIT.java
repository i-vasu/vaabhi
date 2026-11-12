package com.vaabhi.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vaabhi.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Journal posting, reversal, ledgers and the trial balance against real Postgres. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class JournalServiceIT {

    private static final AtomicInteger CODES = new AtomicInteger(7000);
    private static final AtomicInteger SOURCES = new AtomicInteger(700_000);

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private JournalService journal;

    @Autowired
    private ChartService chart;

    @Autowired
    private PeriodService periods;

    @Autowired
    private BooksSettingsService settings;

    private @org.jspecify.annotations.Nullable LocalDate originalStart;

    @BeforeEach
    void rememberStart() {
        originalStart = settings.get().startDate();
    }

    @AfterEach
    void restoreStart() {
        jdbc.sql("UPDATE books_setting SET start_date = :start WHERE id = 1")
                .param("start", originalStart)
                .update();
    }

    private Account newAccount(AccountType type) {
        var code = "%04d".formatted(CODES.incrementAndGet());
        return chart.create(new AccountForm(code, "Test " + code, type, null, null));
    }

    private static NewEntryLine debit(long account, String amount) {
        return new NewEntryLine(account, null, new BigDecimal(amount), BigDecimal.ZERO, null);
    }

    private static NewEntryLine credit(long account, String amount) {
        return new NewEntryLine(account, null, BigDecimal.ZERO, new BigDecimal(amount), null);
    }

    private static NewEntry manual(LocalDate date, List<NewEntryLine> lines) {
        return new NewEntry(date, "MANUAL", null, "MANUAL", "Test entry", null, null, lines);
    }

    private static void assertRejected(Runnable action, HttpStatus status, String message) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
            assertThat(e.getStatusCode()).isEqualTo(status);
            assertThat(e.getReason()).isEqualTo(message);
        });
    }

    @Test
    void postsBalancedEntryWithNumber() {
        var cash = newAccount(AccountType.ASSET);
        var sales = newAccount(AccountType.INCOME);

        var entry = journal.post(
                manual(LocalDate.of(2031, 1, 10), List.of(debit(cash.id(), "250"), credit(sales.id(), "250"))),
                "Asha Rao");

        assertThat(entry.number()).matches("JV/\\d{2}-\\d{2}/\\d{5}");
        assertThat(entry.createdBy()).isEqualTo("Asha Rao");
        assertThat(entry.lines()).hasSize(2);
        assertThat(entry.lines().get(0).accountCode()).isEqualTo(cash.code());
        assertThat(journal.get(entry.id()).number()).isEqualTo(entry.number());
    }

    @Test
    void rejectsOneLine() {
        var cash = newAccount(AccountType.ASSET);

        assertRejected(
                () -> journal.post(manual(LocalDate.of(2031, 1, 11), List.of(debit(cash.id(), "10"))), null),
                HttpStatus.BAD_REQUEST,
                "Add at least two lines");
    }

    @Test
    void rejectsLineWithBothSides() {
        var cash = newAccount(AccountType.ASSET);
        var sales = newAccount(AccountType.INCOME);
        var both = new NewEntryLine(cash.id(), null, new BigDecimal("10"), new BigDecimal("10"), null);

        assertRejected(
                () -> journal.post(manual(LocalDate.of(2031, 1, 12), List.of(both, credit(sales.id(), "10"))), null),
                HttpStatus.BAD_REQUEST,
                "Each line needs a debit or a credit, not both");
    }

    @Test
    void rejectsUnequalEntry() {
        var cash = newAccount(AccountType.ASSET);
        var sales = newAccount(AccountType.INCOME);

        assertRejected(
                () -> journal.post(
                        manual(LocalDate.of(2031, 1, 12), List.of(debit(cash.id(), "10"), credit(sales.id(), "9"))),
                        null),
                HttpStatus.BAD_REQUEST,
                "Debits and credits must be equal");
    }

    @Test
    void rejectsInactiveAccount() {
        var cash = newAccount(AccountType.ASSET);
        var off = newAccount(AccountType.INCOME);
        chart.update(off.id(), new AccountForm(off.code(), off.name(), off.type(), null, false));

        assertRejected(
                () -> journal.post(
                        manual(LocalDate.of(2031, 1, 13), List.of(debit(cash.id(), "10"), credit(off.id(), "10"))),
                        null),
                HttpStatus.BAD_REQUEST,
                off.code() + " " + off.name() + " is turned off");
    }

    @Test
    void rejectsLockedMonth() {
        var cash = newAccount(AccountType.ASSET);
        var sales = newAccount(AccountType.INCOME);
        periods.lock(YearMonth.of(2032, 3), "Asha Rao");
        try {
            assertRejected(
                    () -> journal.post(
                            manual(
                                    LocalDate.of(2032, 3, 10),
                                    List.of(debit(cash.id(), "10"), credit(sales.id(), "10"))),
                            null),
                    HttpStatus.CONFLICT,
                    "March 2032 is locked");
        } finally {
            periods.unlock(YearMonth.of(2032, 3));
        }
    }

    @Test
    void reverseSwapsSides() {
        var cash = newAccount(AccountType.ASSET);
        var sales = newAccount(AccountType.INCOME);
        var original = journal.post(
                manual(LocalDate.of(2031, 2, 1), List.of(debit(cash.id(), "75"), credit(sales.id(), "75"))), null);

        var reversal = journal.reverse(original.id(), "Asha Rao");

        assertThat(reversal.reversesId()).isEqualTo(original.id());
        assertThat(reversal.sourceStep()).isEqualTo("REVERSAL");
        assertThat(reversal.lines().get(0).accountId()).isEqualTo(cash.id());
        assertThat(reversal.lines().get(0).creditRupees()).isEqualByComparingTo("75");
        assertThat(reversal.lines().get(1).debitRupees()).isEqualByComparingTo("75");
        assertThat(journal.get(original.id()).reversedById()).isEqualTo(reversal.id());
        assertRejected(
                () -> journal.reverse(original.id(), null), HttpStatus.CONFLICT, "This entry is already reversed");
    }

    @Test
    void reverseRefusesSweptEntry() {
        var debtors = newAccount(AccountType.ASSET);
        var sales = newAccount(AccountType.INCOME);
        var swept = journal.post(
                new NewEntry(
                        LocalDate.of(2031, 2, 2),
                        "INVOICE",
                        (long) SOURCES.incrementAndGet(),
                        "ISSUE",
                        "Invoice",
                        null,
                        null,
                        List.of(debit(debtors.id(), "40"), credit(sales.id(), "40"))),
                null);

        assertRejected(
                () -> journal.reverse(swept.id(), null),
                HttpStatus.CONFLICT,
                "Only manual and opening entries can be reversed");
    }

    @Test
    void trialBalanceBalances() {
        var cash = newAccount(AccountType.ASSET);
        var sales = newAccount(AccountType.INCOME);
        var asOf = LocalDate.of(2031, 12, 31);
        var before = journal.trialBalance(asOf);

        journal.post(
                manual(LocalDate.of(2031, 3, 5), List.of(debit(cash.id(), "300"), credit(sales.id(), "300"))), null);
        var after = journal.trialBalance(asOf);

        assertThat(after.balanced()).isTrue();
        assertThat(after.totalDebitRupees().subtract(before.totalDebitRupees())).isEqualByComparingTo("300");
        assertThat(after.lines())
                .anySatisfy(line -> {
                    assertThat(line.accountId()).isEqualTo(cash.id());
                    assertThat(line.debitRupees()).isEqualByComparingTo("300");
                })
                .anySatisfy(line -> {
                    assertThat(line.accountId()).isEqualTo(sales.id());
                    assertThat(line.creditRupees()).isEqualByComparingTo("300");
                });
    }

    @Test
    void ledgerRunningBalance() {
        var bank = newAccount(AccountType.ASSET);
        var sales = newAccount(AccountType.INCOME);
        journal.post(
                manual(LocalDate.of(2031, 4, 20), List.of(debit(bank.id(), "50"), credit(sales.id(), "50"))), null);
        journal.post(
                manual(LocalDate.of(2031, 5, 3), List.of(debit(bank.id(), "100"), credit(sales.id(), "100"))), null);
        journal.post(manual(LocalDate.of(2031, 5, 9), List.of(debit(sales.id(), "30"), credit(bank.id(), "30"))), null);

        var statement = journal.ledger(bank.id(), LocalDate.of(2031, 5, 1), LocalDate.of(2031, 5, 31));

        assertThat(statement.openingRupees()).isEqualByComparingTo("50");
        assertThat(statement.lines()).hasSize(2);
        assertThat(statement.lines().get(0).balanceRupees()).isEqualByComparingTo("150");
        assertThat(statement.lines().get(1).balanceRupees()).isEqualByComparingTo("120");
        assertThat(statement.totalDebitRupees()).isEqualByComparingTo("100");
        assertThat(statement.totalCreditRupees()).isEqualByComparingTo("30");
        assertThat(statement.closingRupees()).isEqualByComparingTo("120");
        assertThat(chart.get(bank.id()).balanceRupees()).isEqualByComparingTo("120");
    }

    @Test
    void periodsEmptyWithoutStartDate() {
        jdbc.sql("UPDATE books_setting SET start_date = NULL WHERE id = 1").update();

        assertThat(periods.list()).isEmpty();

        var today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        jdbc.sql("UPDATE books_setting SET start_date = :start WHERE id = 1")
                .param("start", today.minusMonths(2).withDayOfMonth(15))
                .update();
        var months = periods.list();
        assertThat(months).hasSize(3);
        assertThat(months.get(0).month()).isEqualTo(today.withDayOfMonth(1));
    }

    @Test
    void reversalCannotBeReversed() {
        var cash = newAccount(AccountType.ASSET);
        var sales = newAccount(AccountType.INCOME);
        var original = journal.post(
                manual(LocalDate.of(2031, 2, 3), List.of(debit(cash.id(), "20"), credit(sales.id(), "20"))), null);
        var reversal = journal.reverse(original.id(), null);

        assertRejected(() -> journal.reverse(reversal.id(), null), HttpStatus.CONFLICT, "A reversal can't be reversed");
    }

    @Test
    void mappedAccountCannotBeTurnedOff() {
        var bank = newAccount(AccountType.ASSET);
        var original = settings.get();
        var modes = new java.util.HashMap<>(original.modeAccounts());
        modes.put("CARD", bank.id());
        settings.save(new BooksSettings(original.startDate(), Map.copyOf(modes), null), null);
        try {
            assertRejected(
                    () -> chart.update(bank.id(), new AccountForm(bank.code(), bank.name(), bank.type(), null, false)),
                    HttpStatus.CONFLICT,
                    "This account is used for CARD payments; pick another account there first");
        } finally {
            settings.save(new BooksSettings(original.startDate(), original.modeAccounts(), null), null);
        }
    }

    @Test
    void modeAccountMustBeAsset() {
        var sales = chart.idOf("SALES");
        var current = settings.get();
        var modes = new java.util.HashMap<>(current.modeAccounts());
        modes.put("CASH", sales);

        assertRejected(
                () -> settings.save(new BooksSettings(current.startDate(), Map.copyOf(modes), null), null),
                HttpStatus.BAD_REQUEST,
                "Pick an asset account for CASH");
    }
}
