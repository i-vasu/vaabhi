# Phase 0: web invoices and web money in the ledger — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every web order gets a numbered invoice when it ships, and web payments and refunds appear in the ledger
under the modes Razorpay and COD.

**Architecture:** One `invoice` table for shop and web orders, with buyer/seller snapshots. `checkout` calls a new
public `money.WebInvoiceService`; `money` never imports `checkout` (it reads `web_order` tables in SQL, as the ledger
already reads tailoring tables). Company details live in billing settings.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Modulith, JdbcClient, Postgres + Flyway; Next.js 16 admin (static
export) and storefront (server-rendered), generated `@vaabhi/api-client`.

**Spec:** `docs/superpowers/specs/2026-11-11-phase0-web-invoices-ledger-design.md`

## Global Constraints

- CLAUDE.md: **do not run tests**. Write the tests named below; do not run them. Allowed checks: IntelliJ build
  (`build_project` via the IntelliJ MCP), `pnpm typecheck`, `pnpm lint`.
- Backend is started only through the IntelliJ MCP run configuration `VaabhiApplication`.
- Never hand-edit `frontend/packages/api-client/src/schema.d.ts` or `openapi.json`; regenerate with
  `pnpm generate:api` against the running backend.
- No hardcoded content in the frontend; empty fields are left off the print, never placeholders.
- Next migration number is **V30**.
- Shop dates in `Asia/Kolkata`.
- Invoice title: `"Tax Invoice"` when `billing.gstApplies()`, else `"Invoice"`.
- CIN pattern: `^[LU][0-9]{5}[A-Z]{2}[0-9]{4}[A-Z]{3}[0-9]{6}$`.
- Shiprocket: the invoice number goes in the create-order `comment` as `"Invoice <number>"` (no documented
  invoice-number field was found).
- Commit after each task, message ending with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. Shiprocket fails after the invoice is issued, then staff retry → the same invoice is reused, no second number.
   (Task 4 test `shipRetryReusesInvoice`.)
2. Two ship requests race → partial unique index rejects the second insert; `issueFor` re-reads and returns the live
   one. (Task 3 test `issueForReturnsLiveInvoice`.)
3. Customer asks for another customer's invoice → 404, same as "no invoice". (Task 4 test `invoiceOfOtherCustomer404`.)
4. Return refunded MANUALLY (not Razorpay) → ledger mode `BANK`, not `RAZORPAY`. (Task 5 test in `LedgerServiceIT`.)
5. Existing shop invoices after V30 → still print, with backfilled buyer/seller blocks. (Task 1 migration backfill;
   Task 6 view handles nullable fields.)

---

### Task 1: Migration V30 and company settings

**Files:**
- Create: `backend/src/main/resources/db/migration/V30__web_invoices.sql`
- Create: `backend/src/main/java/com/vaabhi/settings/IndianStates.java`
- Modify: `backend/src/main/java/com/vaabhi/settings/BillingSettings.java` (add `company`, update `defaults()`)
- Modify: `backend/src/main/java/com/vaabhi/settings/SettingsController.java` (GET `/api/v1/admin/settings/states`)
- Test: `backend/src/test/java/com/vaabhi/settings/IndianStatesTest.java`, `BillingSettingsValidationTest.java`

**Interfaces:**
- Produces: `record BillingSettings.Company(@Nullable @Size(max=160) String legalName, @Nullable @Pattern(CIN) String
  cin, @Nullable @Size(max=300) String registeredOffice, @Nullable String state)`; `BillingSettings.company()` (never
  null; `defaults()` gives all-null Company). `IndianStates.code(String state) -> Optional<String>` (two digits),
  `IndianStates.all() -> List<State>` with `record State(String name, String code)`. Unknown `state` in a save → 400.
- Produces columns on `invoice`: `web_order_id`, `party_id`, `title`, `buyer_name`, `buyer_phone`, `buyer_address`,
  `buyer_state`, `seller_name`, `seller_address`, `seller_cin`, `seller_state`, `seller_state_code`.

- [ ] **Step 1: Write tests.** `IndianStatesTest`: `code("Telangana") == "36"`, `code("Karnataka") == "29"`,
  `code("Atlantis")` empty, `all()` names unique and every code two digits (current 28 states + 8 UTs with GSTN
  state codes; Dadra & Nagar Haveli and Daman & Diu = `"26"`). `BillingSettingsValidationTest`: CIN `U74999TG2026OPC123456` valid,
  `U74999TG26OPC123456` invalid.
