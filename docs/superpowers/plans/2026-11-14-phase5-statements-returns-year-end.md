# Books Phase 5: Statements, Returns, Year-End, Auditor Pack Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Schedule III P&L and balance sheet for any period, monthly GSTR-3B, quarterly 26Q data, a year-end close
(closing stock, closing entry, months locked, reopen) and a one-workbook auditor pack.

**Architecture:** Migration V37 adds `account.schedule_line`, `5150 Change in inventories`, the `YEAR_END` journal
source and the year-end tables. Everything new lives in `accounts`: read-only report services over `journal_line`
(as `JournalService.trialBalance` does), `Gstr3bService` reusing `money.Gstr1Service` (new allowed dependency),
`YearEndService` posting through `JournalService.post`, and `AuditorPackService` writing xlsx with fastexcel. New
controllers keep tasks off each other's files.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Modulith, JdbcClient, Flyway, Postgres, fastexcel 0.18.4 writer;
Next.js 16 static-export admin, Tailwind 4.

**Spec:** `docs/superpowers/specs/2026-11-14-phase5-statements-returns-year-end-design.md`

**Deviations from spec (wiring only):**
- Close checklist "no unposted documents": `close()` first calls `JournalSweeper.sweepNow()`, then requires
  `PostingProblemService.count() == 0` (problems carry no date, so the check is not limited to the year). `get()`
  shows the same item from `count()` without sweeping.
- The bank reconciliation sheet lists `BankReconciliationService.report(id)` for every `bank_statement` whose
  `period_to` falls in the FY (reports are per statement, not "as at a date").

## Global Constraints

- Migration `V37__statements_year_end.sql`; never edit V1–V36.
- Money: `NUMERIC(12,2)` / `BigDecimal` via `Rupees`; HALF_UP to paise; never double.
- New account: `5150 Change in inventories EXPENSE CHANGE_IN_INVENTORIES`, line `CHANGE_IN_INVENTORIES`.
- `source_type` gains `YEAR_END`; `source_step` gains `STOCK`, `CLOSE`. Journal `source_id` = `year_close_run.id`.
- Schedule line seed mapping, sides and defaults by type: exactly the spec's "Schedule line on accounts" table.
- FY = 1 April – 31 March, label `"2026-27"`; dates IST (`Asia/Kolkata`).
- REST under `/api/v1/admin/accounts/...`: GET = MONEY area, writes (stock save, close, reopen) = OWNER, same as
  existing accounts endpoints (no security change).
- API record names globally unique (springdoc): no bare `Row`, `Line`, `Item`, `Summary`.
- `accounts/package-info.java` `allowedDependencies` becomes `{"settings", "party", "money", "shared :: rupees"}`.
- Admin is a static export: query params, no dynamic routes. New pages in `frontend/admin/src/app/money/accounts/`.
  No hardcoded data; honest empty states.
- Per CLAUDE.md, tests are written with each task but **not run**. Backend verification: scratch worktree
  `./mvnw -q -o compile test-compile -DskipTests`. Frontend: `pnpm typecheck` and `pnpm lint --filter admin` from
  `frontend/`.
- Implementation goes through `bash .claude/scripts/deepseek-coder.sh <spec>` in a haiku subagent; the main session
  reviews, compiles and commits. Stage `backend/src`, `frontend`, `docs`; never `git add backend`.

## Review Focus

1. A year with no income or expense activity and closing stock equal to the Stock balance: close posts no entries,
   still locks the months and marks the year closed; reopen then reverses nothing and succeeds. Test in Task 5.
2. Close when March is already locked: the close unlocks March, posts, relocks, and the lock survives. Test in
   Task 5.
3. Balance sheet for a date inside a closed year after the next year has activity: "Profit not yet closed" shows only
   the open year's profit and the sheet balances. Test in Task 2.
4. GSTR-3B month where ITC exceeds output tax: cash payable is zero per head and the surplus is carried forward,
   never negative. Test in Task 3.
5. A stock item with movements after 31 March: the pre-fill quantity ignores them (IST end of day 31 March).
   Test in Task 5.

---

### Task 1: Schema, schedule lines, fiscal year helper

