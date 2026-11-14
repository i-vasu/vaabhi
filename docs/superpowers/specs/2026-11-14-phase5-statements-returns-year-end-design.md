# Books Phase 5: statements, returns, year-end close, auditor pack

Date: 2026-11-14. Branch `feat/id-engine-parties` (PR #48, all books phases in one PR). Plan: "Vaabhi books without
accounting software", Phase 5.

## Goal

Vaabhi shows the business's profit and loss and balance sheet under Schedule III headings for any period, gives the
monthly GSTR-3B figures and the quarterly 26Q TDS data, closes a financial year (closing stock, profit into retained
earnings, months locked), and hands the CA one Excel workbook with everything needed for the audit and the filed
statements.

## Decisions

| Topic | Decision |
| --- | --- |
| Statements | Management statements grouped by Schedule III line, with the accounts listed under each line and a previous-period comparative. The CA prepares the filed statements, notes and AOC-4 from the auditor pack. |
| Grouping | A `schedule_line` on every account. Seeded for system accounts, picked on the account form, defaulted by type. Computed live from the journal; no snapshots. |
| Closing stock | Pre-filled per material (quantity on hand at 31 March × the item's `cost_paise`), every line editable, plus one figure for finished and in-progress garments. Posted as one adjustment to the Stock account; no 1 April reversal. |
| Year-end close | Stock adjustment, then a closing entry moving every income and expense balance to Retained earnings, then lock all 12 months. The latest closed year can be reopened. |
| GSTR-3B | Monthly 3.1, 3.2, 4 and 6.1 figures with CSV. Outward from the GSTR-1 data (so both returns agree), ITC from the input GST report. No portal JSON. |
| TDS returns | Quarterly 26Q deductee rows and challans as CSV for the CA's return utility. No FVU file, no 24Q. |
| Auditor pack | One `.xlsx` per financial year (fastexcel), one sheet per report. Builds for an open year too, marked draft. |
| Module | Everything in `accounts`. `accounts` gains `money` as an allowed dependency to reuse `Gstr1Service` (money does not depend on accounts, so no cycle). Other tables (stock, bills, invoices) are read by SQL as the posting rules already do. |

Out of scope: filed Schedule III notes and AOC-4/ITR forms, GSTR-2B matching, GSTR-9, 24Q (salary TDS), Form 16A,
income-tax depreciation blocks, reclassifying overdrawn bank or debit-balance creditors to the other side of the
balance sheet, finished-goods costing from product material/work rows.

## Data (migration V37)

All money columns `NUMERIC(12,2)` mapped to `Rupees`. The V32 audit trigger is attached to every new table.

### Schedule line on accounts

`account.schedule_line VARCHAR(30) NOT NULL` with a CHECK over the `ScheduleLine` enum values. Added with a default
by type, then system accounts set explicitly:

| Line | Side | Seeded accounts |
| --- | --- | --- |
| `REVENUE_FROM_OPERATIONS` | P&L income | 4000 Sales, 4010 Sales returns, 4100 Alteration income |
| `OTHER_INCOME` | P&L income | 4900 Gain on asset disposal; default for new INCOME |
| `COST_OF_MATERIALS` | P&L expense | 5020 Materials |
| `PURCHASES_STOCK_IN_TRADE` | P&L expense | 5100 Purchases |
| `CHANGE_IN_INVENTORIES` | P&L expense | 5150 Change in inventories (new) |
| `EMPLOYEE_BENEFITS` | P&L expense | 5010 Salaries |
| `FINANCE_COSTS` | P&L expense | none seeded |
| `DEPRECIATION` | P&L expense | 5200 Depreciation |
| `OTHER_EXPENSES` | P&L expense | 5000–5095 except the above, 5210 Loss on asset disposal; default for new EXPENSE |
| `SHARE_CAPITAL` | Equity | 3000 Share capital |
| `RESERVES_SURPLUS` | Equity | 3100 Opening balance equity, 3200 Retained earnings; default for new EQUITY |
| `LONG_TERM_BORROWINGS` | Liability | none seeded |
| `SHORT_TERM_BORROWINGS` | Liability | 2500 Owner loan |
| `TRADE_PAYABLES` | Liability | 2000 Creditors |
| `OTHER_CURRENT_LIABILITIES` | Liability | 2100, 2200, 2210, 2220, 2300, 2400; default for new LIABILITY |
| `PPE` | Asset | 1500–1531 and every account `AssetService` creates for a new asset class (asset and acc. dep.) |
| `INVENTORIES` | Asset | 1300 Stock |
| `TRADE_RECEIVABLES` | Asset | 1100 Debtors |
| `CASH_AND_BANK` | Asset | 1000 Cash in hand, 1010 Bank |
| `OTHER_CURRENT_ASSETS` | Asset | 1020, 1030, 1200; default for new ASSET |

`ScheduleLine` carries its side (`INCOME`, `EXPENSE`, `EQUITY`, `LIABILITY`, `ASSET`), a display label, and an
order. An account's line must match its type's side (`ChartService` rejects a mismatch with 400).

### New account

`5150 Change in inventories` `CHANGE_IN_INVENTORIES` (EXPENSE, line `CHANGE_IN_INVENTORIES`).

### Journal sources

`journal_entry.source_type` CHECK gains `YEAR_END`; `source_step` CHECK gains `STOCK` and `CLOSE`.
`JournalService.REVERSIBLE` gains `YEAR_END` (used only by reopen; the generic reverse endpoint refuses `YEAR_END`
with 409 "Reopen the year instead").

### Tables

```sql
-- One row per financial year that has had its stock list saved. fy_start is 1 April.
CREATE TABLE fiscal_year (
    fy_start               DATE PRIMARY KEY CHECK (EXTRACT(MONTH FROM fy_start) = 4 AND EXTRACT(DAY FROM fy_start) = 1),
    finished_goods_rupees  NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (finished_goods_rupees >= 0),
    stock_note             VARCHAR(300),
    stock_saved_by         VARCHAR(120),
    stock_saved_at         TIMESTAMPTZ,
    closed                 BOOLEAN NOT NULL DEFAULT FALSE
);

-- Material lines of the closing stock list.
CREATE TABLE closing_stock_line (
    fy_start       DATE NOT NULL REFERENCES fiscal_year (fy_start),
    stock_item_id  BIGINT NOT NULL REFERENCES stock_item (id),
    quantity       NUMERIC(12, 2) NOT NULL CHECK (quantity >= 0),
    rate_rupees    NUMERIC(12, 2) NOT NULL CHECK (rate_rupees >= 0),
    value_rupees   NUMERIC(12, 2) NOT NULL CHECK (value_rupees >= 0),
    PRIMARY KEY (fy_start, stock_item_id)
);

-- Every close of a year. Its id is the journal source_id, so closing again after a reopen gets fresh entries.
CREATE TABLE year_close_run (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fy_start          DATE NOT NULL REFERENCES fiscal_year (fy_start),
    stock_entry_id    BIGINT REFERENCES journal_entry (id),
    closing_entry_id  BIGINT REFERENCES journal_entry (id),
    closed_by         VARCHAR(120),
    closed_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    reopened_by       VARCHAR(120),
    reopened_at       TIMESTAMPTZ
);
CREATE UNIQUE INDEX year_close_run_open ON year_close_run (fy_start) WHERE reopened_at IS NULL;
```

Guard: `closing_stock_line` and `fiscal_year.finished_goods_rupees` refuse changes while `fiscal_year.closed`
(trigger, like V33's protect triggers).

## Units (`accounts`)

### `FiscalYears` (small helper)

`FiscalYears.of(LocalDate)` → `fy_start`; `label(fyStart)` → `"2026-27"`; `parse("2026-27")`; `end(fyStart)` →
31 March. The first year of the books is the one containing `BooksSettings.startDate`. API paths take the label.

### `StatementService`

- `profitAndLoss(LocalDate from, LocalDate to)` → `ProfitAndLoss`: income lines then expense lines, each
  `StatementLine(line, label, amount, previousAmount, List<StatementAccount>)`, plus totals and `profit`.
  Amounts are movements in the range, sign-normalised (income credit-positive, expenses debit-positive). Excludes
  journal entries with `source_type = 'YEAR_END' AND source_step = 'CLOSE'` and reversals of them (`reverses_id`
  pointing at a CLOSE entry). Previous period = the same length immediately before `from` (for a full FY, the
  previous FY).
- `balanceSheet(LocalDate asOf)` → `BalanceSheet`: equity and liability lines, asset lines, each with accounts and a
  previous comparative (as at the same date a year earlier), totals, and `balanced`. Balances include every entry up
  to `asOf`. Equity/liabilities credit-positive, assets debit-positive (a contra account shows as negative within
  its line). `RESERVES_SURPLUS` gets one extra synthetic account row "Profit not yet closed" = the net balance of all
  INCOME and EXPENSE accounts as at `asOf` (zero once every year up to `asOf` is closed, so nothing is counted twice).
- Both are computed with one grouped SQL over `journal_line` joined to `journal_entry` and `account`, like
  `JournalService.trialBalance`.

### `Gstr3bService`

`month(YearMonth)` → `Gstr3b`:
- 3.1(a) outward taxable: sum of `Gstr1Service.month()` B2B + B2CL + B2CS taxable/IGST/CGST/SGST, minus CDNR + CDNUR.
- 3.1(c) nil/exempt: invoices with zero tax (taxable value only), if the GSTR-1 rows expose them; otherwise zero and a
  gap noted.
- 3.2 inter-state supplies to unregistered persons: B2CL + B2CS rows with IGST > 0, grouped by place of supply,
  minus CDNUR inter-state.
- 4(A)(5) ITC all other: `InputGstReport.months(month)` CGST/SGST/IGST. 4(B) reversals: zero (none tracked).
- 6.1 tax payable: per head, output minus ITC (IGST ITC set off IGST, then CGST, then SGST; CGST ITC against CGST then
  IGST; SGST ITC against SGST then IGST), and the cash to pay per head. Never negative; leftover ITC is shown as
  carried forward.
- `gstOn` flag passed through; with GST off the page still shows the figures with a "not registered" note.
- `csv(YearMonth)`: one CSV with section, description, taxable, IGST, CGST, SGST.

### `TdsReturnService`

`quarter(fyStart, int q)` → `TdsQuarter`:
- Deductee rows: every `supplier_bill` with `tds_rupees > 0`, `status = 'OPEN'`, and a posted journal entry, with
  `bill_date` in the quarter. Columns: section code, party name, PAN (blank flagged "PAN missing: higher rate"),
  bill number, date of credit (bill date), amount credited (`taxable_rupees`), TDS rate (`tds_rupees / taxable`),
  TDS deducted, challan matched (the first challan with the same section and `period_month` = bill month, if any).
- Challan rows: `tds_challan` with `period_month` in the quarter.
- Totals: deducted vs deposited per section, and the difference.
- `csv(fyStart, q, "deductees" | "challans")`.

### `YearEndService`

- `get(fyStart)` → `YearEnd`: the stock list (saved lines, or the pre-fill if nothing saved), finished-goods figure,
  totals, the Stock account balance at 31 March before any stock adjustment, the adjustment it would post, the
  checklist (below) with pass/fail and a message per item, `closed`, and the close runs.
- Pre-fill: every active or previously-moved `stock_item` with on-hand quantity at 31 March > 0 (sum of
  `stock_movement.quantity` with `moved_at` on or before 31 March 23:59:59 Asia/Kolkata), rate = `cost_paise / 100`,
  value = quantity × rate rounded to paise half-up.
- `saveStock(fyStart, YearEndStockForm)`: replaces the lines and the finished-goods figure, stamps saved by/at.
  409 if the year is closed.
- Checklist: books start date set and on or before 31 March; previous FY closed (when it is on or after the books'
  first FY); no open posting problems dated in the year; no unposted documents in the year (the sweeper's pending
  count for the date range); trial balance as at 31 March balanced; stock list saved.
- `close(fyStart, actor)`, one transaction:
  1. Every checklist item must pass, else 409 with the first failing item's message.
  2. Insert `year_close_run`.
  3. Unlock March if locked (others are left as they are).
  4. Stock adjustment: `closing total − Stock balance at 31 March`. If non-zero, post `YEAR_END/<run id>/STOCK`
     dated 31 March: positive → Dr Stock / Cr Change in inventories; negative → the reverse. Narration "Closing stock
     2026-27".
  5. Closing entry: every INCOME and EXPENSE account's balance at 31 March (after step 4) is zeroed in one entry
     `YEAR_END/<run id>/CLOSE`, with the net to Retained earnings. Skipped if all balances are zero.
  6. Lock all 12 months (`PeriodService.lock`, idempotent).
  7. `fiscal_year.closed = true`; store entry ids on the run.
- `reopen(fyStart, actor)`: only the latest closed year (409 otherwise, "Reopen 2026-27 first"). Unlocks March,
  reverses the CLOSE then the STOCK entry with `JournalService.reverseOn(id, 31 March)`, stamps `reopened_by/at`,
  sets `closed = false`. Other months stay locked.

### `AuditorPackService`

`write(fyStart, OutputStream)` builds one workbook. Sheet 1 "About": company name and CIN (from the company card),
FY, generated at, "Draft: year not closed" when open. Then:

| Sheet | Source |
| --- | --- |
| Trial balance | As at 31 March, excluding CLOSE entries (so income and expenses show) |
| Profit and loss | `StatementService.profitAndLoss` for the FY, lines and accounts, with previous year |
| Balance sheet | `StatementService.balanceSheet(31 March)`, with previous year |
| Day book | Every journal line in the FY: date, number, narration, account code/name, party, debit, credit |
| Ledgers | One sheet, all accounts: opening, each line, running balance, closing (accounts with no activity and zero balance skipped) |
| Debtors | Party balances on Debtors at 31 March |
| Creditors | Party balances on Creditors at 31 March, with oldest unpaid bill date |
| Sales register | Invoices and credit notes in the FY: number, date, party, place of supply, taxable, IGST, CGST, SGST, total |
| Purchase register | Supplier bills in the FY: supplier, GSTIN, bill no/date, taxable, IGST, CGST, SGST, TDS, total, status |
| Fixed assets | Per asset: class, cost, put-to-use, opening acc. dep., depreciation for the FY, closing WDV, disposed |
| Closing stock | The saved stock list and the finished-goods figure |
| Bank reconciliation | `BankReconciliationService` as at 31 March |
| GSTR-3B | One row per month: 3.1 and 4 totals and 6.1 cash payable |
| TDS | Deductee rows and challans for all four quarters |

Rupee cells are numbers with two decimals; dates are date cells. Uses the services above rather than repeating
queries; the registers and debtors/creditors lists are new SQL in this service.

## API (`/api/v1/admin/accounts`, same auth as the rest of accounts)

| Method | Path | Returns |
| --- | --- | --- |
| GET | `/statements/profit-and-loss?from&to` | `ProfitAndLoss` |
| GET | `/statements/balance-sheet?asOf` | `BalanceSheet` |
| GET | `/gstr3b?month=2026-05` | `Gstr3b` |
| GET | `/gstr3b.csv?month=2026-05` | CSV |
| GET | `/tds/returns?fy=2026-27&quarter=1` | `TdsQuarter` |
| GET | `/tds/returns.csv?fy&quarter&part=deductees\|challans` | CSV |
| GET | `/year-end/{fy}` | `YearEnd` |
| PUT | `/year-end/{fy}/stock` | `YearEnd` |
| POST | `/year-end/{fy}/close` | `YearEnd` |
| POST | `/year-end/{fy}/reopen` | `YearEnd` |
| GET | `/year-end/{fy}/auditor-pack.xlsx` | workbook, `Content-Disposition: attachment; filename=vaabhi-books-2026-27.xlsx` |

`AccountForm` and `Account` gain `scheduleLine` (form: optional, defaulted by type on create; kept on update when
absent). API record names are unique (springdoc collides on simple names): `StatementLine`, `StatementAccount`,
`Gstr3bRow`, `TdsDeducteeRow`, `TdsQuarterChallan`, `ClosingStockLine`, `YearEndCheck`, `YearCloseRunView`.

Regenerate the api-client after the backend tasks (`pnpm generate:api`).

## Admin UI (`frontend/admin`)

- **Statements** (`/books/statements`): tabs P&L and Balance sheet. P&L: FY picker (default current FY) or custom
  from/to; balance sheet: as-at date (default today). Lines expand to their accounts; account rows link to the
  ledger. Comparative column. "Download Excel" uses the auditor pack link for the FY.
- **GSTR-3B** (`/books/gstr3b`): month picker, the four tables, CSV download. Linked from the GSTR-1 page.
- **TDS returns**: a "Returns" tab on the existing TDS page: FY + quarter pickers, deductee table (missing PAN
  highlighted), challan table, deducted vs deposited per section, two CSV buttons.
- **Year end** (`/books/year-end`): FY picker. Checklist with pass/fail. Stock table (item, unit, quantity, rate,
  value; editable quantity and rate; remove row) + finished-goods input + Save. Summary: closing stock total, Stock
  balance, adjustment, estimated profit. "Close year" (confirm sheet) and, for the latest closed year, "Reopen".
  "Download auditor pack" always available.
- **Chart of accounts**: a Schedule III line select on the account form, filtered to the type's side; a column in
  the list.
- Books nav gains Statements, GSTR-3B and Year end.

## Errors

- Account line on the wrong side: 400 "<line label> is not a <type> line".
- Bad FY label or quarter: 400.
- Save stock on a closed year: 409 "2026-27 is closed. Reopen it to change the stock list".
- Close: 409 with the failing checklist message, e.g. "Close 2025-26 first", "3 documents in 2026-27 aren't posted
  yet. Run Post now", "Save the closing stock list first".
- Close an already-closed year: 409. Reopen a year that isn't the latest closed one: 409.
- April of the next year locked does not block a close (nothing is posted there).

## Testing (added alongside, not run)

- `StatementServiceIT`: P&L excludes CLOSE entries; balance sheet balances before and after a close; "profit not
  yet closed" goes to zero after close.
- `YearEndServiceIT`: pre-fill quantity at 31 March; close posts stock adjustment and closing entry and locks months;
  reopen reverses both; close again after reopen gets new entries.
- `Gstr3bServiceTest`: set-off order and carry forward.
- `ScheduleLine` side check in `ChartService` unit test.

## Gaps (to add to `docs/BOOKS_GAPS.md` when the phase is done)

- Overdrawn bank and debit-balance creditors are not reclassified across the balance sheet.
- 3.1(c) nil/exempt and 3.1(b) zero-rated depend on what GSTR-1 rows expose; 4(B) reversals and 4(D) ineligible ITC
  are always zero.
- 26Q export is a CSV for the CA, not an FVU file; no Form 16A.
- Finished-goods closing stock is one typed figure.
- Reopen unlocks only March.
- Phase 5 owner checks by hand: this spec.
