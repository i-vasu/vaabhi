# Books Phase 3: Bank Reconciliation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Import bank statements and Shiprocket COD remittance reports, pull Razorpay settlements, post them to the
ledger, match bank lines to ledger Bank movements, and show a per-statement reconciliation report.

**Architecture:** New tables in V35. `checkout` fetches Razorpay settlements and stores them (it owns the Razorpay
client). `accounts` owns everything else: file parsing, bank lines, matching, COD remittances, the report, and two
new `PostingRule`s that the existing `JournalSweeper` uses to post settlements and remittances (so locked periods and
the problems list work as in Phase 2). Bank-line actions (match difference, post as) post directly via
`JournalService.post`.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Modulith, JdbcClient, Flyway, Postgres, commons-csv,
fastexcel-reader; Next.js 16 static-export admin, Tailwind 4.

**Spec:** `docs/superpowers/specs/2026-11-12-phase3-bank-reconciliation-design.md`

**Deviation from spec (wiring only):** settlements and remittances are posted by the sweeper through
`RazorpaySettlementRule` and `CodRemittanceRule`, not inline during sync/import. `accounts` reads `web_order` and
`razorpay_settlement` through SQL, as Phase 2 rules already do; `accounts` gets no new Modulith dependency.

## Global Constraints

- Migration is `V35__bank_reconciliation.sql`; never edit V1–V34.
- Money: `NUMERIC(12,2)` in SQL, `BigDecimal` via `Rupees.of(...)` in Java; never double.
- New accounts: `5070 Payment gateway fees` `EXPENSE_GATEWAY_FEES`, `5080 Courier & COD charges` `EXPENSE_COURIER`,
  `5095 Bank charges` `EXPENSE_BANK_CHARGES`, all type `EXPENSE`.
- New `source_type` values `RAZORPAY_SETTLEMENT`, `COD_REMITTANCE`, `BANK_LINE`; new `source_step` values `SETTLE`,
  `MATCH`, `POST`.
- REST base path `/api/v1/admin/accounts/bank` (already OWNER-only in `SecurityConfig`; no security change).
- API record names must be globally unique (springdoc uses simple class names): no `Row`, `Line`, `Item`, `Result`.
- Upload limit 5 MB; preview returns the first 20 rows.
- Auto-match window ±3 days. Exactly one candidate → match; otherwise leave `UNMATCHED`.
- Dates in IST (`Asia/Kolkata`). Razorpay sync cron `0 0 6 * * *` IST.
- Admin is a static export: pages use `?id=` query params, not dynamic routes. Pages under
  `frontend/admin/src/app/money/accounts/bank/`.
- No hardcoded data in the admin; honest empty states.
- Tests: written alongside each task; the reviewer runs them through IntelliJ MCP. Implementers never run anything.

## Review Focus

1. Bank exports with title/summary rows above the header and a "Closing balance" footer → header row index from
   the mapping; rows without a parseable date are skipped, not fatal. Test in Task 2.
2. Amounts written as `1,23,456.78`, with `Cr`/`Dr` suffixes, or blank vs `0.00` in the unused column → parsed
   correctly; a row with both debit and credit > 0 is rejected into the skipped list. Test in Task 2.
3. Re-uploading an overlapping statement (same lines, two identical ₹100 UPI credits on one day) → the identical
   pair stays two lines (running balance differs), the overlap is dropped. Test in Task 3.
4. A reversed entry (or a reversal) must never be a match candidate. Test in Task 4.
5. Undo in a locked period → 409 and nothing changes (line keeps its status). Test in Task 4.

---

### Task 1: Migration, dependencies, accounts

**Files:**
- Create: `backend/src/main/resources/db/migration/V35__bank_reconciliation.sql`
- Modify: `backend/pom.xml` (add `org.apache.commons:commons-csv` 1.12.0 and `org.dhatim:fastexcel-reader` 0.18.4)
- Test: `backend/src/test/java/com/vaabhi/accounts/BankSchemaIT.java` (extends `BooksFixtures`, copy the setup of
  `LedgerSchemaIT`)