**Files:**
- Create: `backend/src/main/resources/db/migration/V37__statements_year_end.sql`
- Create: `backend/src/main/java/com/vaabhi/accounts/ScheduleLine.java`, `FiscalYears.java`
- Modify: `Account.java`, `AccountForm.java`, `ChartService.java` (create/update/list), `AssetService.java`
  (`createClass` sets `PPE` on both accounts), `JournalService.java` (`REVERSIBLE`), `package-info.java`
- Test: `backend/src/test/java/com/vaabhi/accounts/ScheduleLineTest.java`, `FiscalYearsTest.java`

**Interfaces:**
- Produces: `enum ScheduleLine { REVENUE_FROM_OPERATIONS, OTHER_INCOME, COST_OF_MATERIALS, PURCHASES_STOCK_IN_TRADE,
  CHANGE_IN_INVENTORIES, EMPLOYEE_BENEFITS, FINANCE_COSTS, DEPRECIATION, OTHER_EXPENSES, SHARE_CAPITAL,
  RESERVES_SURPLUS, LONG_TERM_BORROWINGS, SHORT_TERM_BORROWINGS, TRADE_PAYABLES, OTHER_CURRENT_LIABILITIES, PPE,
  INVENTORIES, TRADE_RECEIVABLES, CASH_AND_BANK, OTHER_CURRENT_ASSETS }` with `AccountType side()`, `String label()`
  (e.g. "Revenue from operations", "Property, plant and equipment"), declaration order = display order,
  `static ScheduleLine defaultFor(AccountType)`, `boolean fits(AccountType)` (`side() == type`).
- Produces: `Account` gains `ScheduleLine scheduleLine` (after `type`); `AccountForm` gains
  `@Nullable ScheduleLine scheduleLine` (last).
- Produces: `final class FiscalYears { static LocalDate startOf(LocalDate d); static LocalDate end(LocalDate fyStart);
  static String label(LocalDate fyStart); static LocalDate parse(String label) /* 400 on bad input */ ;
  static YearMonth[] months(LocalDate fyStart) }`.

- [ ] **Step 1: Tests.** `ScheduleLineTest`: `defaultFor(INCOME) == OTHER_INCOME`, `defaultFor(ASSET) ==
  OTHER_CURRENT_ASSETS`; `PPE.fits(ASSET)`, `!SALES… REVENUE_FROM_OPERATIONS.fits(EXPENSE)`. `FiscalYearsTest`:
  `startOf(2027-03-31) == 2026-04-01`, `startOf(2026-04-01) == 2026-04-01`, `label(2026-04-01) == "2026-27"`,
  `parse("2026-27") == 2026-04-01`, `parse("2026-28")` and `parse("x")` throw `ResponseStatusException` 400,
  `months(2026-04-01)` has 12 entries from 2026-04 to 2027-03. Add to `ChartService`'s IT (or a new
  `ChartScheduleLineIT`): creating an INCOME account with `PPE` → 400 with message format
  `"<label> is not an <type lower-case> line"` ("a" before consonants), e.g. "Property, plant and equipment is not an
  income line".
- [ ] **Step 2: V37.** `ALTER TABLE account ADD COLUMN schedule_line VARCHAR(30)`; UPDATE by type defaults; UPDATE
  system accounts per the spec table (by `system_key`, plus `system_key LIKE 'ASSET\_%' OR LIKE 'ACCDEP\_%'` → PPE,
  excluding `ASSET_GAIN`/`ASSET_LOSS`); `SET NOT NULL`; CHECK constraint `account_schedule_line_check` over the 20
  values. Insert 5150. Rebuild `journal_entry_source_type_check` (V36 list + `YEAR_END`) and
  `journal_entry_source_step_check` (V36 list + `STOCK`, `CLOSE`). Create `fiscal_year`, `closing_stock_line`,
  `year_close_run` exactly as the spec. Trigger `closing_stock_protect` (BEFORE INSERT/UPDATE/DELETE on
  `closing_stock_line`, and BEFORE UPDATE of `finished_goods_rupees, stock_note` on `fiscal_year`) raising
  `'% is closed'` when the year's `closed` is true. Attach the V32 audit trigger to the three tables (same loop as
  V36 lines 365–).