- [ ] **Step 2: Write V30.**
  - `ALTER TABLE invoice ALTER COLUMN order_id DROP NOT NULL, ALTER COLUMN client_id DROP NOT NULL`.
  - Add `web_order_id BIGINT REFERENCES web_order (id)`, `party_id BIGINT REFERENCES party (id)`.
  - `CHECK (num_nonnulls(order_id, web_order_id) = 1)`.
  - `CREATE UNIQUE INDEX invoice_live_web_order_idx ON invoice (web_order_id) WHERE cancelled_at IS NULL AND
    web_order_id IS NOT NULL`.
  - Snapshot columns: `title VARCHAR(20)`, `buyer_name VARCHAR(160)`, `buyer_phone VARCHAR(16)`,
    `buyer_address VARCHAR(400)`, `buyer_state VARCHAR(80)`, `seller_name VARCHAR(160)`, `seller_address VARCHAR(300)`,
    `seller_cin VARCHAR(21)`, `seller_state VARCHAR(80)`, `seller_state_code CHAR(2)`; backfill, then `title` and
    `buyer_name`, `seller_name` `SET NOT NULL`.
  - Backfill from `client` (name, phone, `concat_ws(', ', address, city, pin_code)`, state, `party_id`) and from the
    `shop_settings` row for profile (`shopName`, `address`, `state`); title `'Tax Invoice'` where `shop_gstin IS NOT
    NULL`, else `'Invoice'`. Read the existing settings storage (`settings/SettingsService.java` Section "profile")
    to get the JSON path.
- [ ] **Step 3: Implement** `IndianStates`, `Company`, the states endpoint, and validation of `company.state` in the
  billing save path.
- [ ] **Step 4: Verify** — IntelliJ `build_project` succeeds.
- [ ] **Step 5: Commit** `feat(settings): company details and state codes for invoices`.

### Task 2: Shop invoices carry snapshots (money)

**Files:**
- Create: `backend/src/main/java/com/vaabhi/money/InvoiceParties.java` (snapshot builder, package-private)
- Modify: `money/InvoiceService.java` (`issue`, `get`, `list`, `issuedOn`), `money/Invoice.java`,
  `money/InvoiceSummary.java`
- Test: `backend/src/test/java/com/vaabhi/money/InvoicePartiesTest.java`

**Interfaces:**
- Consumes: `BillingSettings.company()`, `IndianStates.code`, `SettingsService.profile()`.
- Produces: `record InvoiceParties.Seller(String name, @Nullable String address, @Nullable String cin, @Nullable
  String state, @Nullable String stateCode)`, `record InvoiceParties.Buyer(String name, @Nullable String phone,
  @Nullable String address, @Nullable String state)`; `InvoiceParties.seller(ShopProfile, BillingSettings) -> Seller`;
  `InvoiceParties.title(BillingSettings) -> String`; `InvoiceParties.today(Clock) -> LocalDate` (Asia/Kolkata).
- Produces: `Invoice` becomes `(long id, String number, Source source, @Nullable Long orderId, String orderNumber,
  @Nullable ClientSummary client, String title, InvoiceParties.Seller seller, InvoiceParties.Buyer buyer, …rest
  unchanged)` with `public enum Source { SHOP, WEB }`; `InvoiceParties.Seller/Buyer` made public records.
  `InvoiceSummary`: `Source source`, `@Nullable Long orderId`, `@Nullable Long clientId`; `clientName` = buyer name.
  For WEB rows `orderNumber` = web order number, `paidRupees` = total when the web order has `paid_at`, else 0.

- [ ] **Step 1: Write `InvoicePartiesTest`:** title is `"Invoice"` with `chargeGst=false`, `"Tax Invoice"` with
  `chargeGst=true` and a GSTIN; seller name = legal name when set, else shop name; seller address = registered
  office when set, else shop address; state code `"36"` for Telangana; `today` at `2027-03-31T19:00Z` is
  `2027-04-01`.
- [ ] **Step 2: Implement** `InvoiceParties`; `issue` writes the snapshot columns and `party_id` (from
  `client.party_id`) and uses `today(clock)`; `get`/`list` read snapshots and LEFT JOIN `shop_order` / `web_order`
  for the order number.
- [ ] **Step 3: Verify** — `build_project` succeeds.
- [ ] **Step 4: Commit** `feat(money): invoices keep buyer and seller snapshots`.

### Task 3: WebInvoiceService (money)

**Files:**
- Create: `money/WebInvoiceService.java`, `money/WebInvoiceRequest.java`, `money/StoreInvoice.java`
- Modify: `money/InvoiceService.java` (extract the line-insert + tax code into a package-private method both use)
- Test: `backend/src/test/java/com/vaabhi/money/WebInvoiceServiceIT.java`

