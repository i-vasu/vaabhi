package com.vaabhi.accounts;

import com.vaabhi.shared.rupees.Rupees;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** The Schedule III profit and loss account and balance sheet. */
@Service
public class StatementService {

    private static final Set<AccountType> PROFIT_AND_LOSS = Set.of(AccountType.INCOME, AccountType.EXPENSE);
    private static final Set<AccountType> BALANCE_SHEET =
            Set.of(AccountType.ASSET, AccountType.LIABILITY, AccountType.EQUITY);
    private static final String UNCLOSED_PROFIT = "Profit not yet closed";

    private final JdbcClient jdbc;

    StatementService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    record StatementRow(
            long accountId,
            String code,
            String name,
            AccountType type,
            ScheduleLine scheduleLine,
            BigDecimal net) {}

    /** Profit and loss for a period, with the preceding period of the same length (or the previous year) beside it. */
    @Transactional(readOnly = true)
    public ProfitAndLoss profitAndLoss(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "From must be on or before to");
        }
        LocalDate previousFrom;
        LocalDate previousTo;
        if (from.equals(FiscalYears.startOf(from)) && to.equals(FiscalYears.end(from))) {
            previousFrom = from.minusYears(1);
            previousTo = to.minusYears(1);
        } else {
            long days = ChronoUnit.DAYS.between(from, to) + 1;
            previousTo = from.minusDays(1);
            previousFrom = previousTo.minusDays(days - 1);
        }
        var current = profitAndLossRows(from, to);
        var previous = profitAndLossRows(previousFrom, previousTo);
        var income = List.copyOf(lines(Set.of(AccountType.INCOME), current, previous));
        var expenses = List.copyOf(lines(Set.of(AccountType.EXPENSE), current, previous));
        var totalIncome = Rupees.sum(income, StatementLine::amountRupees);
        var totalExpenses = Rupees.sum(expenses, StatementLine::amountRupees);
        var previousTotalIncome = Rupees.sum(income, StatementLine::previousRupees);
        var previousTotalExpenses = Rupees.sum(expenses, StatementLine::previousRupees);
        return new ProfitAndLoss(
                from,
                to,
                previousFrom,
                previousTo,
                income,
                expenses,
                totalIncome,
                totalExpenses,
                Rupees.of(totalIncome.subtract(totalExpenses)),
                Rupees.of(previousTotalIncome.subtract(previousTotalExpenses)));
    }

    /** The balance sheet as at a date, with the position a year earlier beside it. */
    @Transactional(readOnly = true)
    public BalanceSheet balanceSheet(LocalDate asOf) {
        var previousAsOf = asOf.minusYears(1);
        var current = balanceSheetRows(asOf);
        var previous = balanceSheetRows(previousAsOf);
        var equityAndLiabilities = new ArrayList<>(
                lines(Set.of(AccountType.EQUITY, AccountType.LIABILITY), current, previous));
        var assets = List.copyOf(lines(Set.of(AccountType.ASSET), current, previous));
        var unclosed = unclosedProfit(asOf);
        var previousUnclosed = unclosedProfit(previousAsOf);
        if (unclosed.signum() != 0 || previousUnclosed.signum() != 0) {
            addUnclosedProfit(equityAndLiabilities, unclosed, previousUnclosed);
        }
        var totalEquityAndLiabilities = Rupees.sum(equityAndLiabilities, StatementLine::amountRupees);
        var totalAssets = Rupees.sum(assets, StatementLine::amountRupees);
        var previousTotalEquityAndLiabilities = Rupees.sum(equityAndLiabilities, StatementLine::previousRupees);
        var previousTotalAssets = Rupees.sum(assets, StatementLine::previousRupees);
        return new BalanceSheet(
                asOf,
                previousAsOf,
                List.copyOf(equityAndLiabilities),
                assets,
                totalEquityAndLiabilities,
                totalAssets,
                previousTotalEquityAndLiabilities,
                previousTotalAssets,
                totalEquityAndLiabilities.compareTo(totalAssets) == 0);
    }

    /**
     * Net credits minus debits over every income and expense account up to a date, closing entries included: what the
     * profit and loss side of the balance sheet is still carrying.
     */
    @Transactional(readOnly = true)
    public BigDecimal unclosedProfit(LocalDate asOf) {
        var net = jdbc.sql("""
                        SELECT COALESCE(SUM(l.credit_rupees), 0) - COALESCE(SUM(l.debit_rupees), 0)
                        FROM journal_line l
                        JOIN journal_entry e ON e.id = l.entry_id
                        JOIN account a ON a.id = l.account_id
                        WHERE e.entry_date <= :asOf AND a.type IN ('INCOME', 'EXPENSE')
                        """)
                .param("asOf", asOf)
                .query(BigDecimal.class)
                .single();
        return Rupees.of(net);
    }

    private List<StatementRow> profitAndLossRows(LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        SELECT a.id AS account_id, a.code, a.name, a.type, a.schedule_line,
                               COALESCE(SUM(l.debit_rupees), 0) - COALESCE(SUM(l.credit_rupees), 0) AS net
                        FROM account a
                        JOIN journal_line l ON l.account_id = a.id
                        JOIN journal_entry e ON e.id = l.entry_id
                        WHERE e.entry_date BETWEEN :from AND :to
                          AND a.type IN (:types)
                          AND NOT (e.source_type = 'YEAR_END' AND e.source_step = 'CLOSE')
                          AND NOT EXISTS (SELECT 1 FROM journal_entry c
                                          WHERE c.id = e.reverses_id
                                            AND c.source_type = 'YEAR_END' AND c.source_step = 'CLOSE')
                        GROUP BY a.id
                        """)
                .param("from", from)
                .param("to", to)
                .param("types", names(PROFIT_AND_LOSS))
                .query(StatementRow.class)
                .list();
    }

    private List<StatementRow> balanceSheetRows(LocalDate asOf) {
        return jdbc.sql("""
                        SELECT a.id AS account_id, a.code, a.name, a.type, a.schedule_line,
                               COALESCE(SUM(l.debit_rupees), 0) - COALESCE(SUM(l.credit_rupees), 0) AS net
                        FROM account a
                        JOIN journal_line l ON l.account_id = a.id
                        JOIN journal_entry e ON e.id = l.entry_id
                        WHERE e.entry_date <= :asOf AND a.type IN (:types)
                        GROUP BY a.id
                        """)
                .param("asOf", asOf)
                .param("types", names(BALANCE_SHEET))
                .query(StatementRow.class)
                .list();
    }

    private static List<StatementLine> lines(
            Set<AccountType> sides, List<StatementRow> currentRows, List<StatementRow> previousRows) {
        Map<Long, StatementRow> current =
                currentRows.stream().collect(Collectors.toMap(StatementRow::accountId, Function.identity()));
        Map<Long, StatementRow> previous =
                previousRows.stream().collect(Collectors.toMap(StatementRow::accountId, Function.identity()));
        var ids = new LinkedHashSet<Long>();
        ids.addAll(current.keySet());
        ids.addAll(previous.keySet());
        Map<ScheduleLine, List<StatementAccount>> accounts = new EnumMap<>(ScheduleLine.class);
        for (long id : ids) {
            var amount = current.containsKey(id) ? normalised(current.get(id)) : Rupees.ZERO;
            var previousAmount = previous.containsKey(id) ? normalised(previous.get(id)) : Rupees.ZERO;
            if (amount.signum() == 0 && previousAmount.signum() == 0) {
                continue;
            }
            var row = current.containsKey(id) ? current.get(id) : previous.get(id);
            accounts.computeIfAbsent(row.scheduleLine(), line -> new ArrayList<>())
                    .add(new StatementAccount(id, row.code().trim(), row.name(), amount, previousAmount));
        }
        var lines = new ArrayList<StatementLine>();
        for (var line : ScheduleLine.values()) {
            if (!sides.contains(line.side())) {
                continue;
            }
            var lineAccounts = accounts.get(line);
            if (lineAccounts == null) {
                continue;
            }
            lineAccounts.sort(Comparator.comparing(StatementAccount::code));
            var amount = Rupees.sum(lineAccounts, StatementAccount::amountRupees);
            var previousAmount = Rupees.sum(lineAccounts, StatementAccount::previousRupees);
            if (amount.signum() == 0 && previousAmount.signum() == 0 && lineAccounts.isEmpty()) {
                continue;
            }
            lines.add(new StatementLine(line, line.label(), amount, previousAmount, List.copyOf(lineAccounts)));
        }
        return lines;
    }

    private static void addUnclosedProfit(List<StatementLine> lines, BigDecimal unclosed, BigDecimal previousUnclosed) {
        var account = new StatementAccount(0, "", UNCLOSED_PROFIT, unclosed, previousUnclosed);
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            if (line.line() == ScheduleLine.RESERVES_SURPLUS) {
                var accounts = new ArrayList<>(line.accounts());
                accounts.add(account);
                lines.set(
                        i,
                        new StatementLine(
                                line.line(),
                                line.label(),
                                Rupees.of(line.amountRupees().add(unclosed)),
                                Rupees.of(line.previousRupees().add(previousUnclosed)),
                                List.copyOf(accounts)));
                return;
            }
        }
        int at = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).line().ordinal() > ScheduleLine.RESERVES_SURPLUS.ordinal()) {
                at = i;
                break;
            }
        }
        lines.add(
                at,
                new StatementLine(
                        ScheduleLine.RESERVES_SURPLUS,
                        ScheduleLine.RESERVES_SURPLUS.label(),
                        unclosed,
                        previousUnclosed,
                        List.of(account)));
    }

    private static BigDecimal normalised(StatementRow row) {
        return Rupees.of(row.type().debitNormal() ? row.net() : row.net().negate());
    }

    private static List<String> names(Set<AccountType> types) {
        return types.stream().map(AccountType::name).toList();
    }
}