- [ ] **Step 3: Code.** `ScheduleLine`, `FiscalYears`. `ChartService.create` uses `form.scheduleLine()` or
  `defaultFor(type)`; `update` keeps the stored line when absent; both reject `!fits` with 400. System accounts may
  change their line (not their type). `list`/`get` select the column. `AssetService.createClass` inserts both new
  accounts with `schedule_line = 'PPE'`. `JournalService.REVERSIBLE` adds `"YEAR_END"`, and the public reverse
  endpoint path (`AccountsController` `/journal/{id}/reverse`) refuses `YEAR_END` with 409 "Reopen the year instead"
  (check in the controller, so `YearEndService` can still call `reverseOn`). `package-info` dependency change.
- [ ] **Step 4: Compile** (scratch worktree command above). Expected: BUILD SUCCESS, no output.
- [ ] **Step 5: Commit** `feat(accounts): schedule III lines, year-end schema and fiscal years`.

### Task 2: P&L and balance sheet

**Files:**
- Create: `accounts/StatementService.java`, `StatementsController.java`, records `ProfitAndLoss.java`,
  `BalanceSheet.java`, `StatementLine.java`, `StatementAccount.java`
- Test: `backend/src/test/java/com/vaabhi/accounts/StatementServiceIT.java` (use `BooksFixtures`)

**Interfaces:**
- Consumes: `ScheduleLine`, `FiscalYears` (Task 1).
- Produces:
  - `record StatementAccount(long accountId, String code, String name, BigDecimal amountRupees, BigDecimal
    previousRupees)`
  - `record StatementLine(ScheduleLine line, String label, BigDecimal amountRupees, BigDecimal previousRupees,
    List<StatementAccount> accounts)`
  - `record ProfitAndLoss(LocalDate from, LocalDate to, LocalDate previousFrom, LocalDate previousTo,
    List<StatementLine> income, List<StatementLine> expenses, BigDecimal totalIncomeRupees, BigDecimal
    totalExpensesRupees, BigDecimal profitRupees, BigDecimal previousProfitRupees)`
  - `record BalanceSheet(LocalDate asOf, LocalDate previousAsOf, List<StatementLine> equityAndLiabilities,
    List<StatementLine> assets, BigDecimal totalEquityAndLiabilitiesRupees, BigDecimal totalAssetsRupees,
    BigDecimal previousTotalEquityAndLiabilitiesRupees, BigDecimal previousTotalAssetsRupees, boolean balanced)`
  - `StatementService.profitAndLoss(LocalDate from, LocalDate to)`, `balanceSheet(LocalDate asOf)`,
    `BigDecimal unclosedProfit(LocalDate asOf)` (net INCOME − EXPENSE balance as at, all entries).
  - `GET /api/v1/admin/accounts/statements/profit-and-loss?from&to`, `GET .../statements/balance-sheet?asOf`.
- Rules: lines with zero amount and zero previous and no accounts are omitted; accounts with both zero omitted.
  P&L excludes entries `source_type='YEAR_END' AND source_step='CLOSE'` and entries whose `reverses_id` points at
  one. Previous P&L range: if `from..to` is exactly one FY, the previous FY; otherwise the same number of days ending
  the day before `from`. Previous balance sheet date: `asOf.minusYears(1)`. Synthetic `StatementAccount` with
  `accountId = 0`, `code = ""`, `name = "Profit not yet closed"` added to `RESERVES_SURPLUS` (line created if
  missing) when `unclosedProfit` ≠ 0 for either date. `balanced` = totals equal. `from > to` → 400.

- [ ] **Step 1: IT** `StatementServiceIT`: (a) post a sale (Dr Debtors 1000 / Cr Sales 1000) and an expense (Dr
  Rent 300 / Cr Cash 300) in May 2026 → P&L for FY 2026-27 has `REVENUE_FROM_OPERATIONS` 1000, `OTHER_EXPENSES`
  300, profit 700; balance sheet at 2027-03-31 has "Profit not yet closed" 700 and `balanced`. (b) Post a manual
  `YEAR_END`/`CLOSE` entry dated 2027-03-31 zeroing Sales and Rent to Retained earnings → P&L still shows profit 700;
  balance sheet at 2027-03-31 has Retained earnings 700, no "Profit not yet closed", still balanced. (c) Review
  Focus 3: add a May 2027 sale of 200 → balance sheet at 2027-06-30 shows Retained earnings 700 and "Profit not yet
  closed" 200, balanced.