**Interfaces:**
- Produces: `public record WebInvoiceRequest(long webOrderId, String orderNumber, @Nullable Long partyId,
  InvoiceParties.Buyer buyer, List<Line> lines, BigDecimal shippingRupees)` with `public record Line(String
  description, int quantity, BigDecimal unitRupees)`.
- Produces on `WebInvoiceService` (public):
  - `@Transactional Invoice issueFor(WebInvoiceRequest r)` — live invoice if any; else issues (number from
    `NumberingService.next(INVOICE)`, lines + a `"Delivery"` line when shipping > 0, tax via `InvoiceService.tax`
    on items + shipping). On `DuplicateKeyException` from the partial index, re-read and return the live one.
  - `Map<Long, String> liveNumbers(Collection<Long> webOrderIds)` — web order id → live invoice number.
  - `void cancelFor(long webOrderId, @Nullable String actor)` — cancels the live one; no-op when none.
  - `Optional<StoreInvoice> forCustomer(String orderNumber, long customerId)` — joins `web_order.customer_id`.
- Produces: `public record StoreInvoice(String number, String title, LocalDate issuedOn, InvoiceParties.Seller seller,
  InvoiceParties.Buyer buyer, String orderNumber, List<InvoiceLine> lines, BigDecimal taxableRupees, BigDecimal
  cgstRupees, BigDecimal sgstRupees, BigDecimal totalRupees, @Nullable String shopGstin, boolean cancelled)`.

- [ ] **Step 1: Write `WebInvoiceServiceIT`** (Testcontainers, like other `*IT`): `issueForCreatesInvoice` (number
  starts `INV/`, 2 item lines + Delivery line, total = items + shipping when prices include tax);
  `issueForReturnsLiveInvoice` (second call returns the same id); `cancelForThenIssueGivesNewNumber`;
  `forCustomerOtherCustomerEmpty`.
- [ ] **Step 2: Implement.**
- [ ] **Step 3: Verify** — `build_project` succeeds.
- [ ] **Step 4: Commit** `feat(money): invoices for web orders`.

### Task 4: Checkout issues invoices at ship time

**Files:**
- Modify: `checkout/ShiprocketShipping.java` (`ship`), `checkout/ShiprocketClient.java` (`createOrder` takes the
  invoice number), `checkout/CheckoutService.java` (`ship`, `cancel`, `cancelByCustomer`, order reads),
  `checkout/WebOrder.java` (add `@Nullable String invoiceNumber`), `checkout/CheckoutController.java`
- Test: `backend/src/test/java/com/vaabhi/checkout/WebOrderInvoiceIT.java`

**Interfaces:**
- Consumes: `WebInvoiceService.issueFor / liveNumbers / cancelFor / forCustomer`.
- Produces: `CheckoutService.invoiceFor(String number) -> Invoice` (package-private; builds `WebInvoiceRequest` from
  the order: buyer = contact name/phone, `concat_ws(', ', line1, line2, city, pincode)`, state; party =
  `customer.party_id`; lines from `web_order_line` names, qty, unit price, add-on as its own line if present).
  `ShiprocketClient.createOrder(WebOrder order, BigDecimal weightKg, String invoiceNumber)` puts
  `comment = "Invoice " + invoiceNumber`.
  `GET /api/v1/customer/orders/{number}/invoice -> StoreInvoice` (404 `"No invoice for this order"` otherwise).

- [ ] **Step 1: Write `WebOrderInvoiceIT`:** `manualShipIssuesInvoice`; `shipRetryReusesInvoice` (invoiceFor twice →
  same number); `cancelBeforeShipWithInvoiceCancelsIt`; `invoiceOfOtherCustomer404`; `orderListShowsInvoiceNumber`.
- [ ] **Step 2: Implement.** In `ShiprocketShipping.ship`, call `orders.invoiceFor(number)` inside a
  `TransactionTemplate` before `createOrder`. `CheckoutService.ship` calls `invoiceFor` in its transaction.
  Both cancel paths call `cancelFor` when the order had an invoice. `orders()` / `order()` / admin reads fill
  `invoiceNumber` via `liveNumbers`.
- [ ] **Step 3: Verify** — `build_project` succeeds; Modulith: `money` has no `import com.vaabhi.checkout`
  (`grep -r "com.vaabhi.checkout" backend/src/main/java/com/vaabhi/money` prints nothing).
- [ ] **Step 4: Commit** `feat(checkout): invoice web orders when they ship`.

### Task 5: Web money in the ledger

