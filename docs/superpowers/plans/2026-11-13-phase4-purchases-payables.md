# Books Phase 4: Purchases, Payables, TDS, Fixed Assets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Supplier bills (typed or imported from Shiprocket) with input GST and TDS, supplier payments, a creditors
view, stock bought on bills landing in inventory, a fixed-asset register with monthly depreciation, TDS challans, and
an input-GST-per-month report, all posted to the ledger by the existing sweeper.

**Architecture:** New tables in V36. A new `purchases` module owns suppliers' bills, payments and the Shiprocket
import; it calls `party`, `inventory`, `settings`, `shared.storage` and the public file-parsing classes of
`accounts`. `accounts` gets new `PostingRule`s that read `purchases` tables by SQL (as Phase 2/3 rules do), plus TDS,
assets, depreciation and the input GST report. Nothing in `purchases` posts to the journal.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Modulith, JdbcClient, Flyway, Postgres; Next.js 16 static-export
admin, Tailwind 4.

**Spec:** `docs/superpowers/specs/2026-11-13-phase4-purchases-payables-design.md`

**Deviations from spec (wiring only):**
- Depreciation: instead of relaxing the V32 unique index, `asset_depreciation` rows (`id BIGSERIAL`, unique
  `(asset_id, month)`) are created ahead by `DepreciationScheduler` and posted by a normal rule with
  `source_type = 'ASSET_DEPRECIATION'`, `source_id = asset_depreciation.id`. Disposal uses `source_type = 'ASSET'`,
  `source_id = fixed_asset.id`, step `DISPOSAL`. The V32 index stays as it is. No `journal_entry_id` column on
  `asset_depreciation`.
- The "TabularFiles facade" is the existing public `TabularFile`, `ColumnMapping`, `FilePreview`,
  `ImportProfileService` plus `BankFileParser.parseAmount/parseDate` made public. No new facade class.
- `PostingRule` gains `default void posted(long sourceId, long entryId) {}`, called by the sweeper in the same
  transaction after `journal.post`; the supplier payment rule uses it to mark a bank line matched.
- TDS suggestion: pre-filled only when the supplier's section threshold is crossed (else 0), and the save response
  warns when crossed and TDS is 0.

## Global Constraints

- Migration `V36__purchases_payables.sql`; never edit V1–V35.
- Money: `NUMERIC(12,2)` in SQL, `BigDecimal` via `Rupees.of(...)`; rounding HALF_UP to paise; never double.
- New accounts (code, name, type, system_key): `5100 Purchases EXPENSE PURCHASES`, `5200 Depreciation EXPENSE
  DEPRECIATION`, `4900 Gain on asset disposal INCOME ASSET_GAIN`, `5210 Loss on asset disposal EXPENSE ASSET_LOSS`,
  `1500 Plant & machinery ASSET ASSET_PLANT`, `1501 Acc. depreciation – plant & machinery ASSET ACCDEP_PLANT`,
  `1510 Furniture & fittings ASSET_FURNITURE`/`1511 ACCDEP_FURNITURE`, `1520 Office equipment ASSET_OFFICE`/`1521
  ACCDEP_OFFICE`, `1530 Computers ASSET_COMPUTERS`/`1531 ACCDEP_COMPUTERS`.
- Asset classes seeded: Plant & machinery 15 y, Furniture & fittings 10 y, Office equipment 5 y, Computers 3 y,
  residual 5%.
- TDS sections seeded (placeholders for the CA; no-PAN rate 20%): `194C-I` contractor individual/HUF 1%, `194C-O`
  contractor others 2%, `194I-B` rent land/building 10%, `194I-A` rent plant/machinery 2%, `194J` professional fees
  10%, `194H` commission 2%. Thresholds: 194C single 30000 / year 100000; 194I year 240000; 194J year 30000; 194H
  year 15000.
- New `source_type` values `SUPPLIER_BILL`, `SUPPLIER_PAYMENT`, `TDS_CHALLAN`, `ASSET`, `ASSET_DEPRECIATION`; new
  `source_step` values `PAYMENT`, `DEPRECIATION`, `DISPOSAL`. `import_profile.kind` gains `SHIPROCKET_INVOICE`.
- IGST when the bill's `supply_state_code` ≠ the company state code (from `IndianStates.code` of billing company
  state, else shop profile state, as `money/InvoiceParties.seller` does); else CGST = SGST = half, SGST takes the
  odd paisa.
- REST: `purchases` under `/api/v1/admin/money/purchases` (MONEY area, read+write; no security change). `accounts`
  additions under `/api/v1/admin/accounts/...` (GET = MONEY, writes = OWNER; no security change).
- API record names globally unique (springdoc): no `Row`, `Line`, `Item`, `Result`, `Summary` alone.
- Dates IST (`Asia/Kolkata`). Financial year April–March.
- Admin is a static export: `?id=` query params, no dynamic routes. Purchases pages in
  `frontend/admin/src/app/money/purchases/`, books pages in `frontend/admin/src/app/money/accounts/`.
- No hardcoded data in the admin; honest empty states.
- Per CLAUDE.md, tests are written with each task but **not run**. Verification per backend task is a compile:
  scratch worktree `./mvnw -q -o compile test-compile -DskipTests`. Frontend: `pnpm typecheck` and `pnpm lint`
  from `frontend/` with `--filter admin`.
- Stage `backend/src` and `docs`, never `git add backend` (untracked `hs_err_pid*.log`).

## Review Focus

1. Editing an unposted bill that has a STOCK line: stock must end where it would be had the bill been typed right
   the first time (OUT old qty, IN new qty), and on cancel the stock goes back. Test in Task 3.
2. Payment allocated across two bills where the second is only part-paid; then a second payment fills the rest; a
   third allocation over the bill's payable is rejected with 400 and nothing saved. Test in Task 4.
3. Depreciation when the books start date is after put-to-use (opening asset): the first charge starts at the start
   date, counts `opening_accdep_rupees` toward the cap, and never goes negative when opening accdep already reaches
   cost − residual. Test in Task 7.
4. A Shiprocket CSV with one row per AWB (many rows per invoice number) and an invoice already imported → grouped
   into one bill per invoice, the repeat skipped and counted. Test in Task 8.
5. Supplier with no GSTIN state: `supply_state_code` blank on a bill → treated as same state (CGST/SGST), not an
   error. Test in Task 3.

