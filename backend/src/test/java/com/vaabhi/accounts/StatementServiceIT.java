package com.vaabhi.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vaabhi.TestcontainersConfiguration;
import com.vaabhi.shared.rupees.Rupees;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The Schedule III profit and loss account and balance sheet, including the year-end close. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class StatementServiceIT extends BooksFixtures {

    /** A financial year no other test posts in. */
    private static final LocalDate FY_START = LocalDate.of(2071, 4, 1);

    private static final LocalDate FY_END = LocalDate.of(2072, 3, 31);

    @Autowired
    private StatementService statements;

    @Autowired
    private JournalService journal;

    private static NewEntryLine debit(long account, String amount) {
        return new NewEntryLine(account, null, new BigDecimal(amount), BigDecimal.ZERO, null);
    }

    private static NewEntryLine credit(long account, String amount) {
        return new NewEntryLine(account, null, BigDecimal.ZERO, new BigDecimal(amount), null);
    }

    private void post(LocalDate date, List<NewEntryLine> lines) {
        journal.post(new NewEntry(date, "MANUAL", null, "MANUAL", "Statement test", null, null, lines), "test");
    }

    private static StatementLine line(List<StatementLine> lines, ScheduleLine which) {
        return lines.stream()
                .filter(line -> line.line() == which)
                .findFirst()
                .orElseThrow(() -> new AssertionError(which + " is missing"));
    }

    private static Stream<StatementAccount> accounts(BalanceSheet sheet) {
        return Stream.concat(sheet.equityAndLiabilities().stream(), sheet.assets().stream())
                .flatMap(line -> line.accounts().stream());
    }

    private static BigDecimal unclosed(BalanceSheet sheet) {
        return accounts(sheet)
                .filter(account -> account.accountId() == 0)
                .findFirst()
                .map(StatementAccount::amountRupees)
                .orElse(Rupees.ZERO);
    }

    private static BigDecimal amountOf(BalanceSheet sheet, long accountId) {
        return accounts(sheet)
                .filter(account -> account.accountId() == accountId)
                .findFirst()
                .map(StatementAccount::amountRupees)
                .orElse(Rupees.ZERO);
    }

    @Test
    void profitAndLossAndYearEndClose() {
        var before = statements.balanceSheet(FY_END);
        var unclosedBefore = unclosed(before);
        var retainedBefore = amountOf(before, accountId("RETAINED_EARNINGS"));

        post(
                LocalDate.of(2071, 5, 10),
                List.of(debit(accountId("DEBTORS"), "1000"), credit(accountId("SALES"), "1000")));
        post(
                LocalDate.of(2071, 5, 12),
                List.of(debit(accountId("EXPENSE_RENT"), "300"), credit(accountId("CASH"), "300")));

        var profitAndLoss = statements.profitAndLoss(FY_START, FY_END);
        assertThat(line(profitAndLoss.income(), ScheduleLine.REVENUE_FROM_OPERATIONS)
                        .amountRupees())
                .isEqualByComparingTo("1000.00");
        assertThat(line(profitAndLoss.expenses(), ScheduleLine.OTHER_EXPENSES).amountRupees())
                .isEqualByComparingTo("300.00");
        assertThat(profitAndLoss.profitRupees()).isEqualByComparingTo("700.00");
        assertThat(profitAndLoss.previousFrom()).isEqualTo(LocalDate.of(2070, 4, 1));
        assertThat(profitAndLoss.previousTo()).isEqualTo(LocalDate.of(2071, 3, 31));

        var afterPosting = statements.balanceSheet(FY_END);
        assertThat(unclosed(afterPosting)).isEqualByComparingTo(unclosedBefore.add(new BigDecimal("700")));
        assertThat(afterPosting.balanced()).isTrue();

        journal.post(
                new NewEntry(
                        FY_END,
                        "YEAR_END",
                        2_071_999L,
                        "CLOSE",
                        "Closing entry",
                        null,
                        null,
                        List.of(
                                debit(accountId("SALES"), "1000"),
                                credit(accountId("EXPENSE_RENT"), "300"),
                                credit(accountId("RETAINED_EARNINGS"), "700"))),
                "test");

        assertThat(statements.profitAndLoss(FY_START, FY_END).profitRupees()).isEqualByComparingTo("700.00");

        var afterClose = statements.balanceSheet(FY_END);
        assertThat(amountOf(afterClose, accountId("RETAINED_EARNINGS")))
                .isEqualByComparingTo(retainedBefore.add(new BigDecimal("700")));
        assertThat(unclosed(afterClose)).isEqualByComparingTo(unclosedBefore);
        assertThat(afterClose.balanced()).isTrue();
    }

    @Test
    void balanceSheetCarriesLaterProfitNotYetClosed() {
        var atYearEnd = statements.balanceSheet(FY_END);
        post(LocalDate.of(2072, 5, 10), List.of(debit(accountId("DEBTORS"), "200"), credit(accountId("SALES"), "200")));

        var later = statements.balanceSheet(LocalDate.of(2072, 6, 30));

        assertThat(unclosed(later).subtract(unclosed(atYearEnd))).isEqualByComparingTo("200.00");
        assertThat(later.balanced()).isTrue();
    }

    @Test
    void rejectsReversedRange() {
        assertThatThrownBy(() -> statements.profitAndLoss(LocalDate.of(2071, 5, 2), LocalDate.of(2071, 5, 1)))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    void customRangeComparesThePrecedingWindowOfTheSameLength() {
        var profitAndLoss = statements.profitAndLoss(LocalDate.of(2071, 5, 1), LocalDate.of(2071, 5, 31));

        assertThat(profitAndLoss.previousFrom()).isEqualTo(LocalDate.of(2071, 3, 31));
        assertThat(profitAndLoss.previousTo()).isEqualTo(LocalDate.of(2071, 4, 30));
    }
}