**Files:**
- Modify: `tailoring/PaymentMode.java` (add `RAZORPAY`, `COD`), `money/LedgerEntry.java` (add `WEB_PAYMENT`,
  `WEB_REFUND`), `money/LedgerService.java` (`MOVEMENTS`)
- Modify: every counter payment form validator that accepts `PaymentMode` — reject `RAZORPAY`/`COD` with 400
  `"Pick cash, UPI, card or bank"` (find with `grep -rn "PaymentMode" backend/src/main/java --include=*Form.java`).
- Test: `backend/src/test/java/com/vaabhi/money/LedgerServiceIT.java` (add cases)

**Interfaces:**
- Produces three `UNION ALL` parts in `MOVEMENTS`:
  - `web_order` where `paid_at IS NOT NULL` → `'WEB_PAYMENT'`, `w.id`, `w.number`, `w.contact_name`,
    mode `CASE payment_method WHEN 'COD' THEN 'COD' ELSE 'RAZORPAY' END`, `total_rupees`, at `paid_at`.
  - `web_order` where `refund_status = 'PROCESSED'` → `'WEB_REFUND'`, mode `'RAZORPAY'`, `-refunded_rupees`, at
    `refunded_at`.
  - `web_order_return` where `status = 'REFUNDED'` (or `refund_status = 'PROCESSED'` for Razorpay) →
    `'WEB_REFUND'`, reference = return number, mode `CASE refund_method WHEN 'RAZORPAY' THEN 'RAZORPAY' ELSE 'BANK'
    END`, `-refund_rupees`, at `refunded_at`.
  - Check `LedgerService.outstanding` / dues queries ignore web orders (they read shop tables only — leave them).

- [ ] **Step 1: Add IT cases:** `onlinePaidOrderIsRazorpayRow`, `codDeliveredIsCodRow`,
  `processedRefundIsNegativeRow`, `manualReturnRefundIsBankRow`, `pendingOnlineOrderNotInLedger`.
- [ ] **Step 2: Implement.**
- [ ] **Step 3: Verify** — `build_project` succeeds.
- [ ] **Step 4: Commit** `feat(money): web payments and refunds in the ledger`.

### Task 6: Admin screens

**Pre-step:** restart `VaabhiApplication` via the IntelliJ MCP, run `pnpm generate:api` in `frontend/`.

**Files:**
- Modify: `frontend/admin/src/app/settings/billing/page.tsx` (Company card), `frontend/admin/src/lib/` settings
  API file (states call), `frontend/admin/src/app/money/invoices/view/page.tsx` (title, seller and buyer blocks,
  WEB link to `/online-orders/view/?number=`), `money/invoices/page.tsx` (Shop/Web chip),
  `money/ledger/page.tsx` (kind filters `WEB_PAYMENT`/`WEB_REFUND`, mode labels "Razorpay", "COD"),
  `online-orders/view/page.tsx` ("Invoice <number>" link to `/money/invoices/view/?id=`), test fixtures for new
  required fields.
- Test: extend `settings.test.tsx` (`saves company details`), `money.test.tsx` (`prints seller CIN and state code`).

- [ ] **Step 1: Add the two tests.**
- [ ] **Step 2: Implement.** State dropdown options read from the states endpoint, labelled `"Telangana (36)"`.
  The view hides any empty seller/buyer line.
- [ ] **Step 3: Verify** — `pnpm --filter admin typecheck` and `pnpm --filter admin lint` pass.
- [ ] **Step 4: Commit** `feat(admin): company details, web invoices and web ledger rows`.

### Task 7: Storefront invoice page

**Files:**
- Modify: `frontend/storefront/src/lib/api.ts` (`myInvoice(number)`), `frontend/storefront/src/app/account/orders/page.tsx`
  ("View invoice" link when `invoiceNumber`)
- Create: `frontend/storefront/src/app/account/orders/[number]/invoice/page.tsx` (server-rendered, uses the session
  like the orders page; `notFound()` on 404), `.../invoice/print-button.tsx` (client component calling
  `window.print()`)
- Test: `frontend/storefront/src/app/account/orders/invoice.test.tsx` (`renders title, lines and total`)

- [ ] **Step 1: Add the test.**
- [ ] **Step 2: Implement.** Print styles hide site chrome (`print:hidden` on header/footer).
- [ ] **Step 3: Verify** — `pnpm --filter storefront typecheck` and `lint` pass.
- [ ] **Step 4: Commit** `feat(storefront): customers can view and print their invoice`.

### Hand-off

List for the owner the seven manual checks in the spec's Testing section.
