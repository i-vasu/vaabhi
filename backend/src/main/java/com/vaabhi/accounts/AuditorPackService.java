package com.vaabhi.accounts;

import com.vaabhi.settings.SettingsService;
import com.vaabhi.shared.rupees.Rupees;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The workbook an auditor asks for: the statements, registers, ledgers and returns of one financial year. */
@Service
@Transactional(readOnly = true)
public class AuditorPackService {

    private static final ZoneId SHOP_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter GENERATED_AT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH);

    private final JdbcClient jdbc;
    private final StatementService statements;
    private final YearEndService yearEnds;
    private final Gstr3bService gstr3b;
    private final TdsReturnService tdsReturns;
    private final JournalService journal;
    private final BankReconciliationService reconciliations;
    private final AssetService assets;
    private final SettingsService settings;

    AuditorPackService(
            JdbcClient jdbc,
            StatementService statements,
            YearEndService yearEnds,
            Gstr3bService gstr3b,
            TdsReturnService tdsReturns,
            JournalService journal,
            BankReconciliationService reconciliations,
            AssetService assets,
            SettingsService settings) {
        this.jdbc = jdbc;
        this.statements = statements;
        this.yearEnds = yearEnds;
        this.gstr3b = gstr3b;
        this.tdsReturns = tdsReturns;
        this.journal = journal;
        this.reconciliations = reconciliations;
        this.assets = assets;
        this.settings = settings;
    }

    /** Writes the year starting at {@code fyStart} as an xlsx workbook, one sheet per part of the pack. */
    public void write(LocalDate fyStart, OutputStream out) throws IOException {
        var end = FiscalYears.end(fyStart);
        var yearEnd = yearEnds.get(fyStart);
        try (var workbook = new Workbook(out, "Vaabhi", "1.0")) {
            about(workbook, fyStart, end, yearEnd);
            trialBalance(workbook, end);
            profitAndLoss(workbook, fyStart, end);
            balanceSheet(workbook, end);
            dayBook(workbook, fyStart, end);
            ledgers(workbook, fyStart, end);
            debtors(workbook, end);
            creditors(workbook, end);
            salesRegister(workbook, fyStart, end);
            purchaseRegister(workbook, fyStart, end);
            fixedAssets(workbook, fyStart, end);
            closingStock(workbook, fyStart, yearEnd);
            bankReconciliation(workbook, fyStart, end);
            gstr3b(workbook, fyStart);
            tds(workbook, fyStart);
        }
    }

    // --- statements ---------------------------------------------------------------------------------------------

    private void about(Workbook workbook, LocalDate fyStart, LocalDate end, YearEnd yearEnd) {
        var w = new Writer(workbook.newWorksheet("About"));
        w.header("Item", "Value");
        var company = settings.billing().company();
        w.row("Company", company.legalName());
        w.row("CIN", company.cin());
        w.row("Financial year", FiscalYears.label(fyStart));
        w.row("Period", DAY_MONTH_YEAR.format(fyStart) + " to " + DAY_MONTH_YEAR.format(end));
        w.row("Generated at", GENERATED_AT.format(OffsetDateTime.now(SHOP_ZONE)));
        if (!yearEnd.closed()) {
            w.row("Draft: year not closed");
        }
    }

    private void trialBalance(Workbook workbook, LocalDate end) {
        var w = new Writer(workbook.newWorksheet("Trial balance"));
        w.header("Code", "Account", "Type", "Debit", "Credit");
        var balance = journal.trialBalance(end, true);
        for (var line : balance.lines()) {
            w.row(
                    line.code().trim(),
                    line.name(),
                    line.type().name(),
                    line.debitRupees(),
                    line.creditRupees());
        }
        w.row("Total", null, null, balance.totalDebitRupees(), balance.totalCreditRupees());
    }

    private void profitAndLoss(Workbook workbook, LocalDate from, LocalDate to) {
        var w = new Writer(workbook.newWorksheet("Profit and loss"));
        w.header("Section", "Line", "Account", "This year", "Previous year");
        var pnl = statements.profitAndLoss(from, to);
        statementRows(w, "Income", pnl.income());
        statementRows(w, "Expenses", pnl.expenses());
        w.row(
                null,
                "Total income",
                null,
                pnl.totalIncomeRupees(),
                Rupees.sum(pnl.income(), StatementLine::previousRupees));
        w.row(
                null,
                "Total expenses",
                null,
                pnl.totalExpensesRupees(),
                Rupees.sum(pnl.expenses(), StatementLine::previousRupees));
        w.row(null, "Profit", null, pnl.profitRupees(), pnl.previousProfitRupees());
    }

    private void balanceSheet(Workbook workbook, LocalDate end) {
        var w = new Writer(workbook.newWorksheet("Balance sheet"));
        w.header("Section", "Line", "Account", "This year", "Previous year");
        var sheet = statements.balanceSheet(end);
        statementRows(w, "Equity and liabilities", sheet.equityAndLiabilities());
        statementRows(w, "Assets", sheet.assets());
        w.row(
                null,
                "Total equity and liabilities",
                null,
                sheet.totalEquityAndLiabilitiesRupees(),
                sheet.previousTotalEquityAndLiabilitiesRupees());
        w.row(
                null,
                "Total assets",
                null,
                sheet.totalAssetsRupees(),
                sheet.previousTotalAssetsRupees());
    }

    private static void statementRows(Writer w, String section, List<StatementLine> lines) {
        for (var line : lines) {
            w.row(section, line.label(), null, line.amountRupees(), line.previousRupees());
            for (var account : line.accounts()) {
                w.row(
                        section,
                        line.label(),
                        accountLabel(account),
                        account.amountRupees(),
                        account.previousRupees());
            }
        }
    }

    private static String accountLabel(StatementAccount account) {
        return account.code().isBlank() ? account.name() : account.code() + " " + account.name();
    }

    // --- books --------------------------------------------------------------------------------------------------

    private void dayBook(Workbook workbook, LocalDate from, LocalDate to) {
        var w = new Writer(workbook.newWorksheet("Day book"));
        w.header("Date", "Number", "Narration", "Account code", "Account", "Party", "Debit", "Credit");
        var rows = jdbc.sql("""
                        SELECT e.entry_date AS date, e.number, e.narration, a.code AS account_code,
                               a.name AS account_name, p.name AS party_name, l.debit_rupees, l.credit_rupees
                        FROM journal_line l
                        JOIN journal_entry e ON e.id = l.entry_id
                        JOIN account a ON a.id = l.account_id
                        LEFT JOIN party p ON p.id = l.party_id
                        WHERE e.entry_date BETWEEN :from AND :to
                        ORDER BY e.entry_date, e.number, l.position
                        """)
                .param("from", from)
                .param("to", to)
                .query(DayBookRow.class)
                .list();
        for (var row : rows) {
            w.row(
                    row.date(),
                    row.number(),
                    row.narration(),
                    row.accountCode().trim(),
                    row.accountName(),
                    row.partyName(),
                    row.debitRupees(),
                    row.creditRupees());
        }
    }

    private void ledgers(Workbook workbook, LocalDate from, LocalDate to) {
        var w = new Writer(workbook.newWorksheet("Ledgers"));
        w.header("Account", "Date", "Number", "Narration", "Debit", "Credit", "Balance");
        var accounts = jdbc.sql("""
                        SELECT a.id, a.code, a.name, COALESCE(o.opening_rupees, 0) AS opening_rupees
                        FROM account a
                        LEFT JOIN (SELECT l.account_id, SUM(l.debit_rupees - l.credit_rupees) AS opening_rupees
                                   FROM journal_line l
                                   JOIN journal_entry e ON e.id = l.entry_id
                                   WHERE e.entry_date < :from
                                   GROUP BY l.account_id) o ON o.account_id = a.id
                        WHERE COALESCE(o.opening_rupees, 0) <> 0
                           OR EXISTS (SELECT 1 FROM journal_line l
                                      JOIN journal_entry e ON e.id = l.entry_id
                                      WHERE l.account_id = a.id AND e.entry_date BETWEEN :from AND :to)
                        ORDER BY a.code
                        """)
                .param("from", from)
                .param("to", to)
                .query(AccountLedgerRow.class)
                .list();
        for (var account : accounts) {
            var label = account.code().trim() + " " + account.name();
            var running = Rupees.of(account.openingRupees());
            w.row(label, null, null, "Opening", null, null, running);
            var lines = jdbc.sql("""
                            SELECT e.entry_date AS date, e.number, e.narration, l.debit_rupees, l.credit_rupees
                            FROM journal_line l
                            JOIN journal_entry e ON e.id = l.entry_id
                            WHERE l.account_id = :account AND e.entry_date BETWEEN :from AND :to
                            ORDER BY e.entry_date, e.id, l.position
                            """)
                    .param("account", account.id())
                    .param("from", from)
                    .param("to", to)
                    .query(LedgerLineRow.class)
                    .list();
            for (var line : lines) {
                running = Rupees.of(running.add(line.debitRupees()).subtract(line.creditRupees()));
                w.row(
                        label,
                        line.date(),
                        line.number(),
                        line.narration(),
                        line.debitRupees(),
                        line.creditRupees(),
                        running);
            }
            w.row(label, null, null, "Closing", null, null, running);
        }
    }

    private void debtors(Workbook workbook, LocalDate end) {
        var w = new Writer(workbook.newWorksheet("Debtors"));
        w.header("Party", "Balance");
        var rows = jdbc.sql("""
                        SELECT p.name, SUM(l.debit_rupees - l.credit_rupees) AS balance_rupees
                        FROM journal_line l
                        JOIN journal_entry e ON e.id = l.entry_id
                        JOIN account a ON a.id = l.account_id
                        JOIN party p ON p.id = l.party_id
                        WHERE a.system_key = 'DEBTORS' AND e.entry_date <= :end
                        GROUP BY p.id, p.name
                        HAVING SUM(l.debit_rupees - l.credit_rupees) <> 0
                        ORDER BY p.name
                        """)
                .param("end", end)
                .query(DebtorRow.class)
                .list();
        for (var row : rows) {
            w.row(row.name(), row.balanceRupees());
        }
    }

    private void creditors(Workbook workbook, LocalDate end) {
        var w = new Writer(workbook.newWorksheet("Creditors"));
        w.header("Party", "Balance", "Oldest bill");
        var rows = jdbc.sql("""
                        SELECT p.name, SUM(l.credit_rupees - l.debit_rupees) AS balance_rupees,
                               (SELECT MIN(b.bill_date) FROM supplier_bill b
                                WHERE b.party_id = p.id AND b.status = 'OPEN' AND b.bill_date <= :end)
                                   AS oldest_bill
                        FROM journal_line l
                        JOIN journal_entry e ON e.id = l.entry_id
                        JOIN account a ON a.id = l.account_id
                        JOIN party p ON p.id = l.party_id
                        WHERE a.system_key = 'CREDITORS' AND e.entry_date <= :end
                        GROUP BY p.id, p.name
                        HAVING SUM(l.credit_rupees - l.debit_rupees) <> 0
                        ORDER BY p.name
                        """)
                .param("end", end)
                .query(CreditorRow.class)
                .list();
        for (var row : rows) {
            w.row(row.name(), row.balanceRupees(), row.oldestBill());
        }
    }

    // --- registers ----------------------------------------------------------------------------------------------

    private void salesRegister(Workbook workbook, LocalDate from, LocalDate to) {
        var w = new Writer(workbook.newWorksheet("Sales register"));
        w.header("Type", "Number", "Date", "Party", "Place of supply", "Taxable", "IGST", "CGST", "SGST", "Total");
        var rows = jdbc.sql("""
                        SELECT * FROM (
                            SELECT 'Invoice' AS type, i.number, i.issued_on AS date, i.buyer_name AS party,
                                   COALESCE(i.place_of_supply, i.seller_state_code) AS place_of_supply,
                                   i.taxable_rupees, i.igst_rupees, i.cgst_rupees, i.sgst_rupees, i.total_rupees
                            FROM invoice i
                            WHERE i.issued_on BETWEEN :from AND :to AND i.cancelled_at IS NULL
                            UNION ALL
                            SELECT 'Credit note', n.number, n.issued_on, n.buyer_name,
                                   COALESCE(n.place_of_supply, n.seller_state_code),
                                   -n.taxable_rupees, -n.igst_rupees, -n.cgst_rupees, -n.sgst_rupees, -n.total_rupees
                            FROM credit_note n
                            WHERE n.issued_on BETWEEN :from AND :to
                        ) register
                        ORDER BY date, number
                        """)
                .param("from", from)
                .param("to", to)
                .query(SalesRow.class)
                .list();
        for (var row : rows) {
            w.row(
                    row.type(),
                    row.number(),
                    row.date(),
                    row.party(),
                    row.placeOfSupply(),
                    row.taxableRupees(),
                    row.igstRupees(),
                    row.cgstRupees(),
                    row.sgstRupees(),
                    row.totalRupees());
        }
    }

    private void purchaseRegister(Workbook workbook, LocalDate from, LocalDate to) {
        var w = new Writer(workbook.newWorksheet("Purchase register"));
        w.header(
                "Supplier",
                "GSTIN",
                "Bill no",
                "Bill date",
                "Taxable",
                "IGST",
                "CGST",
                "SGST",
                "TDS",
                "Total",
                "Status");
        var rows = jdbc.sql("""
                        SELECT p.name AS supplier, p.gstin, b.bill_no, b.bill_date,
                               COALESCE(SUM(l.taxable_rupees), 0) AS taxable_rupees,
                               COALESCE(SUM(l.igst_rupees), 0) AS igst_rupees,
                               COALESCE(SUM(l.cgst_rupees), 0) AS cgst_rupees,
                               COALESCE(SUM(l.sgst_rupees), 0) AS sgst_rupees,
                               b.tds_rupees, b.total_rupees, b.status
                        FROM supplier_bill b
                        JOIN party p ON p.id = b.party_id
                        LEFT JOIN supplier_bill_line l ON l.bill_id = b.id
                        WHERE b.bill_date BETWEEN :from AND :to
                        GROUP BY b.id, p.name, p.gstin, b.bill_no, b.bill_date, b.tds_rupees, b.total_rupees, b.status
                        ORDER BY b.bill_date, b.bill_no
                        """)
                .param("from", from)
                .param("to", to)
                .query(PurchaseRow.class)
                .list();
        for (var row : rows) {
            w.row(
                    row.supplier(),
                    row.gstin(),
                    row.billNo(),
                    row.billDate(),
                    row.taxableRupees(),
                    row.igstRupees(),
                    row.cgstRupees(),
                    row.sgstRupees(),
                    row.tdsRupees(),
                    row.totalRupees(),
                    row.status());
        }
    }

    // --- year end -----------------------------------------------------------------------------------------------

    private void fixedAssets(Workbook workbook, LocalDate from, LocalDate to) {
        var w = new Writer(workbook.newWorksheet("Fixed assets"));
        w.header(
                "Asset",
                "Class",
                "Cost",
                "Put to use",
                "Opening acc. dep.",
                "Depreciation this year",
                "Book value",
                "Disposed on");
        Map<Long, BigDecimal> thisYear = new HashMap<>();
        var depreciation = jdbc.sql("""
                        SELECT asset_id, SUM(amount_rupees) AS amount_rupees
                        FROM asset_depreciation
                        WHERE month BETWEEN :from AND :to
                        GROUP BY asset_id
                        """)
                .param("from", from)
                .param("to", to)
                .query(DepreciationRow.class)
                .list();
        for (var row : depreciation) {
            thisYear.put(row.assetId(), row.amountRupees());
        }
        for (var asset : assets.register()) {
            w.row(
                    asset.name(),
                    asset.className(),
                    asset.costRupees(),
                    asset.putToUseOn(),
                    asset.openingAccdepRupees(),
                    Rupees.orZero(thisYear.get(asset.id())),
                    asset.bookValueRupees(),
                    asset.disposedOn());
        }
    }

    private void closingStock(Workbook workbook, LocalDate fyStart, YearEnd yearEnd) {
        var w = new Writer(workbook.newWorksheet("Closing stock"));
        w.header("Item", "Unit", "Quantity", "Rate", "Value");
        for (var line : yearEnds.savedStock(fyStart)) {
            w.text(0, line.name());
            w.text(1, line.unit());
            w.number(2, line.quantity());
            w.money(3, line.rateRupees());
            w.money(4, line.valueRupees());
            w.next();
        }
        w.row("Finished goods", null, null, null, yearEnd.finishedGoodsRupees());
        w.row("Total", null, null, null, yearEnd.closingStockRupees());
    }

    private void bankReconciliation(Workbook workbook, LocalDate from, LocalDate to) {
        var w = new Writer(workbook.newWorksheet("Bank reconciliation"));
        w.header(
                "Statement",
                "Period to",
                "Statement closing",
                "Ledger balance",
                "Difference",
                "Unmatched lines",
                "Ledger-only movements");
        var ids = jdbc.sql("""
                        SELECT id FROM bank_statement
                        WHERE period_to BETWEEN :from AND :to
                        ORDER BY period_to, id
                        """)
                .param("from", from)
                .param("to", to)
                .query(Long.class)
                .list();
        for (long id : ids) {
            var report = reconciliations.report(id);
            w.row(
                    report.statement().fileName(),
                    report.statement().periodTo(),
                    report.statementClosingRupees(),
                    report.ledgerBalanceRupees(),
                    report.differenceRupees(),
                    report.unmatchedLines().size(),
                    report.ledgerOnly().size());
        }
    }

    // --- returns ------------------------------------------------------------------------------------------------

    private void gstr3b(Workbook workbook, LocalDate fyStart) {
        var w = new Writer(workbook.newWorksheet("GSTR-3B"));
        w.header(
                "Month",
                "3.1 taxable",
                "3.1 IGST",
                "3.1 CGST",
                "3.1 SGST",
                "ITC IGST",
                "ITC CGST",
                "ITC SGST",
                "Cash IGST",
                "Cash CGST",
                "Cash SGST");
        for (var month : FiscalYears.months(fyStart)) {
            var g = gstr3b.month(month);
            var outward = g.outward().stream()
                    .filter(row -> "3.1(a)".equals(row.section()))
                    .findFirst()
                    .orElse(null);
            var itc = g.itc();
            w.row(
                    month.toString(),
                    outward == null ? Rupees.ZERO : outward.taxableRupees(),
                    outward == null ? Rupees.ZERO : outward.igstRupees(),
                    outward == null ? Rupees.ZERO : outward.cgstRupees(),
                    outward == null ? Rupees.ZERO : outward.sgstRupees(),
                    itc.igstRupees(),
                    itc.cgstRupees(),
                    itc.sgstRupees(),
                    cash(g.setOff(), "IGST"),
                    cash(g.setOff(), "CGST"),
                    cash(g.setOff(), "SGST"));
        }
    }

    private static BigDecimal cash(List<Gstr3bSetOff> setOff, String head) {
        return setOff.stream()
                .filter(row -> head.equals(row.head()))
                .map(Gstr3bSetOff::cashRupees)
                .findFirst()
                .orElse(Rupees.ZERO);
    }

    private void tds(Workbook workbook, LocalDate fyStart) {
        var w = new Writer(workbook.newWorksheet("TDS"));
        w.header(
                "Quarter",
                "Section",
                "Party",
                "PAN",
                "Bill no",
                "Credited on",
                "Amount",
                "Rate %",
                "TDS",
                "Challan no");
        var quarters = new ArrayList<TdsQuarter>();
        for (int quarter = 1; quarter <= 4; quarter++) {
            quarters.add(tdsReturns.quarter(fyStart, quarter));
        }
        for (var quarter : quarters) {
            for (var row : quarter.deductees()) {
                w.text(0, "Q" + quarter.quarter());
                w.text(1, row.sectionCode());
                w.text(2, row.partyName());
                w.text(3, row.pan());
                w.text(4, row.billNo());
                w.date(5, row.creditedOn());
                w.money(6, row.amountRupees());
                w.number(7, row.ratePercent());
                w.money(8, row.tdsRupees());
                w.text(9, row.challanNo());
                w.next();
            }
        }
        w.next();
        w.header("Quarter", "Section", "Period month", "Amount", "Interest", "Challan no", "BSR code", "Paid on");
        for (var quarter : quarters) {
            for (var challan : quarter.challans()) {
                w.row(
                        "Q" + quarter.quarter(),
                        challan.sectionCode(),
                        challan.periodMonth(),
                        challan.amountRupees(),
                        challan.interestRupees(),
                        challan.challanNo(),
                        challan.bsrCode(),
                        challan.paidOn());
            }
        }
    }

    // --- rows and cells -----------------------------------------------------------------------------------------

    record DayBookRow(
            LocalDate date,
            String number,
            String narration,
            String accountCode,
            String accountName,
            @Nullable String partyName,
            BigDecimal debitRupees,
            BigDecimal creditRupees) {}

    record AccountLedgerRow(long id, String code, String name, BigDecimal openingRupees) {}

    record LedgerLineRow(
            LocalDate date,
            String number,
            String narration,
            BigDecimal debitRupees,
            BigDecimal creditRupees) {}

    record DebtorRow(String name, BigDecimal balanceRupees) {}

    record CreditorRow(String name, BigDecimal balanceRupees, @Nullable LocalDate oldestBill) {}

    record SalesRow(
            String type,
            String number,
            LocalDate date,
            String party,
            @Nullable String placeOfSupply,
            BigDecimal taxableRupees,
            BigDecimal igstRupees,
            BigDecimal cgstRupees,
            BigDecimal sgstRupees,
            BigDecimal totalRupees) {}

    record PurchaseRow(
            String supplier,
            @Nullable String gstin,
            String billNo,
            LocalDate billDate,
            BigDecimal taxableRupees,
            BigDecimal igstRupees,
            BigDecimal cgstRupees,
            BigDecimal sgstRupees,
            BigDecimal tdsRupees,
            BigDecimal totalRupees,
            String status) {}

    record DepreciationRow(long assetId, BigDecimal amountRupees) {}

    /** A sheet with a row cursor and the cell formats the pack uses: money to two places, dates day-month-year. */
    private static final class Writer {

        private static final String MONEY_FORMAT = "#,##0.00";
        private static final String DATE_FORMAT = "dd-mm-yyyy";

        private final Worksheet sheet;
        private int row;

        Writer(Worksheet sheet) {
            this.sheet = sheet;
        }

        void header(String... titles) {
            for (int column = 0; column < titles.length; column++) {
                sheet.value(row, column, titles[column]);
                sheet.style(row, column).bold().set();
            }
            row++;
        }

        void row(@Nullable Object... cells) {
            for (int column = 0; column < cells.length; column++) {
                var cell = cells[column];
                if (cell instanceof String text) {
                    text(column, text);
                } else if (cell instanceof LocalDate date) {
                    date(column, date);
                } else if (cell instanceof BigDecimal amount) {
                    money(column, amount);
                } else if (cell instanceof Number number) {
                    sheet.value(row, column, number);
                } else if (cell != null) {
                    sheet.value(row, column, cell.toString());
                }
            }
            row++;
        }

        void text(int column, @Nullable String value) {
            if (value != null) {
                sheet.value(row, column, value);
            }
        }

        void money(int column, @Nullable BigDecimal amount) {
            if (amount != null) {
                sheet.value(row, column, amount);
                sheet.style(row, column).format(MONEY_FORMAT).set();
            }
        }

        void number(int column, @Nullable BigDecimal amount) {
            if (amount != null) {
                sheet.value(row, column, amount);
            }
        }

        void date(int column, @Nullable LocalDate date) {
            if (date != null) {
                sheet.value(row, column, date);
                sheet.style(row, column).format(DATE_FORMAT).set();
            }
        }

        void next() {
            row++;
        }
    }
}