- [ ] **Step 2: Implement** the service with one grouped query per date window (sum debit − credit per account,
  joined to `account` for type/line), then sign-normalise by `type.debitNormal()` and side.
- [ ] **Step 3: Compile.** Expected: BUILD SUCCESS.
- [ ] **Step 4: Commit** `feat(accounts): schedule III profit and loss and balance sheet`.

### Task 3: GSTR-3B

**Files:**
- Create: `accounts/Gstr3bService.java`, `GstReturnsController.java`, `Gstr3b.java`, `Gstr3bRow.java`,
  `Gstr3bSetOff.java`
- Test: `backend/src/test/java/com/vaabhi/accounts/Gstr3bSetOffTest.java`

**Interfaces:**
- Consumes: `money.Gstr1Service.month(YearMonth)` → `Gstr1` (`b2b`, `b2cl`, `b2cs`, `cdnr`, `cdnur` rows with
  `taxable, igst, cgst, sgst` and `columns` map holding place of supply), `InputGstReport.months(LocalDate from,
  LocalDate to)` → `List<InputGstMonth>`.
- Produces:
  - `record Gstr3bRow(String section, String description, BigDecimal taxableRupees, BigDecimal igstRupees,
    BigDecimal cgstRupees, BigDecimal sgstRupees)`
  - `record Gstr3bSetOff(String head, BigDecimal outputRupees, BigDecimal itcUsedRupees, BigDecimal cashRupees,
    BigDecimal carriedForwardRupees)` — heads `"IGST"`, `"CGST"`, `"SGST"`
  - `record Gstr3b(YearMonth month, boolean gstOn, List<Gstr3bRow> outward /*3.1 a,c*/, List<Gstr3bRow>
    interState /*3.2 one per place of supply*/, Gstr3bRow itc /*4(A)(5)*/, List<Gstr3bSetOff> setOff)`
  - `static List<Gstr3bSetOff> Gstr3bService.setOff(BigDecimal outIgst, BigDecimal outCgst, BigDecimal outSgst,
    BigDecimal itcIgst, BigDecimal itcCgst, BigDecimal itcSgst)` (pure)
  - `Gstr3bService.month(YearMonth)`, `String csv(YearMonth)` (header `section,description,taxable,igst,cgst,sgst`,
    3.1, 3.2, 4 rows, then `6.1 <head> cash` rows)
  - `GET /api/v1/admin/accounts/gstr3b?month=2026-05`, `GET .../gstr3b.csv?month=2026-05` (`text/csv`,
    attachment `gstr3b-2026-05.csv`)
- Set-off order (CGST and SGST ITC never cross): IGST ITC → IGST output, then CGST, then SGST; CGST ITC → CGST then
  IGST; SGST ITC → SGST then IGST. Apply IGST ITC first, then CGST ITC, then SGST ITC. Leftover ITC per head is
  `carriedForwardRupees`; `cashRupees` = remaining output per head.
- 3.1(a) = Σ(b2b, b2cl, b2cs) − Σ(cdnr, cdnur). 3.1(c) nil/exempt: rows among those with zero total tax, taxable only
  (moved out of 3.1(a)). 3.2: b2cl + b2cs rows with IGST > 0 grouped by the place-of-supply column, minus cdnur rows
  with IGST > 0 by the same column. Read the actual column key from `Gstr1Service` (e.g. `"Place Of Supply"`).

- [ ] **Step 1: Test** `Gstr3bSetOffTest`: (a) output IGST 100, CGST 50, SGST 50; ITC IGST 120 → IGST cash 0,
  CGST cash 30, SGST 50, carried 0. (b) Review Focus 4: output CGST 10, SGST 10; ITC CGST 40, SGST 5 → CGST cash 0,
  SGST cash 5, CGST carried 30, SGST carried 0; no negative values. (c) all zeros → three zero heads.