---

### Task 1: Migration V36

**Files:**
- Create: `backend/src/main/resources/db/migration/V36__purchases_payables.sql`
- Test: `backend/src/test/java/com/vaabhi/purchases/PurchasesSchemaIT.java` (extends
  `com.vaabhi.accounts.BooksFixtures`; make `BooksFixtures` public with a public no-arg constructor if it isn't)

**Interfaces:**
- Produces the tables, columns, CHECKs and seeds exactly as the spec's Data section, with these specifics:
  - `supplier_bill.status` CHECK (`OPEN`,`CANCELLED`); `source` CHECK (`MANUAL`,`SHIPROCKET`); unique index
    `supplier_bill_no ON supplier_bill (party_id, lower(bill_no))`; CHECK `payable_rupees = total_rupees -
    tds_rupees`.
  - `supplier_bill_line.kind` CHECK (`EXPENSE`,`STOCK`,`ASSET`) plus CHECKs: STOCK ⇒ `stock_item_id` and
    `quantity > 0` not null; ASSET ⇒ `asset_class_id` not null; `total_rupees = taxable + cgst + sgst + igst`.
  - `supplier_payment.mode` CHECK (`CASH`,`UPI`,`CARD`,`BANK`); `bank_line_id` unique, nullable.
  - `asset_depreciation`: `id BIGSERIAL PK`, `asset_id`, `month DATE`, `amount_rupees > 0`, unique
    (`asset_id`, `month`).
  - `fixed_asset.bill_line_id` unique, `ON DELETE` restrict.
  - Widen `journal_entry_source_type_check`, `journal_entry_source_step_check` (start from V35's lists) and
    `import_profile_kind_check` (find V35's constraint name; drop and re-add).
  - Triggers (pattern of `expense_protect` in V33), each raising `'This <thing> is in the books; …'`:
    `supplier_bill_protect` (UPDATE of any column other than `status`, `cancelled_on`, `cancel_reason`,
    `attachment_key`, `notes`, or DELETE, when an `SUPPLIER_BILL/ISSUE` entry exists);
    `supplier_bill_line_protect` (INSERT/UPDATE/DELETE when the bill's ISSUE exists);
    `supplier_payment_protect` and `supplier_payment_allocation_protect` (any change when the payment's
    `SUPPLIER_PAYMENT/PAYMENT` exists); `tds_challan_protect` (UPDATE/DELETE when posted);
    `fixed_asset_protect` (UPDATE of `cost_rupees`, `asset_class_id`, `put_to_use_on`,
    `opening_accdep_rupees` when any `asset_depreciation` row of it is posted; DELETE when any is posted).
  - V32 audit trigger on every new table. `party.tds_section_id` FK.
  - Books settings: no SQL needed if settings are JSON; else add the key with null.

- [ ] Step 1: Write `PurchasesSchemaIT`: `accountsAndSeedsPresent()` (codes 5100, 5200, 4900, 5210, 1500–1531; 4
  asset classes; 6 TDS sections); `billPayableMustEqualTotalLessTds()` expects `DataIntegrityViolationException`;
  `stockLineNeedsItemAndQuantity()`; `postedBillRejectsAmountChange()` (insert a bill and a
  `SUPPLIER_BILL/ISSUE` journal row with two balancing lines, then UPDATE `total_rupees` → exception; UPDATE
  `notes` → ok); `depreciationMonthUniquePerAsset()`.
- [ ] Step 2: Write the migration.
- [ ] Step 3: Compile check (scratch worktree). Do not run the IT.
- [ ] Step 4: Commit `feat(purchases): schema for bills, payments, TDS and assets`.

### Task 2: `purchases` module and suppliers

**Files:**
- Create: `backend/src/main/java/com/vaabhi/purchases/package-info.java` (`@ApplicationModule(displayName =
  "Purchases")`, copy style of `accounts/package-info.java`)
- Create: `purchases/SupplierService.java`, `purchases/SupplierForm.java`, `purchases/SupplierBalance.java`,
  `purchases/SupplierStatement.java`, `purchases/PurchasesController.java`, `purchases/CompanyState.java`
- Modify: `backend/src/test/java/com/vaabhi/ModularityTests.java` only if it lists modules explicitly
- Test: `backend/src/test/java/com/vaabhi/purchases/SupplierServiceIT.java`

**Interfaces:**
- `record SupplierForm(@NotBlank @Size(max=160) String name, @Nullable String phone, @Nullable @Email String email,
  @Nullable @Pattern(regexp="[0-9A-Z]{15}") String gstin, @Nullable @Pattern(regexp="[A-Z]{5}[0-9]{4}[A-Z]") String
  pan, @Nullable String address, @Nullable String city, @Nullable String state, @Nullable String pinCode,
  @Nullable Long tdsSectionId)`
- `record SupplierBalance(long partyId, String name, @Nullable String gstin, @Nullable String state,
  BigDecimal billedRupees, BigDecimal paidRupees, BigDecimal tdsRupees, BigDecimal balanceRupees,
  @Nullable LocalDate oldestUnpaidDue, boolean overdue)` — billed = Σ `total_rupees` of OPEN bills, tds = Σ their
  `tds_rupees`, paid = Σ payments, balance = Σ `payable_rupees` − paid. `oldestUnpaidDue` = min due date (else bill
  date) among OPEN bills whose allocations < payable. `overdue` = that date < today IST.
- `record SupplierStatement(SupplierBalance balance, @Nullable String pan, @Nullable Long tdsSectionId,
  List<SupplierStatementEntry> entries)` with `record SupplierStatementEntry(LocalDate date, String kind /* BILL |
  PAYMENT */, long id, String reference, BigDecimal billedRupees, BigDecimal paidRupees, BigDecimal
  runningBalanceRupees)` ordered by date then id.
- `SupplierService`: `List<SupplierBalance> list(@Nullable String query)` (parties with `is_supplier`),
  `SupplierStatement statement(long partyId)`, `long create(SupplierForm)`, `void update(long partyId,
  SupplierForm)` — save via `PartyService.save(current, new Details(...), Role.SUPPLIER)` then
  `UPDATE party SET pan = :pan, tds_section_id = :tds WHERE id = :id`.
- `@Component class CompanyState { @Nullable String code() }` — the logic of `InvoiceParties.seller(...)`'s state
  code, using `SettingsService.billing().company().state()` then `shopProfile().state()` and
  `IndianStates.code(...)`.
- Controller endpoints: `GET /suppliers?q=`, `GET /suppliers/{id}`, `POST /suppliers` → `{id}`,
  `PUT /suppliers/{id}`. Actor from the authenticated staff (copy how `MoneyController` gets it).

- [ ] Step 1: Write `SupplierServiceIT`: `createThenListShowsZeroBalance()`; `panAndTdsSectionStored()`;
  `balanceIsPayableMinusPayments()` (insert two bills and one payment by SQL: payable 1000 + 500, paid 600 →
  balance 900, oldest unpaid due = first bill's due date); `cancelledBillsIgnored()`.
- [ ] Step 2: Implement.
- [ ] Step 3: Compile check.
- [ ] Step 4: Commit `feat(purchases): suppliers with balances`.

### Task 3: Supplier bills

**Files:**
- Create: `purchases/BillMath.java` (pure), `purchases/SupplierBillService.java`, `purchases/SupplierBillForm.java`,
  `purchases/SupplierBillLineForm.java`, `purchases/SupplierBillView.java`, `purchases/SupplierBillListEntry.java`,
  `purchases/TdsSuggestion.java`, `purchases/BooksPostedCheck.java`
- Modify: `inventory/InventoryService.java` — add `public void setCost(long itemId, BigDecimal costRupees)`
- Modify: `purchases/PurchasesController.java`
- Test: `backend/src/test/java/com/vaabhi/purchases/BillMathTest.java`, `SupplierBillServiceIT.java`

**Interfaces:**
- `record SupplierBillLineForm(@NotNull Kind kind, @NotBlank @Size(max=200) String description, @Nullable Long
  accountId, @Nullable Long stockItemId, @Nullable BigDecimal quantity, @Nullable Long assetClassId, @Nullable
  @Size(max=8) String hsn, @NotNull @DecimalMin("0.01") BigDecimal taxableRupees, @NotNull @DecimalMin("0")
  @DecimalMax("28") BigDecimal gstRatePercent)` with `enum Kind { EXPENSE, STOCK, ASSET }`. EXPENSE requires
  `accountId` of an active EXPENSE account (checked by SQL on `account`); STOCK requires item + quantity and uses
  the `PURCHASES` account id; ASSET requires a class and uses its `asset_account_id`.
- `record SupplierBillForm(@NotNull Long partyId, @NotBlank @Size(max=40) String billNo, @NotNull LocalDate
  billDate, @Nullable LocalDate dueDate, @Nullable @Size(min=2,max=2) String supplyStateCode, @Nullable Long
  tdsSectionId, @NotNull @DecimalMin("0") BigDecimal tdsRupees, @Nullable @Size(max=300) String notes, @NotEmpty
  @Valid List<SupplierBillLineForm> lines, @Nullable PaidNow paidNow)` with `record PaidNow(@NotNull String mode,
  @Nullable String reference)`.
- `final class BillMath`: `record LineTax(BigDecimal cgst, BigDecimal sgst, BigDecimal igst, BigDecimal total)`;
  `static LineTax tax(BigDecimal taxable, BigDecimal ratePercent, boolean interState)` (tax = taxable × rate / 100
  rounded HALF_UP to paise; intra: `cgst = tax / 2` rounded FLOOR to paise, `sgst = tax − cgst`);
  `static boolean interState(@Nullable String supplyState, @Nullable String companyState)` — false when either is
  blank; `static BigDecimal tds(BigDecimal taxable, BigDecimal ratePercent)`;
  `static LocalDate fyStart(LocalDate d)` (1 April on or before d).
- `record TdsSuggestion(@Nullable Long sectionId, @Nullable String sectionCode, BigDecimal ratePercent,
  BigDecimal suggestedRupees, boolean thresholdCrossed, @Nullable String warning)`;
  `TdsSuggestion suggestTds(long partyId, LocalDate billDate, BigDecimal billTaxable, @Nullable Long excludeBillId)`
  — section = supplier default; rate = `no_pan_rate_percent` when supplier PAN null else `rate_percent`; crossed when
  FY Σ taxable of the supplier's OPEN bills (excluding `excludeBillId`) + this taxable > year threshold, or this
  taxable > single threshold; suggested = crossed ? `BillMath.tds(...)` : 0; warning text "TDS under <code> is due:
  this supplier's bills this year cross ₹<threshold>" when crossed.
- `record SupplierBillView(long id, long partyId, String supplierName, String billNo, LocalDate billDate,
  @Nullable LocalDate dueDate, @Nullable String supplyStateCode, BigDecimal taxableRupees, BigDecimal cgstRupees,
  BigDecimal sgstRupees, BigDecimal igstRupees, BigDecimal totalRupees, @Nullable Long tdsSectionId, BigDecimal
  tdsRupees, BigDecimal payableRupees, BigDecimal paidRupees, String source, String status, boolean posted,
  @Nullable String attachmentKey, @Nullable String notes, List<SupplierBillLineView> lines, @Nullable String
  tdsWarning)`; `record SupplierBillLineView(...)` mirrors the line columns plus `stockItemName`,
  `assetClassName`, `accountName`, `@Nullable Long fixedAssetId`.
- `record SupplierBillListEntry(long id, String supplierName, String billNo, LocalDate billDate, @Nullable
  LocalDate dueDate, BigDecimal totalRupees, BigDecimal payableRupees, BigDecimal paidRupees, String status,
  String source, boolean posted)`.
- `@Component class BooksPostedCheck { boolean posted(String sourceType, long sourceId, String step) }` — SQL on
  `journal_entry` (also used by Task 4).
- `SupplierBillService`: `SupplierBillView create(SupplierBillForm, @Nullable String actor)`, `SupplierBillView
  update(long id, SupplierBillForm, @Nullable String actor)` (409 when posted or cancelled), `SupplierBillView
  cancel(long id, LocalDate on, String reason, @Nullable String actor)`, `SupplierBillView get(long id)`,
  `List<SupplierBillListEntry> list(@Nullable Long partyId, @Nullable String status, @Nullable YearMonth month,
  @Nullable String source)`, `AttachmentUpload startAttachment(long id, String contentType)` →
  `record AttachmentUpload(String key, String uploadUrl)` (key `bills/<id>/<uuid>.<pdf|jpg|png|webp>`, presign
  PUT 10 min via `ObjectStorage.presignPut`, 409 "File storage is not set up" when not configured; follow
  `GalleryService`), `void confirmAttachment(long id, String key)` (checks `exists`, sets `attachment_key`).
  - Create: validate party is a supplier (400 "Pick a supplier"); duplicate bill no → 409 "This supplier's bill
    <no> is already recorded"; `supplyStateCode` default = `IndianStates.code(party.state)`; compute line taxes
    and totals; insert bill + lines; STOCK → `InventoryService.move(item, IN, qty, null, "Bill <billNo>")` and
    `setCost(item, taxable / qty rounded)`; ASSET → insert `fixed_asset(name=description, class, cost=taxable,
    put_to_use_on=billDate, bill_line_id)`; `paidNow` → `SupplierPaymentService.payBill(...)` from Task 4 (until
    Task 4 exists, leave a `// Task 4` stub that throws `UnsupportedOperationException`); attach
    `tdsWarning` when `suggestTds(...).thresholdCrossed()` and `tdsRupees == 0`.
  - Update: OUT movement per old STOCK line, delete old assets (409 "This bill's asset already has depreciation
    in the books" if any `asset_depreciation` row of it is posted), delete old lines, then as create.
  - Cancel: 409 "Remove the payments on this bill first" when allocations exist; asset with posted depreciation
    or disposed → 409 "Dispose the asset first"; else delete unposted asset rows, OUT movement per STOCK line,
    set `status=CANCELLED`, `cancelled_on`, `cancel_reason`.
- Controller: `GET /bills?partyId=&status=&month=YYYY-MM&source=`, `GET /bills/{id}`, `POST /bills`,
  `PUT /bills/{id}`, `POST /bills/{id}/cancel` (`record BillCancelForm(@NotNull LocalDate on, @NotBlank
  @Size(max=200) String reason)`), `POST /bills/{id}/attachment` (`{contentType}` → `AttachmentUpload`),
  `PUT /bills/{id}/attachment` (`{key}`), `POST /bills/tds-check` (`record TdsCheckForm(@NotNull Long partyId,
  @NotNull LocalDate billDate, @NotNull BigDecimal taxableRupees, @Nullable Long billId)` → `TdsSuggestion`).

- [ ] Step 1: Write `BillMathTest`: `intraStateSplitsOddPaisaToSgst()` (taxable 100.10 at 5% → tax 5.01, cgst
  2.50, sgst 2.51); `interStateIsAllIgst()` (1000 at 12% → igst 120.00); `blankStateIsIntraState()`
  (`interState(null,"33")` false, `interState("29","33")` true); `tdsRounds()` (12345.67 at 2% → 246.91);
  `fyStart()` (2027-03-31 → 2026-04-01, 2026-04-01 → 2026-04-01).
- [ ] Step 2: Write `SupplierBillServiceIT`: `createWithStockExpenseAssetLines()` (stock on hand +10, item cost =
  taxable/10, one fixed asset with cost = taxable, totals right); `duplicateBillNoIs409()` (case-insensitive);
  `updateUnpostedBillMovesStockByDifference()` (qty 10 → 6: on hand ends +6 from start); `cancelReturnsStock()`;
  `cancelWithAllocationIs409()`; `updatePostedBillIs409()` (insert ISSUE journal row by SQL first);
  `tdsSuggestedOnlyAboveThreshold()` (194C-O supplier with PAN: bill 20000 → suggested 0; second bill 90000 in same
  FY → crossed, suggested 1800.00; PAN null → 20% rate); `warningWhenCrossedAndNoTds()`.
- [ ] Step 3: Implement.
- [ ] Step 4: Compile check.
- [ ] Step 5: Commit `feat(purchases): supplier bills with stock, assets and TDS`.

### Task 4: Supplier payments

**Files:**
- Create: `purchases/SupplierPaymentService.java`, `purchases/SupplierPaymentForm.java`,
  `purchases/SupplierPaymentView.java`, `purchases/BankLinePaymentForm.java`
- Modify: `purchases/SupplierBillService.java` (replace the Task 3 stub), `purchases/PurchasesController.java`
- Test: `backend/src/test/java/com/vaabhi/purchases/SupplierPaymentServiceIT.java`

**Interfaces:**
- `record PaymentAllocationInput(@NotNull Long billId, @NotNull @DecimalMin("0.01") BigDecimal amountRupees)`
- `record SupplierPaymentForm(@NotNull Long partyId, @NotNull LocalDate paidOn, @NotNull String mode /* CASH|UPI|
  CARD|BANK */, @NotNull @DecimalMin("0.01") BigDecimal amountRupees, @Nullable @Size(max=100) String reference,
  @Nullable @Size(max=300) String notes, @Nullable @Valid List<PaymentAllocationInput> allocations)` — null
  allocations ⇒ auto: oldest OPEN bills of the party first (`coalesce(due_date, bill_date)`, then `bill_date`, then
  id), each up to its outstanding (payable − allocated), until the amount runs out; remainder unallocated.
- `record SupplierPaymentView(long id, long partyId, String supplierName, LocalDate paidOn, String mode,
  BigDecimal amountRupees, BigDecimal allocatedRupees, @Nullable String reference, @Nullable Long bankLineId,
  boolean posted, List<PaymentAllocationView> allocations)`; `record PaymentAllocationView(long billId, String
  billNo, BigDecimal amountRupees)`.
- `record BankLinePaymentForm(@NotNull Long bankLineId, @NotNull Long partyId, @Nullable @Valid
  List<PaymentAllocationInput> allocations)`.
- `SupplierPaymentService`: `SupplierPaymentView create(SupplierPaymentForm, @Nullable String actor)`;
  `SupplierPaymentView payBill(long billId, String mode, @Nullable String reference, @Nullable String actor)`
  (amount = bill outstanding, `paidOn` = bill date, one allocation); `SupplierPaymentView fromBankLine(
  BankLinePaymentForm, @Nullable String actor)` — SQL read of `bank_line` (must be `UNMATCHED` with `debit_rupees >
  0`, else 409 "Only an unmatched money-out line can become a supplier payment"); amount = debit, `paidOn` =
  `line_date`, mode `BANK`, `reference` = line reference, `bank_line_id` set;
  `List<SupplierPaymentView> list(@Nullable Long partyId)`; `void delete(long id)` (409 when posted).
  Validation (400, nothing saved): allocation to another party's bill or a cancelled bill; Σ allocations >
  amount ("Allocated more than paid"); bill's total allocations > payable ("More than this bill's balance").
  `mode` must be a counter mode.
- Controller: `GET /payments?partyId=`, `POST /payments`, `DELETE /payments/{id}`, `POST /payments/from-bank-line`.

- [ ] Step 1: Write `SupplierPaymentServiceIT`: `autoAllocatesOldestFirst()` (bills 1000 due 1 Oct, 500 due 15 Oct,
  pay 1200 → 1000 + 200); `secondPaymentFillsRest()` (then pay 300 → 300 to bill 2, nothing unallocated);
  `overAllocationRejectedAndNothingSaved()`; `unallocatedRemainderIsAdvance()` (pay 2000 against 1500 owed →
  supplier balance −500); `paidNowCreatesPaymentForPayable()` (bill with TDS 200 on total 10000 → payment 9800);
  `fromBankLineTakesDebitLine()` and `fromCreditLineIs409()`; `deletePostedIs409()`.
- [ ] Step 2: Implement.
- [ ] Step 3: Compile check.
- [ ] Step 4: Commit `feat(purchases): supplier payments and allocation`.

### Task 5: Posting rules for bills and payments; Shiprocket supplier on COD

**Files:**
- Create: `accounts/rules/SupplierBillIssue.java`, `accounts/rules/SupplierBillCancel.java`,
  `accounts/rules/SupplierPaymentPosting.java`
- Modify: `accounts/PostingRule.java` (add `default void posted(long sourceId, long entryId) {}`),
  `accounts/JournalSweeper.java` (call `rule.posted(id, entry.id())` inside the same `tx` lambda),
  `accounts/BooksSettings.java` + `BooksSettingsForm.java` + `BooksSettingsService.java` (add `@Nullable Long
  shiprocketPartyId`; validate it is a supplier party when set), `accounts/rules/CodRemittanceRule.java`,
  `accounts/rules/RuleSupport.java` (add `@Nullable Long shiprocketPartyId()`)
- Test: `backend/src/test/java/com/vaabhi/accounts/PurchasePostingIT.java`; update `CodRemittanceIT` expectations

**Interfaces:**
- Rule orders: `SupplierBillIssue` 60, `SupplierPaymentPosting` 62, `SupplierBillCancel` 64.
- `SupplierBillIssue` (`SUPPLIER_BILL`/`ISSUE`): pending = bills with `bill_date >= start`, `status = 'OPEN'`
  and not posted (a bill cancelled before its ISSUE posts never posts at all). Entry dated `bill_date`, narration `"Bill <bill_no> – <supplier>"`, party = supplier on every line: Dr
  each line's `account_id` (taxable; lines on the same account summed), Dr `INPUT_GST` (Σ cgst+sgst+igst, if > 0),
  Cr `CREDITORS` (payable), Cr `TDS_PAYABLE` (tds, if > 0).
- `SupplierBillCancel` (`SUPPLIER_BILL`/`CANCEL`): pending = `CANCELLED` bills with `cancelled_on >= start` whose
  ISSUE is posted and CANCEL is not; entry dated `cancelled_on`: exact mirror of ISSUE (build by reading the ISSUE
  entry's lines and swapping sides).
- `SupplierPaymentPosting` (`SUPPLIER_PAYMENT`/`PAYMENT`): dated `paid_on`: Dr `CREDITORS` (party), Cr
  `support.modeAccount(mode)`. `posted(sourceId, entryId)`: when `bank_line_id` is set, `UPDATE bank_line SET status
  = 'MATCHED', journal_entry_id = :entry, matched_at = now() WHERE id = :line AND status = 'UNMATCHED'`.
- `CodRemittanceRule`: the deduction line becomes Dr `CREDITORS` with party = `shiprocketPartyId`; when the
  deduction > 0 and the setting is null, `build` throws `IllegalStateException("Pick the Shiprocket supplier in
  Books settings")` (the sweeper records it as a posting problem).

- [ ] Step 1: Write `PurchasePostingIT` (sweeper run via `JournalSweeper.sweepNow()` after setting the start date,
  like `PostingRulesIT`): `billIssuePostsPurchasesExpenseAssetGstCreditorsTds()` (one entry, balanced, lines as
  above, all with the supplier party); `cancelAfterPostingMirrors()` (creditors back to 0);
  `cancelBeforePostingPostsNothing()`; `paymentPostsCreditorsAgainstBank()`; `paymentFromBankLineMarksLineMatched()`;
  in `CodRemittanceIT`: `deductionGoesToShiprocketCreditors()` and `deductionWithoutSupplierIsAProblem()`.
- [ ] Step 2: Implement.
- [ ] Step 3: Compile check.
- [ ] Step 4: Commit `feat(accounts): post supplier bills and payments`.

### Task 6: TDS sections, challans, summary

**Files:**
- Create: `accounts/TdsService.java`, `accounts/TdsSectionView.java`, `accounts/TdsSectionForm.java`,
  `accounts/TdsChallanForm.java`, `accounts/TdsChallanView.java`, `accounts/TdsMonthSummary.java`,
  `accounts/TdsController.java`, `accounts/rules/TdsChallanPosting.java`
- Test: `backend/src/test/java/com/vaabhi/accounts/TdsServiceIT.java`

**Interfaces:**
- `record TdsSectionForm(@NotBlank @Size(max=10) String code, @NotBlank @Size(max=80) String name, @NotNull
  @DecimalMin("0") @DecimalMax("30") BigDecimal ratePercent, @NotNull @DecimalMin("0") @DecimalMax("30") BigDecimal
  noPanRatePercent, @Nullable BigDecimal yearThresholdRupees, @Nullable BigDecimal singleThresholdRupees, boolean
  active)`; `TdsSectionView` = the row.
- `record TdsChallanForm(@NotNull Long tdsSectionId, @NotNull YearMonth periodMonth, @NotNull @DecimalMin("0.01")
  BigDecimal amountRupees, @NotNull @DecimalMin("0") BigDecimal interestRupees, @NotBlank @Size(max=20) String
  challanNo, @NotBlank @Pattern(regexp="[0-9]{7}") String bsrCode, @NotNull LocalDate paidOn, @NotNull String mode)`.
- `record TdsMonthSummary(String sectionCode, String sectionName, YearMonth month, BigDecimal deductedRupees,
  BigDecimal paidRupees, BigDecimal balanceRupees)` — deducted = Σ `tds_rupees` of OPEN bills by bill month; paid =
  Σ challan `amount_rupees` by `period_month`.
- `TdsService`: `sections()`, `createSection(form)`, `updateSection(id, form)`, `createChallan(form, actor)`,
  `challans(from, to)`, `summary(LocalDate from, LocalDate to)`.
- Controller under `/api/v1/admin/accounts/tds`: `GET/POST /sections`, `PUT /sections/{id}`, `GET /summary?from=&to=`,
  `GET/POST /challans`.
- `TdsChallanPosting` (`TDS_CHALLAN`/`PAYMENT`, order 66): dated `paid_on`: Dr `TDS_PAYABLE` amount, Dr
  `EXPENSE_OTHER` interest (if > 0), Cr mode account (amount + interest).

- [ ] Step 1: Write `TdsServiceIT`: `sectionsSeededAndEditable()`; `summaryDeductedVsPaid()` (two bills Oct with TDS
  100 + 50, challan 150 for Oct → balance 0); `challanPostsAgainstTdsPayableAndInterest()`;
  `badBsrCodeIs400()` (via controller MockMvc, follow `AccountsControllerIT`).
- [ ] Step 2: Implement.
- [ ] Step 3: Compile check.
- [ ] Step 4: Commit `feat(accounts): TDS sections, challans and summary`.

### Task 7: Fixed assets and depreciation

**Files:**
- Create: `accounts/DepreciationMath.java` (pure), `accounts/DepreciationScheduler.java`, `accounts/AssetService.java`,
  `accounts/AssetClassView.java`, `accounts/AssetClassForm.java`, `accounts/FixedAssetView.java`,
  `accounts/OpeningAssetForm.java`, `accounts/AssetUpdateForm.java`, `accounts/AssetDisposalForm.java`,
  `accounts/AssetController.java`, `accounts/rules/AssetDepreciationPosting.java`,
  `accounts/rules/AssetDisposalPosting.java`
- Test: `backend/src/test/java/com/vaabhi/accounts/DepreciationMathTest.java`, `AssetServiceIT.java`

**Interfaces:**
- `final class DepreciationMath { record MonthCharge(YearMonth month, BigDecimal amount) {}
  static List<MonthCharge> schedule(BigDecimal cost, BigDecimal residualPercent, int lifeYears, BigDecimal
  openingAccdep, LocalDate putToUse, LocalDate booksStart, YearMonth throughMonth, @Nullable LocalDate disposedOn) }`
  - From = max(putToUse, booksStart). Months from `YearMonth.from(from)` to the earlier of `throughMonth` and the
    disposal month.
  - Full month = (cost − cost × residual/100) / (life × 12), rounded per month.
  - First month × (days from `from` to month end, inclusive) / days in month; disposal month × (days from month
    start, or `from` if same month, to `disposedOn`, inclusive) / days in month.
  - Cap: running total starts at `openingAccdep`; a month's charge is min(charge, depreciable − running); stop when
    it reaches 0; never emit a zero or negative month.
- `DepreciationScheduler`: `void refresh(long assetId)` — books start null → delete unposted rows of the asset and
  return; else compute `schedule(...)` with `throughMonth` = last fully ended IST month, delete this asset's
  **unposted** `asset_depreciation` rows, insert months that have no posted row. `void refreshAll()` — every
  non-deleted asset; `@Scheduled(cron = "0 15 1 * * *", zone = "Asia/Kolkata")`. Also called by `AssetService`
  after create/update/dispose and by `BooksSettingsService` after the start date changes. "Posted" = a
  `journal_entry` with `source_type='ASSET_DEPRECIATION' AND source_id = row.id`.
- `record AssetClassForm(@NotBlank @Size(max=60) String name, @Min(1) @Max(60) int lifeYears, @NotNull
  @DecimalMin("0") @DecimalMax("50") BigDecimal residualPercent, boolean active)`. Creating a class also creates its
  two accounts (next free codes in 15xx, `system_key` null) via `ChartService`.
- `record FixedAssetView(long id, String name, long classId, String className, BigDecimal costRupees, LocalDate
  putToUseOn, BigDecimal openingAccdepRupees, BigDecimal accumulatedRupees, BigDecimal bookValueRupees, @Nullable
  Long billId, @Nullable String billNo, @Nullable LocalDate disposedOn, @Nullable BigDecimal disposalProceedsRupees,
  boolean hasPostedDepreciation)` — accumulated = opening + Σ posted rows.
- `record OpeningAssetForm(@NotBlank @Size(max=120) String name, @NotNull Long classId, @NotNull @DecimalMin("0.01")
  BigDecimal costRupees, @NotNull LocalDate putToUseOn, @NotNull @DecimalMin("0") BigDecimal openingAccdepRupees)`
  — `putToUseOn` must be before the books start date (400 otherwise: "Assets bought after the books start come from
  a bill").
- `record AssetUpdateForm(@NotBlank String name, @NotNull Long classId, @NotNull LocalDate putToUseOn)` — 409 once
  any depreciation is posted (name only is still allowed: update just the name in that case).
- `record AssetDisposalForm(@NotNull LocalDate disposedOn, @NotNull @DecimalMin("0") BigDecimal proceedsRupees,
  @Nullable String mode)` — mode required when proceeds > 0; disposedOn ≥ put-to-use; 409 when already disposed.
- `AssetService`: `classes()`, `createClass`, `updateClass`, `register()`, `createOpening(form, actor)`,
  `update(id, form)`, `dispose(id, form)`.
- Controller under `/api/v1/admin/accounts/assets`: `GET/POST /classes`, `PUT /classes/{id}`, `GET /`, `POST /`,
  `PUT /{id}`, `POST /{id}/dispose`.
- `AssetDepreciationPosting` (`ASSET_DEPRECIATION`/`DEPRECIATION`, order 80): pending = unposted
  `asset_depreciation` rows whose month end ≥ start; dated the month's last day (or `disposed_on` for the disposal
  month); Dr `DEPRECIATION`, Cr the class's `accdep_account_id`; narration `"Depreciation <MMM yyyy> – <name>"`.
- `AssetDisposalPosting` (`ASSET`/`DISPOSAL`, order 85): pending = disposed assets with `disposed_on >= start`, not
  posted, and **no unposted `asset_depreciation` rows**; accdep = opening + Σ posted rows; book = cost − accdep;
  Dr accdep account (accdep, if > 0), Dr mode account (proceeds, if > 0), Dr `ASSET_LOSS` (book − proceeds, if > 0),
  Cr class asset account (cost), Cr `ASSET_GAIN` (proceeds − book, if > 0).

- [ ] Step 1: Write `DepreciationMathTest` (residual 5%):
  - `firstMonthProRata()` — cost 120000, life 10, put to use 2026-04-16, start 2026-04-01, through 2026-06 →
    [2026-04: 475.00, 2026-05: 950.00, 2026-06: 950.00].
  - `disposalMonthProRata()` — same, disposed 2026-07-10, through 2026-12 → last is 2026-07: 306.45; nothing after.
  - `capsAtResidual()` — cost 1000, life 1, put to use 2026-04-01, through 2027-06 → 12 months, first 11 are
    79.17, the 12th is 79.13, total 950.00, none after.
  - `openingAccdepCountsTowardCap()` — cost 120000, life 10, opening 113500, put to use 2020-01-01, start
    2026-04-01, through 2026-06 → [2026-04: 500.00] only.
  - `openingAlreadyFullyDepreciated()` — opening 114000 → empty list.
  - `startsAtBooksStartNotPutToUse()` — put to use 2025-01-10, start 2026-04-16 → first month 2026-04 pro rata
    15/30 of full.
- [ ] Step 2: Write `AssetServiceIT`: `billAssetGetsScheduleAfterStartDateSet()`; `threeMonthsPostThenDisposalGain()`
  (sweep, then dispose with proceeds above book value → disposal entry balances with `ASSET_GAIN`);
  `disposalWaitsForDepreciationRows()`; `openingAssetAfterStartIs400()`; `editAfterPostedDepreciationIs409()`;
  `newClassCreatesTwoAccounts()`.
- [ ] Step 3: Implement.
- [ ] Step 4: Compile check.
- [ ] Step 5: Commit `feat(accounts): fixed-asset register and monthly depreciation`.

### Task 8: Shiprocket freight invoice import

**Files:**
- Create: `purchases/ShiprocketInvoiceImport.java`, `purchases/ShiprocketInvoiceSummary.java`
- Modify: `accounts/BankFileParser.java` (make `parseAmount` and `parseDate` public), `purchases/PurchasesController.java`
- Test: `backend/src/test/java/com/vaabhi/purchases/ShiprocketInvoiceImportIT.java`

**Interfaces:**
- Mapping keys (`ColumnMapping.columns`): `invoiceNo`, `invoiceDate` (required), `taxable` (required), `gst`,
  `cgst`, `sgst`, `igst`, `total` (optional). Profile kind `SHIPROCKET_INVOICE` via `ImportProfileService`.
- `record ShiprocketInvoiceSummary(int invoicesCreated, int invoicesSkipped, List<String> skippedInvoiceNos,
  List<SkippedFileRow> badRows, BigDecimal totalRupees)`.
- `ShiprocketInvoiceImport`: `FilePreview preview(MultipartFile)` (first 20 rows + saved mapping; same 5 MB limit
  and messages as Phase 3); `ShiprocketInvoiceSummary importFile(MultipartFile, ColumnMapping, @Nullable String
  actor)`:
  1. No `shiprocketPartyId` in books settings → 409 "Pick the Shiprocket supplier in Books settings first" (read the
     setting through `BooksSettingsService` public API).
  2. Rows after `headerRow`; a row without a parseable date or invoice number → `badRows`.
  3. Group by invoice number: Σ taxable, Σ cgst/sgst/igst when those columns are mapped, else Σ `gst` split by
     `BillMath` intra/inter rule using the Shiprocket party's state vs company state; date = the group's first date.
  4. Skip invoice numbers already billed by that supplier (case-insensitive).
  5. For each new invoice, insert a bill directly (not via `create`, since the GST is given rather than a rate):
     one EXPENSE line on account `EXPENSE_COURIER` (look up id by `system_key`), `gst_rate_percent` = tax /
     taxable × 100 rounded to 2 dp (0 when taxable is 0), `source = 'SHIPROCKET'`, due date = invoice date,
     description `"Shiprocket freight <invoiceNo>"`. One transaction per file.
- Controller: `POST /shiprocket-invoices/preview` (multipart `file`), `POST /shiprocket-invoices` (multipart `file`
  + `mapping` JSON part).

- [ ] Step 1: Write `ShiprocketInvoiceImportIT` (CSV built in the test): `groupsRowsPerInvoice()` (3 AWB rows for
  SR-1, 1 for SR-2 → 2 bills, totals summed); `reimportSkips()` (same file again → 0 created, 2 skipped);
  `splitColumnsUsedWhenMapped()`; `noSupplierSetIs409()`; `badDateRowListed()`.
- [ ] Step 2: Implement.
- [ ] Step 3: Compile check.
- [ ] Step 4: Commit `feat(purchases): import Shiprocket freight invoices as bills`.

### Task 9: Input GST report

**Files:**
- Create: `accounts/InputGstReport.java`, `accounts/InputGstMonth.java`; Modify `accounts/AccountsController.java`
- Test: `backend/src/test/java/com/vaabhi/accounts/InputGstReportIT.java`

**Interfaces:**
- `record InputGstMonth(YearMonth month, BigDecimal taxableRupees, BigDecimal cgstRupees, BigDecimal sgstRupees,
  BigDecimal igstRupees, BigDecimal gatewayGstRupees, BigDecimal totalRupees)` — bills: OPEN only, by `bill_date`
  month, from `supplier_bill_line`; gateway = Σ `razorpay_settlement.tax_rupees` by `settled_on` month (counted as
  IGST in `totalRupees`; kept in its own column). Months with nothing are left out.
- `InputGstReport`: `List<InputGstMonth> months(LocalDate from, LocalDate to)`, `String csv(LocalDate from, LocalDate
  to)` (header `Month,Taxable,CGST,SGST,IGST,Gateway GST,Total`).
- Endpoints `GET /api/v1/admin/accounts/input-gst?from=&to=` and `GET /input-gst.csv?from=&to=` (`text/csv`,
  attachment `input-gst-<from>-<to>.csv`).

- [ ] Step 1: Write `InputGstReportIT`: `sumsBillsAndGatewayByMonth()`; `cancelledBillsLeftOut()`.
- [ ] Step 2: Implement.
- [ ] Step 3: Compile check.
- [ ] Step 4: Commit `feat(accounts): input GST per month`.

### Task 10: Regenerate the API client

- [ ] Step 1: Main session restarts the backend through IntelliJ (`VaabhiApplication`; stop the old java on :8080
  first) so V36 applies to the dev DB, then runs `pnpm generate:api` in `frontend/`.
- [ ] Step 2: `pnpm typecheck --filter admin` (no UI yet; only checks existing code still compiles against the
  regenerated schema; fix breakages from the `BooksSettings` change).
- [ ] Step 3: Commit `chore(api-client): regenerate for purchases and books phase 4`.

### Task 11: Admin purchases screens

**Files:**
- Create: `frontend/admin/src/lib/purchases.ts` (+ `purchases.test.ts` for pure helpers)
- Create: `frontend/admin/src/app/money/purchases/page.tsx` (suppliers list + "Add supplier" sheet),
  `purchases/supplier/page.tsx` (`?id=`; statement, "New bill", "Pay"), `purchases/bills/page.tsx` (list with
  filters), `purchases/bill/page.tsx` (`?id=` edit/view, no id = new; `?supplier=` pre-selects),
  `purchases/shiprocket/page.tsx` (upload wizard)
- Modify: `frontend/admin/src/app/money/page.tsx` (add a "Purchases" card → `/money/purchases/`),
  `frontend/admin/src/lib/api.ts` (add `admin.purchases.*` calls in the existing style)

**Interfaces:**
- `lib/purchases.ts`: `lineTax(taxable: string, rate: string, interState: boolean): {cgst, sgst, igst, total}`
  (same rounding as `BillMath`, in paise integers), `billTotals(lines, interState)`, `defaultAllocation(bills,
  amount)` (oldest first, mirrors the backend), `LINE_KINDS`, `PAYMENT_MODES = ["CASH","UPI","CARD","BANK"]`.
- Bill form: supplier picker (from `GET /suppliers`), state defaults from the supplier, lines editor (kind → account
  select of EXPENSE accounts from the chart API / stock item select from inventory API + qty / asset class select
  from `GET /accounts/assets/classes`), live tax and totals via `lineTax`, TDS field pre-filled from
  `POST /bills/tds-check` on supplier/date/taxable change (debounced), warning text shown, "Paid now" toggle with
  mode and reference, attachment upload after save (presigned PUT, then confirm; hidden with a note when storage
  returns 409). Posted bills read-only with "Cancel bill" (date + reason sheet).
- Pay sheet (on supplier page): amount, mode, date, reference, allocation table pre-filled by `defaultAllocation`
  and editable.
- Shiprocket page: reuse the Phase 3 upload wizard component from `app/money/accounts/bank/upload/` with fields
  `invoiceNo, invoiceDate, taxable, gst, cgst, sgst, igst, total` (extract a shared component if it is inline; keep
  bank behaviour unchanged), then result counts and skipped invoice numbers.
- Empty states: "No suppliers yet", "No bills yet".

- [ ] Step 1: Write `purchases.test.ts`: `lineTax("100.10","5",false)` → cgst "2.50", sgst "2.51";
  `defaultAllocation` oldest-first case from Task 4's IT.
- [ ] Step 2: Implement the lib and pages.
- [ ] Step 3: `pnpm typecheck --filter admin` and `pnpm lint --filter admin` → clean.
- [ ] Step 4: Commit `feat(admin): suppliers, bills, payments and Shiprocket import`.

### Task 12: Admin books screens for Phase 4

**Files:**
- Create: `frontend/admin/src/app/money/accounts/assets/page.tsx`, `accounts/tds/page.tsx`,
  `accounts/input-gst/page.tsx`
- Modify: `frontend/admin/src/lib/accounts.ts` + `lib/api.ts` (calls), `app/money/accounts/page.tsx` (links to the
  three pages; Shiprocket supplier picker in the books settings form, listing suppliers from
  `GET /money/purchases/suppliers`), `app/money/accounts/bank/statement/page.tsx` (unmatched debit line sheet gains
  "Supplier payment": supplier picker + optional allocation table, calls `POST /payments/from-bank-line`)

**Interfaces:**
- Assets page: register table (name, class, cost, accumulated, book value, bill no link to
  `/money/purchases/bill/?id=`, status), totals row, "Add opening asset" sheet, "Dispose" sheet (date, proceeds,
  mode when proceeds > 0), asset classes section with edit (owner only; hide edit for non-owners as other books
  pages do).
- TDS page: month × section table (deducted, paid, balance) for the chosen FY, "Record challan" sheet, sections
  editor (owner).
- Input GST page: from/to (default current FY), month table, CSV download link.

- [ ] Step 1: Implement.
- [ ] Step 2: `pnpm typecheck --filter admin` and `pnpm lint --filter admin` → clean.
- [ ] Step 3: Commit `feat(admin): assets, TDS, input GST and supplier payment from bank line`.

### Task 13: Gaps and wrap-up

- [ ] Step 1: Add a "Phase 4: purchases, payables, TDS, assets" section to `docs/BOOKS_GAPS.md` with gaps found
  during review, plus these known ones: bill attachments sit in the media bucket behind an unguessable key (move to
  a private bucket with signed GETs); reversing a supplier payment entry does not unmatch its bank line; no purchase
  returns/debit notes; TDS warning ignores lower-deduction certificates; Shiprocket wallet recharges must be
  recorded as payments by hand (or from the bank line).
- [ ] Step 2: Whole-branch review of the Phase 4 diff; spotless (`./mvnw -q spotless:apply` in the scratch
  worktree) and prettier (`pnpm format` or the repo's prettier command) on changed files.
- [ ] Step 3: Commit `docs(accounts): phase 4 gaps` and push the branch.
