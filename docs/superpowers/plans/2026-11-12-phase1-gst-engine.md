# Phase 1 GST Engine and Credit Notes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tax every invoice line by its tax class (with price slab), charge IGST on out-of-state web orders, issue
credit notes for returns, cancels and corrections, and produce a month's GSTR-1 tables as CSV.

**Architecture:** A pure `TaxEngine` in `money` turns invoice lines into taxed lines using a rate lookup over
`tax_class`. Both shop and web invoices go through the shared `InvoiceService.insertInvoice`, which now stores tax
per line. `CreditNoteService` reverses invoice lines pro rata; checkout calls it on return refund and on cancel.
`Gstr1Service` groups the month's invoice and credit-note lines.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Modulith, JdbcClient, Postgres + Flyway; Next.js 16 admin
(static export) and storefront; pnpm + Turborepo.

**Spec:** `docs/superpowers/specs/2026-11-12-phase1-gst-engine-design.md`

## Global Constraints

- Work on branch `feat/id-engine-parties` (owner: all books phases in one PR, #48).
- Do not run tests (unit, IT, e2e, coverage, verify). Write them; do not run them.
- Verification per task: backend compile with `./mvnw -q -o compile test-compile -DskipTests` in a scratch
  worktree (the controller runs it); frontend `pnpm typecheck` and `pnpm lint` from `frontend/`.
- Pre-push runs Prettier and Spotless: format before committing (`./mvnw -q -o spotless:apply`,
  `npx prettier --write <files>`).
- Migration is `V31__gst_engine.sql`; no other migration.
- Money columns are `NUMERIC(12,2)` named `*_rupees`; use `com.vaabhi.shared.rupees.Rupees` helpers.
- Dates of issue are today in Asia/Kolkata (`InvoiceParties.today(clock)` pattern).
- `money` must not import `checkout` or `catalog`. `settings` must not import `money`.
- Don't hand-edit `frontend/packages/api-client/src/schema.d.ts` or `openapi.json`; the controller regenerates
  with `pnpm generate:api` after Task 6.
- No hardcoded demo data in the frontend; honest empty states.
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Seed tax rates are placeholders for a CA to confirm (say so in the UI note).

## Rulings made while planning (spec deviations, owner to see)

1. Slab test: a piece goes to the higher rate when its value **before GST at the lower rate** exceeds the limit
   (avoids the circular inclusive-price case). Cost if wrong: one comparison to change.
2. Return credit note is issued when the return is marked refunded (`WebOrderReturns.refund`), not when Razorpay
   later reports PROCESSED: the note records goods coming back. Cost: a note may exist while the refund is pending.
3. There is no category editor in admin, so category tax classes are set in a "Tax by category" list on the Tax
   classes card (Settings → Billing), through a new catalog endpoint.
4. Shop order cancel (event listener) uses `voidOrCredit`: same-day with no notes → cancel, else full credit note,
   so an order cancel is never blocked by the same-day rule. Web cancel always issues a full credit note (spec).
5. GSTR-1 lives in `money` (`Gstr1Service`, endpoints under `/api/v1/admin/money/gstr1`) with its own admin page
   `/reports/gst`; the old "GST register" report entry is removed from `ReportService`.
6. Credit notes on small B2C invoices are netted into B2CS and the HSN summary; only notes on B2CL invoices go to
   CDNUR (GSTR-1 practice).

## Review Focus

1. A shop order with a booking discount: the discount must be spread across lines, so the line taxable values sum
   to the invoice taxable value and the invoice total still equals what the order charged.
2. A second partial credit note on the same line: it may credit only the remaining quantity, and the last one takes
   the exact remainder so line totals match to the paisa.
3. GST off (today): issuing must not fail when no default tax class is set, and the print must look as it does now.
4. Backfilled old invoices: lines' taxable/CGST/SGST must sum to the invoice's stored values.
5. Web order to a state name that `IndianStates` does not know (old orders, typos): intra-state, no exception.

Each has a test in the owning task (Tasks 1, 2, 3, 4).

---

### Task 1: V31 migration, tax classes and the default class setting

**Files:**
- Create: `backend/src/main/resources/db/migration/V31__gst_engine.sql`
- Create: `backend/src/main/java/com/vaabhi/money/TaxClass.java`, `TaxClassForm.java`, `TaxClassService.java`
- Modify: `backend/src/main/java/com/vaabhi/money/MoneyController.java` (tax-class endpoints)
- Modify: `backend/src/main/java/com/vaabhi/settings/BillingSettings.java` (`defaultTaxClassId`)
- Test: `backend/src/test/java/com/vaabhi/money/TaxClassServiceIT.java`, `V31BackfillIT.java`

**Interfaces:**
- Produces:
  - `public record TaxClass(long id, String name, Kind kind, String hsnCode, BigDecimal ratePercent,
    @Nullable BigDecimal slabLimitRupees, @Nullable BigDecimal rateAbovePercent, boolean inUse)` with
    `enum Kind { GOODS, SERVICE }`.
  - `record TaxClassForm(@NotBlank @Size(max=60) String name, @NotNull Kind kind,
    @Pattern("^[0-9]{4,8}$") String hsnCode, rate 0–28 (2 dp), @Nullable slabLimitRupees > 0,
    @Nullable rateAbovePercent 0–28)`; both slab fields or neither (400 "Give both the price limit and the rate
    above it, or neither").
  - `TaxClassService`: `List<TaxClass> list()`, `TaxClass create(TaxClassForm)`, `TaxClass update(long, TaxClassForm)`,
    `void delete(long)` (409 "This tax class is in use" when referenced by category, garment_template,
    service_product or the billing default), and `public TaxRate resolve(@Nullable Long classId, BigDecimal
    unitBeforeTax)` → `record TaxRate(String hsnCode, BigDecimal ratePercent, TaxClass.Kind kind)`; falls back to
    the default class; throws 409 "Set a default tax class in Settings → Billing" when there is neither.
  - `@Nullable Long shopItemClass(@Nullable Long templateId, @Nullable Long serviceProductId)`:
    service product's class, else its template's class, else the item template's class, else null.
  - Endpoints: `GET/POST /api/v1/admin/money/tax-classes`, `PUT/DELETE /api/v1/admin/money/tax-classes/{id}`.
  - `BillingSettings` gains last component `@Nullable Long defaultTaxClassId`; `defaultGstRate` stays (old JSON)
    but is no longer read when issuing.

- [ ] **Step 1: Write V31** per spec "Data", plus `invoice_line.discount_rupees NUMERIC(12,2) NOT NULL DEFAULT 0`
  (the line's share of the order discount; `amount_rupees` stays quantity × unit). Seed classes "Garments"
  (GOODS, 6204, 5, 2500, 18) and "Tailoring service" (SERVICE, 998821, 5). Set the billing JSON
  `defaultTaxClassId` to the Garments id (`jsonb_set` on the `shop_setting` billing row). `invoice_line`
  backfill: `gst_rate = invoice.gst_rate`, `hsn_code` NULL, taxable/CGST/SGST spread by `amount_rupees` share with
  the last line (highest `position`) taking the remainder, `igst_rupees = 0`; invoices get `supply_type 'INTRA'`,
  `place_of_supply = seller_state_code`. `credit_note` and `credit_note_line` as spec; credit_note `number`
  VARCHAR(16) UNIQUE.
- [ ] **Step 2: Write tests.** `TaxClassServiceIT`: `resolveUsesLowerRateAtLimit` (Garments, unit 2500.00 → 5),
  `resolveUsesHigherRateAboveLimit` (2500.01 → 18), `resolveFallsBackToDefault` (null id → Garments),
  `resolveWithoutDefaultFails` (409 message above), `deleteInUseRefused`, `slabNeedsBothFields`.
  `V31BackfillIT`: `backfilledLinesSumToInvoice` — for every invoice, `SUM(line taxable/cgst/sgst)` equals the
  invoice's (Review Focus 4).
- [ ] **Step 3: Implement** the records, service and endpoints (admin role as other money endpoints).
- [ ] **Step 4: Format and commit** `feat(money): tax classes and V31 GST schema`.

### Task 2: TaxEngine

**Files:**
- Create: `backend/src/main/java/com/vaabhi/money/TaxEngine.java`
- Test: `backend/src/test/java/com/vaabhi/money/TaxEngineTest.java`

**Interfaces:**
- Consumes: `TaxClassService.TaxRate`.
- Produces (package-private, pure, static):
  - `record TaxInput(String description, int quantity, BigDecimal unitRupees, @Nullable Long taxClassId,
    @Nullable Long webOrderLineId, boolean shipping)`
  - `record TaxedLine(TaxInput input, BigDecimal amountRupees, BigDecimal discountRupees, @Nullable String hsnCode,
    BigDecimal gstRate, BigDecimal taxableRupees, BigDecimal cgstRupees, BigDecimal sgstRupees,
    BigDecimal igstRupees)`
  - `enum SupplyType { INTRA, INTER }`
  - `record TaxResult(List<TaxedLine> lines, SupplyType supplyType, BigDecimal taxableRupees, BigDecimal
    cgstRupees, BigDecimal sgstRupees, BigDecimal igstRupees, BigDecimal totalRupees, BigDecimal highestRate)`
  - `interface RateLookup { TaxClassService.TaxRate resolve(@Nullable Long classId, BigDecimal unitBeforeTax); }`
  - `static TaxResult compute(List<TaxInput> lines, BigDecimal chargedRupees, @Nullable String sellerStateCode,
    @Nullable String buyerStateCode, boolean gstApplies, boolean pricesIncludeTax, RateLookup rates)`

Algorithm (not determined by the signature):
1. `discount = max(0, sum(qty × unit over all lines) − chargedRupees)`; spread over non-shipping lines by amount,
   HALF_UP to paisa, the last non-shipping line taking the remainder.
2. Per goods line: net = amount − discount share; unit net = net / qty. Look up the class at the lower rate
   (`rates.resolve(id, unitBeforeTax)` where `unitBeforeTax` = unit net, or unit net ×100/(100+lowerRate) when
   inclusive). `TaxClassService.resolve` applies the slab (Ruling 1). When `!gstApplies`: rate 0, HSN still
   from the class, but if lookup throws for a missing default, use HSN null and rate 0.
3. Shipping line: SAC `9965`, rate = highest goods rate (0 when no goods).
4. Line tax as today's `InvoiceService.tax(net, rate, inclusive)`; INTER when both state codes are non-null and
   differ → whole GST in IGST; else CGST/SGST halves, SGST takes the odd paisa.
5. Totals are sums of lines.

- [ ] **Step 1: Write `TaxEngineTest`** with a stub `RateLookup` (Garments slab 2500 → 5/18):
  - `exactlyAtLimitIsLowerRate`: exclusive, one line 1 × 2500.00 → rate 5, taxable 2500.00, gst 125.00.
  - `justAboveLimitIsHigherRate`: exclusive, 2500.01 → rate 18.
  - `discountCanMoveLineToLowerSlab`: exclusive, 1 × 2600, charged 2400 → line discount 200, rate 5.
  - `discountSpreadSumsToCharged` (Review Focus 1): lines 1000 + 500 + 300, charged 1700 (exclusive, GST off) →
    discounts sum to 100.00, line taxable sum = invoice taxable = 1700.00, total 1700.00.
  - `interStateIsIgst`: seller "36", buyer "29" → cgst 0, sgst 0, igst = full GST.
  - `sameStateSplitsWithOddPaisaToSgst`: inclusive 105.01 at 5% → cgst + sgst = gst, sgst − cgst ∈ {0, 0.01}.
  - `unknownBuyerStateIsIntra` (Review Focus 5): buyer null → INTRA.
  - `shippingTakesHighestRate`: lines at 5% and 18% plus shipping 100 → shipping rate 18, hsn "9965".
  - `gstOffGivesZeroTaxButKeepsHsn` (Review Focus 3): gstApplies false → all tax 0, hsn "6204", total = charged.
  - `singleRateMatchesOldTax`: for inclusive 1050 at 5% the line equals `InvoiceService.tax(1050, 5, true)`.
- [ ] **Step 2: Implement `TaxEngine.compute`.** Keep `InvoiceService.tax` as the per-line helper (move it to
  `TaxEngine` and leave a delegating static in `InvoiceService` only if other code calls it).
- [ ] **Step 3: Format and commit** `feat(money): tax engine with slabs, discount spread and IGST`.

### Task 3: Issue invoices through the engine (shop and web)

**Files:**
- Modify: `money/InvoiceService.java` (`NewInvoice`, `insertInvoice`, `issue`, `lines(Order)`, `get`, list)
- Modify: `money/WebInvoiceService.java`, `money/WebInvoiceRequest.java`
- Modify: `money/InvoiceLine.java`, `money/Invoice.java`, `money/StoreInvoice.java`
- Create: `catalog/CatalogTax.java`
- Modify: `checkout/CheckoutService.java` (`invoiceFor`)
- Test: `money/WebInvoiceServiceIT.java`, `money/InvoiceServiceIT.java` (add cases; create if missing)

**Interfaces:**
- Consumes: Task 1 `TaxClassService.resolve`, `shopItemClass`; Task 2 `TaxEngine.compute`.
- Produces:
  - `public record InvoiceLine(long id, String description, int quantity, BigDecimal unitRupees, BigDecimal
    amountRupees, BigDecimal discountRupees, @Nullable String hsnCode, BigDecimal gstRate,
    BigDecimal taxableRupees, BigDecimal cgstRupees, BigDecimal sgstRupees, BigDecimal igstRupees)`.
  - `Invoice` gains `BigDecimal igstRupees`, `@Nullable String placeOfSupply`, `String supplyType`;
    `StoreInvoice` gains the same plus the new line fields.
  - `NewInvoice.lines` becomes `List<TaxEngine.TaxInput>`; `NewInvoice` gains `@Nullable String buyerStateCode`.
    Shop passes the seller's state code; web passes `request.buyerStateCode()`.
  - `WebInvoiceRequest.Line(String description, int quantity, BigDecimal unitRupees, @Nullable Long taxClassId,
    @Nullable Long webOrderLineId)`; `WebInvoiceRequest` gains `@Nullable String buyerStateCode`.
  - `public class CatalogTax { public @Nullable Long classFor(String productSlug) }`: the product's category
    tax class.
  - `IndianStates` lookup by state name to code (add `public static @Nullable String codeFor(@Nullable String
    name)` if not present; case-insensitive, trimmed).

- [ ] **Step 1: Write tests.** `WebInvoiceServiceIT`: `outOfStateOrderIsIgst` (seller Telangana 36, buyer
  Karnataka → `supplyType` INTER, igst > 0 with GST on); `slabPerLine` (₹2,800 and ₹2,000 lines inclusive →
  rates 18 and 5); `gstOffInvoiceHasHsnButNoTax`; `linesSumToInvoice` (line taxable/cgst/sgst/igst sums equal the
  invoice's). `InvoiceServiceIT`: `shopDiscountSpreadKeepsTotal` (order with discount: invoice total = order
  total, lines sum to taxable); `shopItemUsesServiceProductClass` (service product class "Tailoring service" →
  hsn 998821).
- [ ] **Step 2: Implement.** `insertInvoice` calls `TaxEngine.compute(lines, orderTotal, sellerStateCode,
  buyerStateCode, billing.gstApplies(), inclusive, taxClasses::resolve)`, writes per-line columns and the
  invoice's totals, `igst_rupees`, `place_of_supply` (buyer code for web, seller code for shop), `supply_type`,
  and `gst_rate` = `highestRate`. Shop `lines(Order)` sets `taxClassId` from `shopItemClass(item.templateId(),
  item.serviceProductId())`; the "Delivery" line is `shipping = true`. Web: the delivery line is `shipping =
  true`; add-on lines take their product's class. `CheckoutService.invoiceFor` fills `taxClassId` with
  `catalogTax.classFor(line.productSlug())`, `webOrderLineId` with the order line id (add it to the web order
  line read if missing), and `buyerStateCode` with `IndianStates.codeFor(order.state())`.
- [ ] **Step 3: Format and commit** `feat: invoices taxed per line with HSN, slabs and IGST`.

### Task 4: Credit notes

**Files:**
- Create: `money/CreditNote.java`, `CreditNoteLine.java`, `CreditNoteForm.java`, `CreditNoteSummary.java`,
  `CreditNotePage.java`, `StoreCreditNote.java`, `CreditNoteService.java`
- Modify: `money/InvoiceService.java` (`cancel` rule, `voidOrCredit`, `on(OrderEvent)`), `money/MoneyController.java`
- Modify: `money/WebInvoiceService.java` (`cancelFor` → `creditFor`)
- Modify: `checkout/CheckoutService.java` (cancel), `checkout/WebOrderReturns.java` (`refund`),
  the customer orders controller in `checkout` (credit-notes endpoint)
- Test: `money/CreditNoteServiceIT.java`

**Interfaces:**
- Consumes: invoice lines from Task 3 (`invoice_line.id`, tax columns, `web_order_line_id`).
- Produces:
  - `public enum CreditNote.Reason { RETURN, CANCELLED, CORRECTION }`
  - `record CreditNoteForm(@NotNull Reason reason, @Nullable @Size(max=200) String note, List<@Valid Line> lines)`
    with `record Line(long invoiceLineId, @Min(1) int quantity)`; empty lines = whole invoice.
  - `CreditNoteService` (`@Transactional`):
    - `public CreditNote issueFull(long invoiceId, Reason reason, @Nullable String note, @Nullable String actor)`
    - `public CreditNote issuePartial(long invoiceId, List<CreditNoteForm.Line> lines, Reason reason,
      @Nullable String note, @Nullable String actor)`
    - `public CreditNote issueForReturn(long returnId, long webOrderId, Map<Long, Integer> qtyByWebOrderLineId,
      @Nullable String actor)` — idempotent via unique `web_order_return_id`; maps web order lines to the live
      invoice's lines (add-on lines on the same `web_order_line_id` are credited in the same proportion).
    - `public CreditNote get(long id)`, `public CreditNotePage list(@Nullable String q, int page, int size)`,
      `public List<CreditNoteSummary> forInvoice(long invoiceId)`,
      `public List<StoreCreditNote> forCustomer(String orderNumber, long customerId)`.
  - `InvoiceService.cancel`: 409 "Issue a credit note instead" unless `issued_on` = today (IST) and no credit notes.
  - `InvoiceService.voidOrCredit(long invoiceId, @Nullable String actor)`: Ruling 4.
  - `WebInvoiceService.creditFor(long webOrderId, @Nullable String actor)`: full CANCELLED note on the live
    invoice if it has uncredited quantity; no-op without an invoice.
  - Endpoints: `POST /api/v1/admin/money/invoices/{id}/credit-notes` (body `CreditNoteForm`),
    `GET /api/v1/admin/money/credit-notes?q=&page=&size=`, `GET /api/v1/admin/money/credit-notes/{id}`,
    `GET /api/v1/admin/money/invoices/{id}/credit-notes`, `GET /api/v1/customer/orders/{number}/credit-notes`.
  - `Invoice` gains `List<CreditNoteSummary> creditNotes` (number, id, issuedOn, totalRupees).

Line arithmetic: for quantity q of an invoice line with quantity Q, each of amount, discount, taxable, CGST, SGST,
IGST = line value × q / Q HALF_UP to paisa; when q equals the line's remaining uncredited quantity, take line
value minus everything already credited for that line instead (exact remainder).

- [ ] **Step 1: Write `CreditNoteServiceIT`:** `fullNoteMirrorsInvoice` (totals equal invoice's, number
  `CN/26-27/0001` pattern), `partialNoteCreditsChosenLines`, `secondPartialCreditsOnlyRemainder` (line qty 3:
  credit 1 then 2 → second note's values = line − first; a third credit of 1 → 409) (Review Focus 2),
  `cancelledInvoiceRefused`, `returnNoteIsIdempotent`, `cancelRefusedAfterIssueDay` (issued yesterday → 409
  "Issue a credit note instead"), `webCancelIssuesFullNoteNotCancel`.
- [ ] **Step 2: Implement** the service and endpoints. Snapshot buyer/seller/place of supply/supply type from the
  invoice; title "Credit Note"; number from `NumberingService.next(NumberSequence.Kind.CREDIT_NOTE)`.
- [ ] **Step 3: Wire checkout.** `CheckoutService` cancel: replace `webInvoices.cancelFor(id, null)` with
  `webInvoices.creditFor(id, null)`. `WebOrderReturns.refund`: after the UPDATE, call `issueForReturn` with the
  return's lines (Ruling 2). Shop listener `on(OrderEvent)` CANCELLED → `voidOrCredit`.
- [ ] **Step 4: Format and commit** `feat(money): credit notes for returns, cancels and corrections`.

### Task 5: Tax classes on categories, garment templates and service products

**Files:**
- Modify: `catalog/CategoryService.java`, `catalog/CategoryResponse.java`; create `catalog/AdminCategoryController.java`
- Modify: `settings/GarmentTemplate.java`, `settings/ShopListService.java`, garment template form in `settings`
- Modify: `tailoring/ServiceProduct.java`, `ServiceProductForm.java`, `ServiceProductService.java`
- Test: `catalog/AdminCategoryControllerIT.java`

**Interfaces:**
- Produces:
  - `GET /api/v1/admin/categories` → `List<AdminCategory(long id, String name, @Nullable Long taxClassId)>`;
    `PUT /api/v1/admin/categories/{id}/tax-class` body `{ @Nullable Long taxClassId }` → `AdminCategory`.
  - Garment template and service product forms and responses gain `@Nullable Long taxClassId` (stored as-is; the
    FK rejects unknown ids → map the violation to 400 "Unknown tax class").
- [ ] **Step 1: Test** `setAndClearCategoryTaxClass`, `unknownTaxClassIs400`.
- [ ] **Step 2: Implement**; Task 1's `shopItemClass` reads these columns.
- [ ] **Step 3: Format and commit** `feat: pick tax classes on categories, garments and services`.

### Task 6: GSTR-1

**Files:**
- Create: `money/Gstr1.java`, `money/Gstr1Service.java`
- Modify: `money/MoneyController.java`, `reports/ReportService.java` (remove the `gst` report)
- Test: `money/Gstr1ServiceIT.java`

**Interfaces:**
- Produces:
  - `public record Gstr1(YearMonth month, boolean gstOn, List<Row> b2b, List<Row> b2cl, List<Row> b2cs,
    List<Row> cdnr, List<Row> cdnur, List<HsnRow> hsn, List<DocRange> docs)`; `Row` holds the columns of its
    section as a `Map<String, Object>` ordered as below, plus `taxable`, `igst`, `cgst`, `sgst`, `value` totals.
  - `Gstr1Service.month(YearMonth)`, `String csv(YearMonth, String section)` (section ∈ b2b, b2cl, b2cs, cdnr,
    cdnur, hsn, docs; else 404).
  - `GET /api/v1/admin/money/gstr1?month=2026-11`, `GET /api/v1/admin/money/gstr1/{section}.csv?month=2026-11`
    (`text/csv`, filename `gstr1-<section>-2026-11.csv`).

Columns (offline-tool order; dates `dd-MMM-yyyy`; place of supply `36-Telangana`):
- b2b: GSTIN/UIN of Recipient, Receiver Name, Invoice Number, Invoice date, Invoice Value, Place Of Supply,
  Reverse Charge (N), Applicable % of Tax Rate (blank), Invoice Type (Regular B2B), E-Commerce GSTIN (blank),
  Rate, Taxable Value, Cess Amount (0) — one row per invoice and rate.
- b2cl: Invoice Number, Invoice date, Invoice Value, Place Of Supply, Applicable % of Tax Rate, Rate, Taxable
  Value, Cess Amount, E-Commerce GSTIN — INTER, no buyer GSTIN, invoice total > 100000.
- b2cs: Type (OE), Place Of Supply, Applicable % of Tax Rate, Rate, Taxable Value, Cess Amount, E-Commerce GSTIN —
  all other unregistered, grouped by place and rate, net of their credit notes (Ruling 6).
- cdnr: GSTIN/UIN of Recipient, Receiver Name, Note Number, Note Date, Note Type (C), Place Of Supply, Reverse
  Charge, Note Supply Type (Regular B2B), Note Value, Applicable % of Tax Rate, Rate, Taxable Value, Cess Amount.
- cdnur: UR Type (B2CL), Note Number, Note Date, Note Type, Place Of Supply, Note Value, Applicable % of Tax Rate,
  Rate, Taxable Value, Cess Amount.
- hsn: HSN, Description (class name), UQC (PCS-PIECES for GOODS, NA for SERVICE), Total Quantity, Total Value,
  Rate, Taxable Value, Integrated Tax Amount, Central Tax Amount, State/UT Tax Amount, Cess Amount — net of all
  credit notes.
- docs: Nature of Document (Invoices for outward supply / Credit Note), Sr. No. From, Sr. No. To, Total Number,
  Cancelled.

Cancelled invoices are excluded from every table except docs. Periods by `issued_on`.

- [ ] **Step 1: Test** `groupsIntoSections` (one B2B, one B2CL > ₹1 lakh inter-state, two B2CS in one group),
  `creditNoteNetsB2cs`, `hsnSummaryNetsCredits`, `docsCountCancelled`, `csvHeaderOrder` (b2b header line equals
  the list above).
- [ ] **Step 2: Implement.** CSV via simple quoting (wrap fields with commas/quotes in `"`, double inner quotes).
- [ ] **Step 3: Format and commit** `feat(money): GSTR-1 summary and CSV`.

### Task 7 (controller): regenerate the API client

- [ ] Restart `VaabhiApplication` via IntelliJ MCP, run `pnpm generate:api` in `frontend/`, add hand-written
  wrappers in `frontend/packages/api-client/src/index.ts` for: `admin.money.taxClasses.{list,create,update,remove}`,
  `admin.categories.{list,setTaxClass}`, `admin.money.creditNotes.{list,get,forInvoice,issue}`,
  `admin.money.gstr1.{month,csvUrl}`, `customer.myCreditNotes(number)`. Commit `chore(api-client): regenerate
  for GST engine`.

### Task 8: Admin — tax setup

**Files:**
- Modify: `frontend/admin/src/app/settings/billing/page.tsx`, `frontend/admin/src/app/settings/garments/page.tsx`,
  `frontend/admin/src/app/price-list/page.tsx`
- Test: `frontend/admin/src/app/settings/settings.test.tsx` (update fixtures in `admin/src/test/settings-fixtures.ts`)

- [ ] **Step 1:** Billing page: "Tax classes" card (rows: name, HSN/SAC, rate text "5% up to ₹2,500, then 18%"
  or "5%"; add/edit sheet with name, kind, code, rate, optional limit and rate above; delete shows the 409 message);
  "Tax by category" list on the same card (category name + select, saving on change); "Default tax class" select
  replaces "Default GST rate"; note under the GST switch: "When on: each line is taxed by its tax class, orders
  to other states get IGST, and bills are titled Tax Invoice. Check the rates with your CA first."
- [ ] **Step 2:** Garments and Price list editors: "Tax class" select, empty option "Default (<default name>)".
- [ ] **Step 3:** Typecheck, lint, Prettier; commit `feat(admin): tax classes and pickers`.

### Task 9: Admin — invoices and credit notes

**Files:**
- Modify: `frontend/admin/src/app/money/invoices/view/page.tsx`, `frontend/admin/src/app/money/page.tsx` (tab),
  the online order view under `frontend/admin/src/app/online-orders/`
- Create: `frontend/admin/src/app/money/credit-notes/page.tsx`, `frontend/admin/src/app/money/credit-notes/view/page.tsx`,
  `frontend/admin/src/components/credit-note-sheet.tsx`

- [ ] **Step 1:** Invoice print: HSN/SAC and Rate columns, per-rate summary (Taxable, CGST, SGST or IGST),
  "Place of supply: Telangana (36)", amount in words (Indian numbering, "Rupees … only"); all hidden when the
  invoice's total tax is 0. Cancel button only when `issuedOn` is today and no credit notes.
- [ ] **Step 2:** "Credit note" button → sheet: Whole invoice / Choose lines (quantity steppers capped at
  remaining), reason Return or Correction, note; lists notes issued under the invoice.
- [ ] **Step 3:** Credit notes list (search, number, date, invoice, amount, empty state) and view/print titled
  "Credit Note" with "Against invoice INV/…". Money tab "Credit notes". Online order view: credit note links.
- [ ] **Step 4:** Typecheck, lint, Prettier; commit `feat(admin): credit notes and GST invoice print`.

### Task 10: Admin — GST report page

**Files:**
- Create: `frontend/admin/src/app/reports/gst/page.tsx`
- Modify: `frontend/admin/src/app/reports/page.tsx` (card linking to it)

- [ ] **Step 1:** Month picker (default this month), banner "GST is off: these figures are for practice" when
  `gstOn` false, the seven sections with a totals row each, "Download CSV" per section via `csvUrl`, honest empty
  state per section ("No invoices in this section").
- [ ] **Step 2:** Typecheck, lint, Prettier; commit `feat(admin): GSTR-1 report page`.

### Task 11: Storefront — credit notes

**Files:**
- Create: `frontend/storefront/src/app/api/auth/orders/[number]/credit-notes/route.ts`,
  `frontend/storefront/src/app/account/orders/[number]/credit-notes/[cn]/page.tsx`,
  `frontend/storefront/src/components/auth/credit-note-view.tsx`
- Modify: the account order view (`orders-view`) and `invoice-view.tsx` (HSN/rate columns when taxed)

- [ ] **Step 1:** Same pattern as the invoice page: client loader through the `/api/auth` route; "View credit
  note" links per note on the order page; printable view with print button, `print:hidden` chrome.
- [ ] **Step 2:** Typecheck, lint, Prettier; commit `feat(storefront): customers can view credit notes`.

### Task 12 (controller): final review and handoff

- [ ] Whole-branch review (most capable model), one fix wave, re-review.
- [ ] Update PR #48 title/body to cover Phase 1, push (pre-push formatting), update memory handoff, report the
  rulings and the spec's 10 manual checks to the owner.