- [ ] **Step 2: Implement** service, records, controller; `package-info` already allows `money` (Task 1).
- [ ] **Step 3: Compile.** Expected: BUILD SUCCESS (also `ModularityTests` compile; not run).
- [ ] **Step 4: Commit** `feat(accounts): GSTR-3B summary with set-off`.

### Task 4: TDS 26Q quarterly data

**Files:**
- Create: `accounts/TdsReturnService.java`, `TdsQuarter.java`, `TdsDeducteeRow.java`, `TdsQuarterChallan.java`,
  `TdsSectionTotal.java`
- Modify: `TdsController.java` (two endpoints)
- Test: `backend/src/test/java/com/vaabhi/accounts/TdsReturnServiceIT.java`

**Interfaces:**
- Consumes: `FiscalYears.parse` (Task 1).
- Produces:
  - `record TdsDeducteeRow(long billId, String sectionCode, String partyName, @Nullable String pan, String billNo,
    LocalDate creditedOn, BigDecimal amountRupees, BigDecimal ratePercent, BigDecimal tdsRupees, @Nullable String
    challanNo)`
  - `record TdsQuarterChallan(long id, String sectionCode, LocalDate periodMonth, BigDecimal amountRupees,
    BigDecimal interestRupees, String challanNo, String bsrCode, LocalDate paidOn)`
  - `record TdsSectionTotal(String sectionCode, BigDecimal deductedRupees, BigDecimal depositedRupees, BigDecimal
    differenceRupees)`
  - `record TdsQuarter(String fy, int quarter, LocalDate from, LocalDate to, List<TdsDeducteeRow> deductees,
    List<TdsQuarterChallan> challans, List<TdsSectionTotal> totals)`
  - `TdsReturnService.quarter(LocalDate fyStart, int quarter)`, `String csv(LocalDate fyStart, int quarter, String
    part)` (`part` `deductees` | `challans`, else 400)
  - `GET /api/v1/admin/accounts/tds/returns?fy=2026-27&quarter=1`, `GET .../tds/returns.csv?fy&quarter&part`
    (attachment `26q-2026-27-q1-deductees.csv`)
- Quarter 1 = Apr–Jun … 4 = Jan–Mar; other values → 400. Deductees: `supplier_bill` with `tds_rupees > 0`,
  `status = 'OPEN'`, an existing `journal_entry` (`source_type='SUPPLIER_BILL'`, `source_id = bill id`, step
  `ISSUE`, check the rule's step name in `accounts/rules`), `bill_date` in the quarter; PAN from `party.pan` (blank →
  null); rate = `tds / taxable × 100` to 2 dp (0 when taxable is 0); challan = first `tds_challan` (by `paid_on`)
  with the same section and `period_month` = first of the bill's month. Ordered by section, date, bill id.

- [ ] **Step 1: IT**: two posted bills with TDS in May 2026 (one supplier without PAN) and one cancelled, a challan
  for May → quarter 1 has two deductee rows, the PAN-less row has `pan == null`, the challan number on both,
  totals deducted = sum, deposited = challan amount, difference = deducted − deposited; quarter 5 → 400.
- [ ] **Step 2: Implement.**
- [ ] **Step 3: Compile.** Expected: BUILD SUCCESS.
- [ ] **Step 4: Commit** `feat(accounts): quarterly 26Q TDS return data`.

### Task 5: Year end: closing stock, close, reopen

**Files:**
- Create: `accounts/YearEndService.java`, `YearEndController.java`, `YearEnd.java`, `ClosingStockLine.java`,
  `YearEndCheck.java`, `YearCloseRunView.java`, `YearEndStockForm.java`
- Test: `backend/src/test/java/com/vaabhi/accounts/YearEndServiceIT.java`

**Interfaces:**
- Consumes: `FiscalYears`, `StatementService.profitAndLoss`; existing `JournalService.post(NewEntry, actor)`,
  `reverseOn(id, date, actor)`, `trialBalance(asOf)`; `PeriodService.list/lock(YearMonth, actor)/unlock(YearMonth)`;
  `JournalSweeper.sweepNow()`; `PostingProblemService.count()`; `BooksSettingsService` (start date);
  `ChartService.idOf("STOCK" | "CHANGE_IN_INVENTORIES" | "RETAINED_EARNINGS")`.
