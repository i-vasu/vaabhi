# Phase 2 General Ledger Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A double-entry journal in a new `accounts` module, fed by a sweeper that posts every invoice, credit note,
payment, refund and expense from the books-start date, with chart, trial balance, ledgers, manual journals, opening
balances, period locks and an audit log.

**Architecture:** V32 adds the tables, balance/immutability/lock triggers and audit triggers. `accounts` reads source
tables by SQL (like `money/LedgerService` does) and never imports other modules' services. One `PostingRule` per
source+step builds a `NewEntry`; `JournalSweeper` posts pending sources one transaction each and records failures as
posting problems. Admin pages live under Money → Accounts.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Modulith, JdbcClient, Postgres + Flyway; Next.js 16 admin (static
export); pnpm + Turborepo.

**Spec:** `docs/superpowers/specs/2026-11-12-phase2-general-ledger-design.md`

## Global Constraints

- Branch `feat/id-engine-parties` (PR #48). Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Do not run tests; write them. Controller compiles in a scratch worktree (`./mvnw -q -o compile test-compile -DskipTests`), runs Spotless/Prettier, typecheck and lint, and commits. Never `git add backend` (stage `backend/src`).
- Migration `V32__general_ledger.sql`, the only migration.
- Money columns `NUMERIC(12,2)` named `*_rupees`; use `shared.rupees.Rupees`. Dates in Asia/Kolkata.
- `accounts` must not import `money`, `checkout`, `tailoring`, `payroll` services or records; it may use `settings` (NumberingService) and `party`. No other module imports `accounts`.
- API record names must be unique across the backend (springdoc names schemas by simple class name — no nested `Row`, `Line`, `Item`).
- Reads need the MONEY area; every write under `/api/v1/admin/accounts` needs OWNER.
- Don't hand-edit `schema.d.ts`/`openapi.json`; the controller regenerates after Task 4.
- No hardcoded demo data; honest empty states.

## Rulings made while planning

1. Books settings (start date, mode → account map) live in an `accounts`-owned table `books_setting` (single row), not a settings-module section, so `settings` never needs accounts' types. Cost: none visible.
2. `journal_entry.order_key` (`SHOP:<shop_order id>` / `WEB:<web_order id>`, nullable) is added so APPLY can find an order's advances with one query. Cost: one column.
3. The Payment accounts card sits on the Accounts home page (the map is accounts data), not Settings → Billing.
4. `vaabhi.actor` is set by a Spring `TransactionExecutionListener` (`afterBegin`) that runs `SELECT set_config('vaabhi.actor', <staff name>, true)` when a staff user is authenticated — no per-service code.
5. Sweep order within one run: RECEIPT rules, then invoice ISSUE, APPLY, CANCEL, credit-note ISSUE, then REFUND, EXPENSE — so a receipt dated before its invoice is posted before APPLY reads the advances.

## Review Focus

1. A receipt posted after its invoice's APPLY already ran (late backfill or a fixed posting problem) must not leave money stranded on Customer advances — the daily check flags "Debtors for INV/… differ from its balance due".
2. A web order cancelled before shipping (no invoice) must refund out of Customer advances, leaving that order's advances at 0.
3. With the start date unset, nothing posts and every Accounts read returns empty data, not errors.
4. Opening balances saved twice must leave exactly one live OPENING entry (old one reversed), still balanced.
5. A manual entry with an inactive account, a one-sided line (both debit and credit, or neither) or a locked date is refused with a 400/409 message, never a 500.

Tests for each are named in Tasks 2–4.

---

### Task 1: V32 schema, triggers, audit log, actor

**Files:**
- Create: `backend/src/main/resources/db/migration/V32__general_ledger.sql`
- Modify: `backend/src/main/java/com/vaabhi/settings/NumberSequence.java` (Kind `JOURNAL`)
- Create: `backend/src/main/java/com/vaabhi/shared/database/ActorTransactionListener.java`
- Test: `backend/src/test/java/com/vaabhi/accounts/LedgerSchemaIT.java`

**Interfaces:**
- Produces tables per spec "Data", plus: `journal_entry.order_key VARCHAR(24)` (Ruling 2); `books_setting(id SMALLINT PK CHECK (id = 1), start_date DATE, mode_accounts JSONB NOT NULL, last_sweep_at TIMESTAMPTZ)` seeded with id 1 and the default map `{"CASH":<1000 id>,"UPI":<1010>,"CARD":<1010>,"BANK":<1010>,"RAZORPAY":<1020>,"COD":<1030>}`.
- Number sequence row `('JOURNAL', 'JV/{FY}/', 1, '', 5, 'FY')` (columns as in V29's inserts).
- Expense accounts: one per value of the expense `category` CHECK (V9: RENT, SALARY, MATERIAL, UTILITIES, TRANSPORT, MAINTENANCE, MARKETING, OTHER — confirm no later migration changed it), system_key `EXPENSE_<CATEGORY>`, codes 5000, 5010 (Salaries), 5020, … 5090 (Other).
- Triggers: `journal_balanced` (DEFERRABLE INITIALLY DEFERRED constraint trigger on journal_line and journal_entry: sum debit = sum credit and ≥ 2 lines, message "Journal entry <number> does not balance"); `journal_immutable` (BEFORE UPDATE OR DELETE on both → raise "Posted journal entries cannot be changed"); `journal_period_open` (BEFORE INSERT on journal_entry → raise "<Month YYYY> is locked" when `date_trunc('month', entry_date)` is in period_lock); `audit_row()` generic AFTER INSERT/UPDATE/DELETE row trigger writing `audit_log` (to_jsonb(OLD)/to_jsonb(NEW), `current_setting('vaabhi.actor', true)`), attached to the tables listed in the spec (for `web_order` only when total/paid/refund columns change).
- `ActorTransactionListener implements TransactionExecutionListener`; `afterBegin` reads the authenticated JWT's staff name (same claim `MoneyController.actor(jwt)` uses) and runs `set_config`; registered on the JDBC transaction manager (find how the app's `PlatformTransactionManager` is built; if Boot auto-configures it, add the listener via `TransactionManagerCustomizers`/`TransactionExecutionListener` bean — Boot 3.2+ applies listener beans automatically).

- [ ] **Step 1: Write `LedgerSchemaIT`:** `unbalancedEntryRejectedAtCommit`, `postedLineCannotBeUpdatedOrDeleted`, `lockedMonthRejectsEntry` (message contains "is locked"), `auditLogRecordsInvoiceUpdate` (update an invoice's `title` inside a transaction with `set_config('vaabhi.actor','Asha',true)` → one audit_log row with old and new title and actor "Asha"), `chartSeeded` (system keys CASH, BANK, DEBTORS, CUSTOMER_ADVANCES, SALES, EXPENSE_SALARY exist), `journalSequenceSeeded`.
- [ ] **Step 2: Write V32 and the listener; add Kind `JOURNAL`.**
- [ ] **Step 3: Commit** `feat(accounts): V32 ledger schema, triggers and audit log`.

### Task 2: Chart, journal, trial balance, periods, opening balances, books settings

**Files:**
- Create in `backend/src/main/java/com/vaabhi/accounts/`: `package-info.java` (Modulith module, allowed deps settings + party), `Account.java`, `AccountType.java`, `AccountForm.java`, `ChartService.java`, `NewEntry.java`, `NewEntryLine.java`, `JournalEntry.java`, `JournalEntryLine.java`, `JournalPage.java`, `JournalListItem.java`, `JournalService.java`, `LedgerStatement.java`, `LedgerStatementLine.java`, `TrialBalance.java`, `TrialBalanceLine.java`, `PeriodService.java`, `PeriodStatus.java`, `OpeningBalances.java`, `OpeningBalanceLine.java`, `OpeningService.java`, `BooksSettings.java`, `BooksSettingsService.java`
- Test: `backend/src/test/java/com/vaabhi/accounts/JournalServiceIT.java`, `OpeningServiceIT.java`

**Interfaces (Produces):**
- `record Account(long id, String code, String name, AccountType type, @Nullable Long parentId, @Nullable String systemKey, boolean active, BigDecimal balanceRupees)` — balance = debits − credits for ASSET/EXPENSE, credits − debits otherwise.
- `ChartService`: `List<Account> list()`, `Account create(AccountForm)`, `Account update(long id, AccountForm)` (system accounts: name only; cannot deactivate → 409 "System accounts can't be turned off"), `long idOf(String systemKey)`.
- `record NewEntry(LocalDate date, String sourceType, @Nullable Long sourceId, String sourceStep, String narration, @Nullable String orderKey, @Nullable Long reversesId, List<NewEntryLine> lines)`; `record NewEntryLine(long accountId, @Nullable Long partyId, BigDecimal debitRupees, BigDecimal creditRupees, @Nullable String memo)`.
- `JournalService`:
  - `JournalEntry post(NewEntry entry, @Nullable String actor)` (`Propagation.REQUIRED`): 400 "Add at least two lines"; 400 "Each line needs a debit or a credit, not both"; 400 "Debits and credits must be equal"; 400 "<code name> is turned off" for inactive accounts; 409 "<Month YYYY> is locked" (check before insert, the trigger is the backstop); number from `NumberingService.next(JOURNAL)`.
  - `JournalEntry reverse(long id, @Nullable String actor)`: only MANUAL/OPENING sources, else 409 "Only manual and opening entries can be reversed"; already reversed → 409 "This entry is already reversed"; posts a REVERSAL entry dated today (IST) with sides swapped and `reversesId`.
  - `JournalEntry get(long id)` (with lines, account code/name, party name, and `sourceLink` = `{type, id, label}` resolved by SQL: invoice/credit note number, shop order or web order number, expense description).
  - `JournalPage list(@Nullable String q, @Nullable LocalDate from, @Nullable LocalDate to, int page, int size)`.
  - `LedgerStatement ledger(long accountId, LocalDate from, LocalDate to)` (opening balance before `from`, lines with running balance, closing).
  - `TrialBalance trialBalance(LocalDate asOf)` (accounts with non-zero balance, debit/credit columns, totals, `balanced`).
- `PeriodService`: `List<PeriodStatus> list()` (months from start date's month to the current month, newest first; empty when no start date), `lock(YearMonth, actor)`, `unlock(YearMonth)`.
- `BooksSettings(@Nullable LocalDate startDate, Map<String, Long> modeAccounts, @Nullable OffsetDateTime lastSweepAt)`; `BooksSettingsService`: `get()`, `save(BooksSettings, actor)` (mode keys must be the six modes and ids active ASSET accounts → 400 "Pick an asset account for <MODE>"), `long accountFor(String mode)`, `markSwept(OffsetDateTime)`. Publishes `BooksStarted` (record in accounts) the first time startDate goes from null to a date.
- `OpeningService`: `OpeningBalances get()` (balance-sheet accounts with the live OPENING entry's amounts), `OpeningBalances save(List<OpeningBalanceLine> lines, actor)` — requires startDate (409 "Set the books start date first"); reverses the live OPENING entry if any, posts a new one dated startDate with the remainder to `OPENING_BALANCE`; `OpeningBalanceLine(long accountId, BigDecimal debitRupees, BigDecimal creditRupees)`.

- [ ] **Step 1: Write ITs:** `postsBalancedEntryWithNumber` (JV/26-27/00001 pattern), `rejectsOneLine`, `rejectsLineWithBothSides`, `rejectsInactiveAccount`, `rejectsLockedMonth` (Review Focus 5), `reverseSwapsSides`, `reverseRefusesSweptEntry`, `trialBalanceBalances`, `ledgerRunningBalance`, `periodsEmptyWithoutStartDate` (Review Focus 3), `openingSavedTwiceLeavesOneLiveEntry` (Review Focus 4: after two saves the trial balance equals the second save; first OPENING has a REVERSAL), `modeAccountMustBeAsset`.
- [ ] **Step 2: Implement.**
- [ ] **Step 3: Commit** `feat(accounts): chart, journal, trial balance, periods and opening balances`.

### Task 3: Posting rules and the sweeper

**Files:**
- Create in `accounts/`: `PostingRule.java`, `rules/` package-private classes (one per row below), `JournalSweeper.java`, `PostingProblem.java`, `PostingProblemService.java`, `AccountsStatus.java`, `AccountsStatusService.java`, `DailyBooksCheck.java`
- Test: `backend/src/test/java/com/vaabhi/accounts/PostingRulesIT.java`, `JournalSweeperIT.java`

**Interfaces:**
- Consumes: Task 2 `JournalService.post`, `ChartService.idOf`, `BooksSettingsService.accountFor/get/markSwept`.
- `interface PostingRule { String sourceType(); String step(); int order(); List<Long> pending(LocalDate start, int limit); NewEntry build(long sourceId); }` — `pending` = source rows dated ≥ start with no `journal_entry` for (sourceType, id, step).
- Rules (source table → type/step; entry date = the source's IST date; party = client.party_id or customer.party_id):
  - `ShopPaymentReceipt` order_payment amount > 0 → ORDER_PAYMENT/RECEIPT, orderKey `SHOP:<order>`; credit Customer advances if the order had no live invoice created before the payment, else Debtors; debit `accountFor(mode)`.
  - `WebPaymentReceipt` web_order with paid_at → WEB_ORDER/RECEIPT, orderKey `WEB:<id>`, mode RAZORPAY when payment_method ONLINE else COD, amount total_rupees; same advances/Debtors test against the web invoice.
  - `AlterationReceipt` alteration_payment (amount > 0 RECEIPT: Dr mode / Cr ALTERATION_INCOME; < 0 REFUND: reversed).
  - `InvoiceIssue` invoice → INVOICE/ISSUE: Dr DEBTORS total; Cr SALES taxable; Cr OUTPUT_CGST/SGST/IGST non-zero; orderKey from order_id/web_order_id.
  - `InvoiceApply` invoice whose orderKey has a Customer-advances credit balance → INVOICE/APPLY: Dr CUSTOMER_ADVANCES, Cr DEBTORS, amount = min(advances balance on that orderKey, invoice total); `pending` skips invoices with zero advances (they get no APPLY row; a later receipt is handled by the daily check).
  - `InvoiceCancel` invoice with cancelled_at → INVOICE/CANCEL: reverse of ISSUE plus of APPLY when posted (one entry).
  - `CreditNoteIssue` credit_note → CREDIT_NOTE/ISSUE: Dr SALES_RETURNS taxable, Dr OUTPUT_* parts, Cr DEBTORS total.
  - `ShopRefund` order_payment amount < 0 → ORDER_PAYMENT/REFUND; `WebOrderRefund` web_order refund_status PROCESSED with refunded_rupees > 0 → WEB_ORDER/REFUND (mode RAZORPAY); `WebReturnRefund` web_order_return refund_status PROCESSED → WEB_RETURN/REFUND (RAZORPAY, or BANK for MANUAL): Dr DEBTORS if the order has a live invoice, else CUSTOMER_ADVANCES; Cr mode account.
  - `ExpensePosting` expense → EXPENSE/EXPENSE: Dr `EXPENSE_<category>`, Cr `accountFor(mode)`.
- `JournalSweeper`: `@Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT2M")` and `public SweepResult sweepNow()`; also `@EventListener(BooksStarted)` → sweep. Rules run in `order()` (Ruling 5), up to 500 sources per rule per run, each in its own `TransactionTemplate`; exceptions → `PostingProblemService.record(type, id, step, message)`; success → `clear(...)`; finally `markSwept(now)`. `record SweepResult(int posted, int problems)`.
- `DailyBooksCheck`: `@Scheduled(cron = "0 30 3 * * *", zone = "Asia/Kolkata")`: trial balance unbalanced → problem (type `CHECK`, step `TRIAL_BALANCE`); each invoice whose Debtors balance on its orderKey ≠ its balance due → problem `CHECK`/`DEBTORS` "Debtors for INV/… is ₹x but ₹y is due".
- `AccountsStatus(@Nullable LocalDate startDate, @Nullable OffsetDateTime lastSweepAt, int problemCount, BigDecimal trialBalanceDifference)`.

- [ ] **Step 1: Write ITs** (seed sources via existing services or SQL matching current tables; set start date to the first of the month): `shopAdvanceThenInvoiceMovesToDebtors` (Debtors on the order = balance due), `paymentAfterInvoiceCreditsDebtors`, `webOnlineOrderReceiptAndInvoice`, `codOrderPaidAtDelivery` (Dr COD clearing, Debtors 0 afterwards), `webCancelBeforeShipRefundsFromAdvances` (Review Focus 2), `creditNoteEntry`, `invoiceCancelReversesIssueAndApply`, `expensePostsToCategoryAccount`, `alterationReceipt`, `sweepTwicePostsOnce`, `nothingPostsWithoutStartDate` (Review Focus 3), `failingSourceBecomesProblemAndClears`, `lateReceiptFlaggedByDailyCheck` (Review Focus 1).
- [ ] **Step 2: Implement.**
- [ ] **Step 3: Commit** `feat(accounts): posting rules and journal sweeper`.

### Task 4: Accounts API and security

**Files:**
- Create: `accounts/AccountsController.java`, request records `ManualEntryForm.java`, `ManualEntryLineInput.java`, `OpeningBalancesForm.java`, `BooksSettingsForm.java`
- Modify: `backend/src/main/java/com/vaabhi/shared/security/SecurityConfig.java`
- Test: `backend/src/test/java/com/vaabhi/accounts/AccountsControllerIT.java`

**Interfaces:** endpoints exactly as the spec "API" lists, under `/api/v1/admin/accounts`. `ManualEntryForm(LocalDate date, @NotBlank @Size(max=200) String narration, @Size(min=2) List<@Valid ManualEntryLineInput> lines)`; `ManualEntryLineInput(long accountId, @Nullable Long partyId, BigDecimal debitRupees, BigDecimal creditRupees, @Nullable @Size(max=120) String memo)`. Problems: `GET problems` → `List<PostingProblem(sourceType, sourceId, step, message, firstSeen, lastSeen, @Nullable String sourceLabel)>`. Security: GET `/api/v1/admin/accounts/**` → `area("MONEY")`; any other method → `hasRole("OWNER")`; placed before the generic admin rules.

- [ ] **Step 1: Test** `staffWithMoneyCanRead`, `staffWithoutOwnerCannotPostManual` (403), `ownerPostsAndReversesManualEntry`, `manualUnbalancedIs400`.
- [ ] **Step 2: Implement.**
- [ ] **Step 3: Commit** `feat(accounts): admin accounts API`.

### Task 5 (controller): regenerate the API client and add wrappers

- [ ] Restart `VaabhiApplication` (stop the old java on :8080 first), `pnpm generate:api`, add `api.admin.accounts.{status, sweep, problems, settings, saveSettings, chart, createAccount, updateAccount, ledger, journal, entry, postManual, reverse, trialBalance, opening, saveOpening, periods, lock, unlock}` wrappers and type aliases in `frontend/packages/api-client/src/index.ts`; typecheck; commit `chore(api-client): accounts API`.

### Task 6: Admin — Accounts home, chart and ledger, journal, trial balance

**Files:** create under `frontend/admin/src/app/money/accounts/`: `page.tsx` (home), `chart/page.tsx`, `ledger/page.tsx` (`?id=`), `journal/page.tsx`, `entry/page.tsx` (`?id=`), `trial-balance/page.tsx`; modify `frontend/admin/src/app/money/page.tsx` (Accounts row); test `frontend/admin/src/app/money/accounts/accounts.test.tsx` with fixtures in `frontend/admin/src/test/`.

- [ ] **Step 1:** Home: start-date panel ("Books haven't started. Set the start date to begin." + owner-only date input and Set), last sweep ("Posted 2 minutes ago"), **Post now** (owner), problems count linking to the problems section, "Balanced" or "Out by ₹x", links to the other pages, and the **Payment accounts** card (six mode selects over active asset accounts; owner-only save). Chart grouped by type with code, name, balance; owner add/rename/turn off. Ledger: date range (default this month), opening, lines with entry link, running balance, closing, CSV download (client-side from the rows). Journal: search, date range, paging, Reversed tag; entry view with lines, source link (invoice → `/money/invoices/view/?id=`, credit note → `/money/credit-notes/view/?id=`, shop order → `/orders/view/?id=`, web order → `/online-orders/view/?number=`, expense → `/money/expenses/`) and owner **Reverse** on MANUAL/OPENING. Trial balance as at a date with debit/credit totals and "Balanced ✓".
- [ ] **Step 2:** Tests: home shows the not-started message when startDate is null; trial balance shows "Balanced ✓"; entry view links an invoice line to `/money/invoices/view/?id=`.
- [ ] **Step 3:** Commit `feat(admin): accounts home, chart, journal and trial balance` (controller runs Prettier, typecheck, lint).

### Task 7: Admin — manual journal, opening balances, periods, problems

**Files:** create `frontend/admin/src/app/money/accounts/manual/page.tsx`, `opening/page.tsx`, `periods/page.tsx`, `problems/page.tsx`; tests in `accounts.test.tsx`.

- [ ] **Step 1:** Manual journal (owner only; others see "Only the owner can post journals"): date, narration, ≥ 2 lines (account select, optional party search reusing an existing party/client picker if one exists else none, debit, credit, memo), live "Difference ₹x", Save disabled unless difference is 0 and every line has exactly one side. Opening balances: balance-sheet accounts with debit/credit inputs, live remainder "₹x will go to Opening balance equity", Save (owner). Periods: months with Locked/Open and Lock/Unlock (owner), confirm copy "Entries dated in <Month YYYY> will be refused". Problems: list with source label link, message, first/last seen, and Post now.
- [ ] **Step 2:** Tests: manual Save disabled while difference ≠ 0 and enabled at 0; opening shows the remainder; periods lock button calls the lock endpoint.
- [ ] **Step 3:** Commit `feat(admin): manual journals, opening balances and periods`.

### Task 8 (controller): final review and handoff

- [ ] Whole-branch review (most capable model) over this plan's range, one fix wave, one re-review; push; update PR #48 body with a Phase 2 section and the spec's manual checks; update memory; report rulings.