**Interfaces:**
- Produces: tables `import_profile`, `bank_statement`, `bank_line`, `razorpay_settlement`, `cod_remittance`,
  `cod_remittance_line` exactly as in the spec's Data section; the three accounts; widened CHECKs on
  `journal_entry.source_type` / `source_step` (V32 declares them inline, so drop them by their Postgres default
  names `journal_entry_source_type_check` and `journal_entry_source_step_check` and re-add with the old values plus
  the new ones; if V33 already widened them, start from V33's list).
- `bank_line.status` CHECK in (`UNMATCHED`,`MATCHED`,`POSTED`,`IGNORED`); `bank_line` adds `ignore_reason
  VARCHAR(200)`; unique index `(account_id, dedupe_key)`; index `(statement_id, line_date)`.
- `razorpay_settlement.journal_entry_id` and `cod_remittance.journal_entry_id` are NOT needed (the sweeper's
  `journal_entry_source` index links them); leave them out. `razorpay_settlement` keeps `id VARCHAR(40)` as the
  PK and adds `seq BIGSERIAL NOT NULL UNIQUE` (journal `source_id` is a number). `cod_remittance.id` is
  `BIGSERIAL`. `bank_line.journal_entry_id` / `difference_entry_id` reference `journal_entry(id)`.
- Attach the V32 audit trigger function to `bank_line`, `cod_remittance`, `razorpay_settlement`.

- [ ] Step 1: Write `BankSchemaIT`: `newAccountsSeeded()` asserts codes 5070/5080/5095 with the three system keys;
  `journalAcceptsNewSourceTypes()` inserts a `BANK_LINE`/`POST` entry row and expects no exception;
  `bankLineRejectsBothSides()` inserts a line with debit 10 and credit 10 and expects `DataIntegrityViolationException`;
  `dedupeKeyUniquePerAccount()` inserts two lines with the same key and expects a violation.
- [ ] Step 2: Write the migration and pom changes.
- [ ] Step 3: Reviewer runs `BankSchemaIT` → PASS.
- [ ] Step 4: Commit `feat(accounts): bank reconciliation schema`.

### Task 2: Tabular file reader and column mapping

**Files:**
- Create: `backend/src/main/java/com/vaabhi/accounts/TabularFile.java` — reads CSV or XLSX into rows of strings.
- Create: `backend/src/main/java/com/vaabhi/accounts/ColumnMapping.java` — record.
- Create: `backend/src/main/java/com/vaabhi/accounts/AmountStyle.java` — enum `DEBIT_CREDIT`, `SIGNED`.
- Create: `backend/src/main/java/com/vaabhi/accounts/BankFileParser.java`
- Create: `backend/src/main/java/com/vaabhi/accounts/ParsedBankLine.java`, `SkippedFileRow.java`, `ParsedBankFile.java`
- Test: `backend/src/test/java/com/vaabhi/accounts/BankFileParserTest.java` (plain JUnit, no Spring)

**Interfaces:**
- `final class TabularFile { static List<List<String>> read(String fileName, InputStream in) }` — `.csv` via
  commons-csv (auto-detect `,` vs `;` vs tab from the first non-empty line; strip a UTF-8 BOM), `.xlsx` via
  fastexcel-reader (`.xls` → `IllegalArgumentException("Save the file as .xlsx or .csv")`; first sheet; dates as `yyyy-MM-dd` text, numbers as plain text without exponent).
  Other extensions → `IllegalArgumentException("Upload a CSV or XLSX file")`.
- `record ColumnMapping(int headerRow, Map<String, Integer> columns, String dateFormat, AmountStyle amountStyle)` —
  `headerRow` 0-based; keys for BANK: `date`, `description`, `reference`, `debit`, `credit`, `amount`, `balance`;
  for COD: `remittanceRef`, `remittedOn`, `utr`, `awb`, `orderNumber`, `cod`, `deduction`, `net`.
- `record ParsedBankLine(int rowNumber, LocalDate date, String description, @Nullable String reference,
  BigDecimal debit, BigDecimal credit, @Nullable BigDecimal balance)`
- `record SkippedFileRow(int rowNumber, String reason)`
- `record ParsedBankFile(List<ParsedBankLine> lines, List<SkippedFileRow> skipped)`
- `final class BankFileParser { static ParsedBankFile parse(List<List<String>> rows, ColumnMapping m) }`
- `static BigDecimal parseAmount(@Nullable String raw)` (package-private, in `BankFileParser`): strips `₹`, `,`,
  spaces; `Cr`/`CR` suffix → positive credit marker handled by caller; `(123.45)` → −123.45; blank → `Rupees.ZERO`.
- `static @Nullable LocalDate parseDate(String raw, String pattern)` — tries the pattern, then `yyyy-MM-dd`;
  null when neither parses.

- [ ] Step 1: Write `BankFileParserTest`:
  - `readsCsvWithTitleRowsAboveHeader()` — rows: `["HDFC BANK"]`, `["Statement for ..."]`,
    header, two data rows, `["Closing balance","","","","5,000.00"]`; mapping headerRow 2 → 2 lines, footer skipped
    with reason containing `date`.
  - `parsesIndianGrouping()` — `"1,23,456.78"` → `123456.78`.
  - `parsesSignedStyle()` — amount `-250.00` → debit 250, credit 0; `"1,000.00 Cr"` → credit 1000.
  - `blankAndZeroUnusedColumnBothFine()` — debit `""` credit `"500"`, and debit `"0.00"` credit `"500"` → credit 500.
  - `rowWithBothSidesSkipped()` — debit 10, credit 10 → in `skipped`, reason `both debit and credit`.
  - `parsesDdMmmYyyy()` — `"05-Nov-2026"` with pattern `dd-MMM-yyyy` → 2026-11-05 (Locale.ENGLISH).
  - `readsXlsx()` — write a 2-row XLSX with fastexcel (writer is test-scope: add `org.dhatim:fastexcel` 0.18.4 test
    dependency) to a byte array, read via `TabularFile.read("s.xlsx", ...)` → same cells.
- [ ] Step 2: Implement the classes.
- [ ] Step 3: Reviewer runs `BankFileParserTest` → PASS.
- [ ] Step 4: Commit `feat(accounts): bank and remittance file parser`.

### Task 3: Statement import (preview, profile, dedupe)

**Files:**
- Create: `backend/src/main/java/com/vaabhi/accounts/ImportProfileService.java`
- Create: `backend/src/main/java/com/vaabhi/accounts/BankImportService.java`
- Create: `backend/src/main/java/com/vaabhi/accounts/BankController.java`
- Create records: `FilePreview.java` (`List<List<String>> rows, @Nullable ColumnMapping saved`),
  `BankImportSummary.java` (`long statementId, int added, int duplicates, int autoMatched, List<SkippedFileRow> skipped`),
  `BankStatementSummary.java` (`long id, String fileName, LocalDate periodFrom, LocalDate periodTo,
  @Nullable BigDecimal openingRupees, @Nullable BigDecimal closingRupees, int lineCount, int unmatchedCount,
  Instant uploadedAt`), `BankLineView.java` (`long id, LocalDate date, String description, @Nullable String reference,
  BigDecimal debitRupees, BigDecimal creditRupees, @Nullable BigDecimal balanceRupees, String status,
  @Nullable Long journalEntryId, @Nullable String journalNumber, @Nullable String ignoreReason`),
  `BankStatementDetail.java` (`BankStatementSummary statement, List<BankLineView> lines`)
- Test: `backend/src/test/java/com/vaabhi/accounts/BankImportServiceIT.java`

**Interfaces:**
- Consumes: `TabularFile.read`, `BankFileParser.parse`, `ColumnMapping` (Task 2); `ChartService.idOf("BANK")`.
- Produces:
  - `ImportProfileService`: `@Nullable ColumnMapping latest(String kind)`, `void save(String kind, ColumnMapping m)`
    (inserts a new row; JSONB via Jackson).
  - `BankImportService`:
    - `FilePreview preview(String fileName, byte[] bytes)` — first 20 rows + `latest("BANK")`.
    - `BankImportSummary importStatement(String fileName, byte[] bytes, ColumnMapping m, @Nullable BigDecimal
      opening, @Nullable BigDecimal closing, String actor)` — `@Transactional`; saves the profile; computes
      `dedupeKey` = sha256 hex of `date|debit|credit|reference|balance|description` (plain-string forms, null → "");
      skips keys already present for the account; opening/closing defaulted from balances per spec when not given;
      period = min/max line date; then calls `BankMatcher.autoMatch(statementId)` (Task 4 — until Task 4 lands, the
      implementer of this task creates `BankMatcher` with `int autoMatch(long statementId)` returning 0).
    - `List<BankStatementSummary> list()`, `BankStatementDetail detail(long id, @Nullable String status)`.
  - `BankController` (`@RequestMapping("/api/v1/admin/accounts/bank")`): `POST /statements/preview` (multipart
    `file`), `POST /statements` (multipart `file` + part `mapping` JSON `BankImportForm(ColumnMapping mapping,
    @Nullable BigDecimal openingRupees, @Nullable BigDecimal closingRupees)`), `GET /statements`,
    `GET /statements/{id}?status=`. Actor from the authenticated principal, same as `AccountsController`.
  - `IllegalArgumentException` from parsing → 400 via the existing `ApiExceptionHandler` mapping (check it handles
    `IllegalArgumentException`; if not, throw `ResponseStatusException(BAD_REQUEST, msg)`).
- Files over 5 MB → 400 `"File is larger than 5 MB"`.

- [ ] Step 1: Write `BankImportServiceIT`:
  - `importsAndSavesProfile()` — CSV with 3 lines → added 3, `latest("BANK")` equals the mapping.
  - `overlappingUploadDropsDuplicates()` — upload lines A,B,C then B,C,D → second import added 1, duplicates 2.
  - `identicalPairWithDifferentBalancesKept()` — two ₹100 credits same day, balances 1100 and 1200 → added 2.
  - `openingAndClosingFromBalances()` — first line credit 100 balance 1100 → opening 1000; last balance → closing.
  - `rejectsUnknownExtension()` — `s.pdf` → `IllegalArgumentException`.
- [ ] Step 2: Implement.
- [ ] Step 3: Reviewer runs `BankImportServiceIT` → PASS.
- [ ] Step 4: Commit `feat(accounts): bank statement import`.

### Task 4: Matching and line actions

**Files:**
- Create/replace: `backend/src/main/java/com/vaabhi/accounts/BankMatcher.java`
- Create: `backend/src/main/java/com/vaabhi/accounts/BankLineService.java`
- Create records: `MatchCandidate.java` (`long journalEntryId, String number, LocalDate date, String narration,
  BigDecimal amountRupees`), `BankMatchForm.java` (`long journalEntryId, @Nullable Long differenceAccountId`),
  `BankPostForm.java` (`long accountId, @Nullable String narration`), `BankIgnoreForm.java` (`String reason`)
- Modify: `BankController.java` (add line endpoints)
- Test: `backend/src/test/java/com/vaabhi/accounts/BankMatchingIT.java`

**Interfaces:**
- Consumes: `JournalService.post(NewEntry, actor)`, `JournalService.reverse(id, actor)`, `ChartService.idOf`.
- Candidate SQL (one query, used by both auto-match and the candidates endpoint): journal lines on the BANK account
  where bank credit ↔ line debit (bank debit ↔ line credit), entry not a reversal (`reverses_id IS NULL`), entry not
  reversed (no other entry with `reverses_id = e.id`), entry id not in `bank_line.journal_entry_id` and not in
  `bank_line.difference_entry_id`, source_type not `BANK_LINE` with step `MATCH`.
- `BankMatcher`:
  - `int autoMatch(long statementId)` — per `UNMATCHED` line: if `reference` non-blank and equals a
    `razorpay_settlement.utr` or `cod_remittance.utr` whose entry exists (via `journal_entry` source link) and is a
    candidate → match it; else candidates with equal amount and `entry_date` within ±3 days; exactly one → match.
    Sets `status='MATCHED'`, `journal_entry_id`, `matched_by='auto'`, `matched_at=now()`. Returns count.
  - `List<MatchCandidate> candidates(long lineId)` — same filter, any amount, ±15 days, ordered by
    |amount diff| then |date diff|, limit 20.
- `BankLineService` (each method `@Transactional`, returns `BankLineView`):
  - `match(long lineId, BankMatchForm f, String actor)` — line must be `UNMATCHED`. If the entry's bank amount ≠ line
    amount: `differenceAccountId` required (else 400 `"Pick an account for the difference"`); posts
    `BANK_LINE`/`MATCH` (sourceId = lineId) dated `line_date`: if bank got less than the ledger says (credit side) →
    Dr difference account, Cr Bank; if bank got more → Dr Bank, Cr difference account; mirrored for debits so Bank
    ends equal to the statement. Stores `difference_entry_id`.
  - `post(long lineId, BankPostForm f, String actor)` — posts `BANK_LINE`/`POST` per spec; `status='POSTED'`.
    Narration default = description truncated to 200. Rejects the BANK account itself (400).
  - `ignore(long lineId, BankIgnoreForm f, String actor)` — reason required (≤200); `status='IGNORED'`.
  - `undo(long lineId, String actor)` — MATCHED: reverse difference entry if any, clear links; POSTED: reverse
    entry; IGNORED: clear reason. Back to `UNMATCHED`. Reversal errors (locked period) propagate unchanged (409) and
    the transaction rolls back.
  - Period-lock / before-start errors from `JournalService.post` propagate as today (409).
- Controller: `GET /lines/{id}/candidates`, `POST /lines/{id}/match`, `/post`, `/ignore`, `/undo`.

- [ ] Step 1: Write `BankMatchingIT` (use `BooksFixtures` helpers to set books start and post a shop UPI receipt to
  Bank):
  - `autoMatchesExactAmountWithinThreeDays()` — receipt ₹500 on day D, bank credit ₹500 on D+2 → MATCHED.
  - `twoCandidatesStayUnmatched()` — two ₹500 receipts, one line → UNMATCHED, candidates size 2.
  - `reversedEntryIsNeverACandidate()` — post receipt, reverse it, bank line ₹500 → UNMATCHED, candidates empty.
  - `matchWithDifferencePostsFee()` — ledger ₹1,000, line ₹980, difference account 5070 → 5070 balance 20, Bank
    balance moved by 980 net.
  - `postAsBankCharges()` — bank debit ₹59 → POSTED, 5095 debit 59, Bank credit 59.
  - `undoPostReverses()` — then undo → UNMATCHED, 5095 balance 0.
  - `undoInLockedPeriodIs409AndKeepsStatus()` — lock the month after posting, undo → exception mapped to 409, line
    still POSTED.
  - `ignoreNeedsReason()` — blank reason → 400.
- [ ] Step 2: Implement.
- [ ] Step 3: Reviewer runs `BankMatchingIT` and `BankImportServiceIT` → PASS.
- [ ] Step 4: Commit `feat(accounts): bank line matching and actions`.

### Task 5: Razorpay settlements

**Files:**
- Modify: `backend/src/main/java/com/vaabhi/checkout/RazorpayClient.java` — add
  `List<RazorpaySettlementData> settlements(long fromEpoch, long toEpoch, int skip)` calling
  `GET /v1/settlements?from=&to=&count=100&skip=` with the existing `call(...)` helper.
- Create: `backend/src/main/java/com/vaabhi/checkout/RazorpaySettlementData.java` — record `(String id, String utr,
  long amountPaise, long feesPaise, long taxPaise, String status, long createdAt)`.
- Create: `backend/src/main/java/com/vaabhi/checkout/RazorpaySettlementSync.java` — `@Component`, cron
  `0 0 6 * * *` IST, `public RazorpaySyncOutcome syncNow()`.
- Create: `backend/src/main/java/com/vaabhi/checkout/RazorpaySyncOutcome.java` — record `(boolean configured,
  int added, @Nullable String error)`.
- Create: `backend/src/main/java/com/vaabhi/checkout/AdminRazorpaySettlementController.java` —
  `POST /api/v1/admin/accounts/bank/razorpay/sync`.
- Create: `backend/src/main/java/com/vaabhi/accounts/rules/RazorpaySettlementRule.java`
- Create: `backend/src/main/java/com/vaabhi/accounts/RazorpaySettlementView.java` + `GET /razorpay/settlements` in
  `BankController` (reads the table via SQL; includes `@Nullable String journalNumber`).
- Test: `backend/src/test/java/com/vaabhi/checkout/RazorpaySettlementSyncIT.java`,
  extend `backend/src/test/java/com/vaabhi/accounts/PostingRulesIT.java` with `razorpaySettlementPosts()`.

**Interfaces:**
- Sync: only `status = "processed"`; window from latest `settled_on` (minus 1 day) or 90 days back if none, to now;
  pages until a page has < 100 items; inserts with `ON CONFLICT (id) DO NOTHING`. Mapping per row:
  `net = amount`, `tax = tax`, `fee = fees − tax`, `gross = amount + fees`, `settled_on` = IST date of `created_at`.
  Razorpay not configured (same check the client already uses) → `RazorpaySyncOutcome(false, 0, null)`, no call.
  Client error → `(true, 0, message)`, already-inserted rows from earlier pages stay.
- `RazorpaySettlementRule implements PostingRule`: `sourceType()="RAZORPAY_SETTLEMENT"`, `step()="SETTLE"`,
  `order()=40`, `pending` over `razorpay_settlement.seq` by `settled_on >= :start` (source_id is the `seq`
  column from Task 1), `build` → Dr BANK net, Dr EXPENSE_GATEWAY_FEES fee, Dr INPUT_GST tax, Cr RAZORPAY_CLEARING
  gross, zero lines dropped, narration `"Razorpay settlement " + utr`. A `@Component` in `accounts/rules` like the
  other rules; `JournalSweeper` picks up every `PostingRule` bean.
- Checkout must not depend on accounts, so sync does not post. The admin "Sync now" button (Task 9) calls
  `POST /razorpay/sync` and then the existing `POST /api/v1/admin/accounts/sweep`.

- [ ] Step 1: Write tests: `RazorpaySettlementSyncIT` with `@MockitoBean RazorpayClient` returning two processed and
  one `created` settlement → added 2, rerun → added 0; `notConfiguredDoesNothing()`. `razorpaySettlementPosts()` —
  insert a settlement (amount 9764, fees 236, tax 36 rupees) and sweep → BANK +9764, 5070 +200, INPUT_GST +36,
  RAZORPAY_CLEARING −10000.
- [ ] Step 2: Implement.
- [ ] Step 3: Reviewer runs both → PASS; also `ModularityTests`.
- [ ] Step 4: Commit `feat(books): razorpay settlement sync and posting`.

### Task 6: COD remittance import and posting

**Files:**
- Create: `backend/src/main/java/com/vaabhi/accounts/CodRemittanceService.java`
- Create: `backend/src/main/java/com/vaabhi/accounts/rules/CodRemittanceRule.java`
- Create records: `CodImportSummary.java` (`int added, int duplicates, List<String> unknownAwbs,
  List<SkippedFileRow> skipped`), `CodRemittanceView.java` (`long id, String remittanceRef, @Nullable String utr,
  LocalDate remittedOn, BigDecimal codRupees, BigDecimal deductionRupees, BigDecimal netRupees, int awbCount,
  @Nullable String journalNumber`)
- Modify: `BankController.java` — `POST /cod-remittances/preview`, `POST /cod-remittances` (multipart `file` +
  part `mapping` `ColumnMapping`), `GET /cod-remittances`.
- Test: `backend/src/test/java/com/vaabhi/accounts/CodRemittanceIT.java`

**Interfaces:**
- Consumes: `TabularFile`, `BankFileParser.parseAmount/parseDate`, `ImportProfileService` with kind
  `COD_REMITTANCE`.
- `CodRemittanceService`: `FilePreview preview(String fileName, byte[] bytes)`,
  `CodImportSummary importReport(String fileName, byte[] bytes, ColumnMapping m, String actor)`,
  `List<CodRemittanceView> list()`. Grouping per spec flow 3 (rows grouped by `remittanceRef`; deduction from the
  column when mapped, else `cod − net` when `net` mapped, else 0). Required mapped fields: `remittanceRef`,
  `remittedOn`, `awb`, `cod` (missing → 400 naming the field). AWB lookup: `SELECT id FROM web_order WHERE
  tracking_number = :awb`.
- `CodRemittanceRule`: `sourceType()="COD_REMITTANCE"`, `step()="SETTLE"`, `order()=41`, pending by `remitted_on`,
  build → Dr BANK net, Dr EXPENSE_COURIER deduction, Cr COD_CLEARING cod, narration
  `"Shiprocket COD remittance " + remittanceRef`.

- [ ] Step 1: Write `CodRemittanceIT`: `groupsRowsByRemittance()` (3 rows, 2 refs → added 2, totals correct);
  `deductionFromNetWhenNoDeductionColumn()` (cod 2000, net 1900 → deduction 100); `rerunSkipsKnownRefs()`;
  `unknownAwbStillImportsAndIsListed()`; `postsOnSweep()` (BANK +1900, 5080 +100, COD_CLEARING −2000);
  `missingRequiredFieldIs400()`.
- [ ] Step 2: Implement.
- [ ] Step 3: Reviewer runs `CodRemittanceIT` → PASS.
- [ ] Step 4: Commit `feat(accounts): shiprocket cod remittance import`.

### Task 7: Reconciliation report

**Files:**
- Create: `backend/src/main/java/com/vaabhi/accounts/BankReconciliationService.java`
- Create records: `BankReconciliation.java` (`BankStatementSummary statement, @Nullable BigDecimal
  statementClosingRupees, BigDecimal ledgerBalanceRupees, @Nullable BigDecimal differenceRupees,
  List<BankLineView> unmatchedLines, List<LedgerOnlyMovement> ledgerOnly, BigDecimal razorpayClearingRupees,
  BigDecimal codClearingRupees, @Nullable LocalDate oldestUnsettledWebPayment, boolean openingMismatch)`,
  `LedgerOnlyMovement.java` (`long journalEntryId, String number, LocalDate date, String narration,
  BigDecimal debitRupees, BigDecimal creditRupees`)
- Modify: `BankController.java` — `GET /statements/{id}/report`, `GET /statements/{id}/report.csv`
  (`text/csv`, filename `bank-reconciliation-<periodTo>.csv`).
- Test: `backend/src/test/java/com/vaabhi/accounts/BankReconciliationIT.java`

**Interfaces:**
- Ledger balance = BANK balance at `period_to` (reuse `JournalService.ledger` or trial balance logic; do not
  duplicate the sum SQL if a method exists). `ledgerOnly` = BANK journal lines dated in the statement period, entry
  not reversed/reversal, not linked to any bank line (journal or difference). `openingMismatch` = difference ≠ 0
  and both lists empty. Clearing balances at `period_to`; oldest unsettled web payment = oldest `paid_at` IST date of
  is the earliest `entry_date` of a RAZORPAY_CLEARING debit line after the last RAZORPAY_SETTLEMENT entry date
  (null when none).
- CSV columns: `Section,Date,Reference,Description,Debit,Credit`, then a summary block with the four balances.

- [ ] Step 1: Write `BankReconciliationIT`: `zeroDifferenceWhenAllMatched()`; `unmatchedLineExplainsDifference()`;
  `ledgerOnlyListsUnlinkedReceipt()`; `csvHasHeaderAndSummary()`.
- [ ] Step 2: Implement.
- [ ] Step 3: Reviewer runs `BankReconciliationIT` → PASS.
- [ ] Step 4: Commit `feat(accounts): bank reconciliation report`.

### Task 8: API client and admin data layer (reviewer + implementer)

**Files:**
- Regenerate: `frontend/packages/api-client/src/schema.d.ts`, `openapi.json` (reviewer, with the backend running:
  `pnpm generate:api` from `frontend/`).
- Create: `frontend/admin/src/lib/bank.ts` — typed wrappers in the style of `frontend/admin/src/lib/accounts.ts`.
- Modify: `frontend/admin/src/lib/nav.ts` — add "Bank" under Money → Accounts.

**Interfaces:**
- `bank.ts` exports: `previewStatement(file)`, `importStatement(file, form)`, `listStatements()`,
  `getStatement(id, status?)`, `getReconciliation(id)`, `reconciliationCsvUrl(id)`, `lineCandidates(id)`,
  `matchLine(id, form)`, `postLine(id, form)`, `ignoreLine(id, reason)`, `undoLine(id)`, `syncRazorpay()`,
  `listSettlements()`, `previewRemittance(file)`, `importRemittance(file, mapping)`, `listRemittances()`.
  Multipart helpers reuse whatever `api.ts` uses for media uploads (check `photo-upload.ts`).
- Field key lists: `BANK_FIELDS = ["date","description","reference","debit","credit","amount","balance"]`,
  `COD_FIELDS = ["remittanceRef","remittedOn","utr","awb","orderNumber","cod","deduction","net"]` with labels.

- [ ] Step 1: Reviewer regenerates the client.
- [ ] Step 2: Implementer writes `bank.ts` and the nav entry.
- [ ] Step 3: Reviewer runs `pnpm --filter admin typecheck` and `lint` → clean.
- [ ] Step 4: Commit `feat(admin): bank api client`.

### Task 9: Admin screens

**Files:**
- Create: `frontend/admin/src/app/money/accounts/bank/page.tsx` — tabs Statements / Razorpay / COD remittances.
- Create: `frontend/admin/src/app/money/accounts/bank/upload/page.tsx` — `?kind=bank|cod` wizard.
- Create: `frontend/admin/src/app/money/accounts/bank/statement/page.tsx` — `?id=`.
- Create: `frontend/admin/src/app/money/accounts/bank/report/page.tsx` — `?id=`.
- Create: `frontend/admin/src/components/column-mapper.tsx` — preview table with a field `<select>` above each
  column, header-row picker, date format input (default `dd/MM/yyyy`), amount style toggle (BANK only).

**Interfaces:**
- Consumes `bank.ts` (Task 8), `ui.tsx` components, the sheet pattern used in `money/accounts/manual/page.tsx`.
- Statement page: status filter chips (All, Unmatched, Matched, Posted, Ignored); row click on UNMATCHED opens a
  sheet with candidates (radio), difference account select (shown only when amounts differ), "Post as" account
  select + narration, "Ignore" with reason; MATCHED/POSTED show the journal number linking to
  `/money/accounts/journal?id=` and an Undo button. Errors shown inline from the API message.
- Razorpay tab: "Sync now" → shows `added` or "Razorpay not configured" or the error.
- Empty states: "No statements yet. Upload your bank's CSV or Excel export." / "No settlements synced yet." /
  "No remittance reports yet."

- [ ] Step 1: Implement the pages and component.
- [ ] Step 2: Reviewer runs `pnpm --filter admin typecheck` and `lint` → clean.
- [ ] Step 3: Commit `feat(admin): bank reconciliation screens`.

## Owner checks by hand

As in the spec's "Owner checks by hand" section.