- Produces:
  - `record ClosingStockLine(long stockItemId, String name, String unit, BigDecimal quantity, BigDecimal
    rateRupees, BigDecimal valueRupees)`
  - `record YearEndCheck(String key, String label, boolean ok, @Nullable String message)` — keys
    `START_DATE`, `PREVIOUS_YEAR`, `PROBLEMS`, `TRIAL_BALANCE`, `STOCK_SAVED`
  - `record YearCloseRunView(long id, @Nullable Long stockEntryId, @Nullable Long closingEntryId, @Nullable String
    closedBy, OffsetDateTime closedAt, @Nullable String reopenedBy, @Nullable OffsetDateTime reopenedAt)`
  - `record YearEnd(String fy, LocalDate from, LocalDate to, boolean closed, boolean stockSaved, boolean
    canReopen, List<ClosingStockLine> stock, BigDecimal finishedGoodsRupees, @Nullable String stockNote, BigDecimal
    closingStockRupees, BigDecimal stockBalanceRupees, BigDecimal adjustmentRupees, BigDecimal profitRupees,
    List<YearEndCheck> checks, List<YearCloseRunView> runs)`
  - `record YearEndStockForm(@NotNull @Valid List<YearEndStockItemInput> lines, @NotNull @PositiveOrZero BigDecimal
    finishedGoodsRupees, @Nullable @Size(max = 300) String note)` with nested-free
    `record YearEndStockItemInput(long stockItemId, @PositiveOrZero BigDecimal quantity, @PositiveOrZero BigDecimal
    rateRupees)` (own file)
  - `YearEndService.get(LocalDate fyStart)`, `saveStock(fyStart, form, actor)`, `close(fyStart, actor)`,
    `reopen(fyStart, actor)`, `List<ClosingStockLine> savedStock(fyStart)` (for the pack)
  - `GET /api/v1/admin/accounts/year-end/{fy}`, `PUT .../year-end/{fy}/stock`, `POST .../year-end/{fy}/close`,
    `POST .../year-end/{fy}/reopen` (all return `YearEnd`; `{fy}` = label)
- Behaviour: as the spec's `YearEndService` section, with the deviation above. `stockBalanceRupees` = Stock account
  balance at 31 March excluding entries of the current close run (i.e. before step 4; for a closed year read it as
  balance minus the run's stock entry). `adjustmentRupees` = closing − stock balance. `profitRupees` = P&L profit
  for the FY plus the adjustment when not yet closed (the P&L already includes it once closed). `canReopen` = closed
  and no later year closed. Pre-fill when no lines saved: items with on-hand > 0 at `2027-03-31T23:59:59+05:30`
  (sum of `stock_movement.quantity` where `moved_at <=` that instant), rate `cost_paise / 100`. Value = quantity ×
  rate, HALF_UP 2 dp. `saveStock` validates each `stockItemId` exists (400) and upserts `fiscal_year`. Narrations:
  "Closing stock 2026-27", "Closing entry 2026-27". The CLOSE entry has one line per non-zero INCOME/EXPENSE account
  (debit income balances, credit expense balances) and one Retained earnings line for the net; skip when the net
  and all balances are zero. Locks: record which months were locked before; unlock March if needed; after posting,
  lock all 12 (skip ones already locked). Errors as the spec's Errors section (409s, exact copy).

- [ ] **Step 1: IT** `YearEndServiceIT`: (a) stock item with IN 10 on 2027-03-10 and OUT 4 on 2027-04-02, cost 5000
  paise → pre-fill quantity 10, rate 50.00, value 500.00 (Review Focus 5). (b) save stock (finished goods 200), sale
  1000 and rent 300 in FY 2026-27, opening Stock 0 → close: stock entry Dr Stock 700 / Cr Change in inventories
  700, closing entry leaves every income/expense balance 0 at 2027-03-31 and Retained earnings = 1400, all 12 months
  locked, `closed`. (c) reopen → both entries reversed (balances back), March unlocked, April 2026 still locked;
  close again → new run, new entry ids, no unique-index error. (d) Review Focus 2: lock March before close → close
  succeeds and March is locked afterwards. (e) Review Focus 1: empty year with stock saved at 0 → close succeeds,
  `stockEntryId` and `closingEntryId` null, months locked; reopen succeeds. (f) close without saved stock → 409
  "Save the closing stock list first"; save stock on a closed year → 409.
- [ ] **Step 2: Implement.**
- [ ] **Step 3: Compile.** Expected: BUILD SUCCESS.
- [ ] **Step 4: Commit** `feat(accounts): year-end closing stock, close and reopen`.

### Task 6: Auditor pack workbook

**Files:**
- Create: `accounts/AuditorPackService.java`
- Modify: `YearEndController.java` (download endpoint)
- Test: `backend/src/test/java/com/vaabhi/accounts/AuditorPackServiceIT.java`

**Interfaces:**
- Consumes: `StatementService`, `YearEndService.get/savedStock`, `Gstr3bService.month`, `TdsReturnService.quarter`,
  `JournalService.trialBalance`, `BankReconciliationService.report(long)`, `AssetService.register()`,
  `SettingsService.billing().company()` (legal name, CIN).
- Produces: `void AuditorPackService.write(LocalDate fyStart, OutputStream out)`;
  `GET /api/v1/admin/accounts/year-end/{fy}/auditor-pack.xlsx` → `StreamingResponseBody`, content type
  `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, `Content-Disposition: attachment;
  filename=vaabhi-books-2026-27.xlsx`.
- Sheets in this order and with these names: `About`, `Trial balance`, `Profit and loss`, `Balance sheet`,
  `Day book`, `Ledgers`, `Debtors`, `Creditors`, `Sales register`, `Purchase register`, `Fixed assets`,
  `Closing stock`, `Bank reconciliation`, `GSTR-3B`, `TDS`. Contents per the spec's table. Trial balance excludes
  CLOSE entries: add `JournalService.trialBalance(LocalDate asOf, boolean excludeClosing)` (the existing one-arg
  method delegates with `false`). Writer: `org.dhatim.fastexcel.Workbook(out, "Vaabhi", "1.0")`,
  `ws.value(row, col, ...)` (row first), rupees as `BigDecimal` with style `format("#,##0.00")`, dates with
  `format("dd-mm-yyyy")`, bold header row 0. `About` shows "Draft: year not closed" when `!closed`. Each sheet with
  no rows still has its header row.

- [ ] **Step 1: IT**: with one sale and one bill in FY 2026-27, `write` into a byte array and read it back with
  `ReadableWorkbook` → 15 sheets in the listed order; `About` contains "Draft: year not closed"; `Day book` has a
  header plus ≥ 2 rows; `Trial balance` totals equal.
- [ ] **Step 2: Implement.** Registers and debtors/creditors lists are new SQL in this service (invoices +
  credit notes from the money tables, `supplier_bill` + `party.gstin`; party balances from `journal_line.party_id`
  on Debtors/Creditors).
- [ ] **Step 3: Compile.** Expected: BUILD SUCCESS.
- [ ] **Step 4: Commit** `feat(accounts): auditor pack workbook`.

### Task 7: Regenerate the API client

- [ ] **Step 1:** Ask the owner to restart the backend from IntelliJ (`VaabhiApplication`) so V37 applies; confirm
  `/v3/api-docs` lists `/api/v1/admin/accounts/statements/profit-and-loss`.
- [ ] **Step 2:** `pnpm generate:api` from `frontend/`; add typed calls to `frontend/admin/src/lib/accounts.ts`
  (or a new `lib/statements.ts`) for every Phase 5 endpoint, following existing helpers; xlsx/CSV downloads use the
  existing authenticated fetch + blob pattern in `lib/api.ts`.
- [ ] **Step 3:** `pnpm typecheck --filter admin`. Expected: no errors.
- [ ] **Step 4: Commit** `chore(api-client): regenerate for statements, returns and year end`.

### Task 8: Admin: schedule line on chart, Statements page

**Files:**
- Modify: `frontend/admin/src/app/money/accounts/chart/page.tsx` (select + column), `money/accounts/page.tsx` (links)
- Create: `frontend/admin/src/app/money/accounts/statements/page.tsx`; `frontend/admin/src/lib/statements.ts`
  (`SCHEDULE_LINES: {value, label, side}[]` matching the backend enum and labels, `linesFor(type)`)
- Test: `frontend/admin/src/lib/statements.test.ts` (`linesFor("INCOME")` = the two income lines)

- [ ] **Step 1:** Chart form: "Schedule III line" select filtered by the selected type, defaulting to the type's
  default; switching type resets it. List column shows the label.
- [ ] **Step 2:** Statements page per the spec's Admin UI: tabs (`?tab=pl|bs`), FY select (`fyRange`/`fyLabel`
  from `lib/accounts.ts`) or custom from/to, as-at date, expandable lines, account rows link to
  `/money/accounts/ledger/?id=`, previous column, "Download Excel" link to the FY's auditor pack, unbalanced banner
  when `!balanced`, empty state "No entries in this period".
- [ ] **Step 3:** Books hub (`money/accounts/page.tsx`) gains links Statements, GSTR-3B (`/money/accounts/gstr3b/`),
  Year end (`/money/accounts/year-end/`).
- [ ] **Step 4:** `pnpm typecheck` and `pnpm lint --filter admin`. Expected: 0 errors.
- [ ] **Step 5: Commit** `feat(admin): schedule III lines and statements`.

### Task 9: Admin: GSTR-3B page and TDS returns tab

**Files:**
- Create: `frontend/admin/src/app/money/accounts/gstr3b/page.tsx`
- Modify: `frontend/admin/src/app/money/accounts/tds/page.tsx` (Returns tab), `frontend/admin/src/app/reports/gst/page.tsx`
  (link "GSTR-3B for this month")

- [ ] **Step 1:** GSTR-3B: month picker (`?month=`), "Not registered for GST" note when `!gstOn`, tables 3.1, 3.2,
  4 and 6.1 (output, ITC used, cash, carried forward), CSV download.
- [ ] **Step 2:** TDS Returns tab (`?tab=returns`): FY and quarter selects, deductee table with "PAN missing" badge,
  challan table, per-section deducted/deposited/difference (difference ≠ 0 highlighted), two CSV buttons; empty
  state "No TDS deducted this quarter".
- [ ] **Step 3:** typecheck + lint. Expected: 0 errors.
- [ ] **Step 4: Commit** `feat(admin): GSTR-3B and TDS returns`.

### Task 10: Admin: Year end page

**Files:**
- Create: `frontend/admin/src/app/money/accounts/year-end/page.tsx`

- [ ] **Step 1:** FY select (`?fy=2026-27`, default the latest FY whose end is before today, else current).
  Checklist with ok/fail and message. Stock table (name, unit, editable quantity and rate via `toPaise`-style
  parsing, computed value, remove row), finished-goods input, note, Save (owner only, `useIsOwner`). Disabled when
  closed. Summary: closing stock, Stock balance, adjustment, profit. "Close year" opens a confirm sheet stating the
  months will lock; "Reopen" only when `canReopen`. Runs list with links to the journal entries
  (`/money/accounts/entry/?id=`). "Download auditor pack" always shown. API errors shown inline (existing error
  pattern).
- [ ] **Step 2:** typecheck + lint. Expected: 0 errors.
- [ ] **Step 3: Commit** `feat(admin): year-end close and auditor pack`.

### Task 11: Gaps and wrap-up

- [ ] **Step 1:** Add "Phase 5: statements, returns, year end" to `docs/BOOKS_GAPS.md`: the spec's Gaps list, the
  two plan deviations (problem check not limited to the year; bank reconciliation per statement), and anything found
  in review.
- [ ] **Step 2:** Whole-branch review of the Phase 5 diff; spotless (`./mvnw -q spotless:apply` in the scratch
  worktree, copy changed files back) and prettier on changed frontend files.
- [ ] **Step 3:** Commit `docs(accounts): phase 5 gaps` (and `style:` commit if formatting changed); push the branch.
