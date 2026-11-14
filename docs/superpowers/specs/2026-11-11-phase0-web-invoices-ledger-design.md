# Phase 0: every sale has a bill and lands in the ledger

Date: 2026-11-11. Plan: "Vaabhi books without accounting software", Phase 0.
Done when: every shop and web order paid this month has a numbered bill, and the ledger total matches Razorpay plus
counter takings.

## Decisions

| Question | Decision |
| --- | --- |
| When a web order is invoiced | When it ships (GST: at or before goods leave). Cancelled-before-ship orders never get one. |
| Web money in the ledger | Two new modes, `RAZORPAY` and `COD`, so per-mode totals reconcile against Razorpay and Shiprocket. |
| Who sees a web invoice | Admin (existing invoice view) and the customer (account order page, printable). |
| Delivery-state switch | Skipped for now. |
| Company details | New "Company" card on Settings → Billing. |
| Invoice table | One table for shop and web invoices, one number series (`INV/{FY}/`). |

Out of scope (later phases): credit notes, GST engine (HSN, IGST), journal, emailed/WhatsApp PDFs, state switch.

## Data (migration V30)

- `invoice`:
  - `order_id`, `client_id` become nullable; add `web_order_id BIGINT REFERENCES web_order (id)`.
  - `CHECK (num_nonnulls(order_id, web_order_id) = 1)`.
  - `CREATE UNIQUE INDEX ... ON invoice (web_order_id) WHERE cancelled_at IS NULL` (one live invoice per web order).
  - Add `party_id BIGINT REFERENCES party (id)`, set for both kinds.
  - Snapshot columns, frozen at issue: `title`, `buyer_name`, `buyer_phone`, `buyer_address`, `buyer_state`,
    `seller_name`, `seller_address`, `seller_cin`, `seller_state`, `seller_state_code`.
  - Backfill existing rows: buyer from `client`, seller from current shop/billing settings, title from GST status,
    `party_id` from `client.party_id`.
- Billing settings JSON gains `company: {legalName, cin, registeredOffice, state}`, all optional.
- No new ledger table. COD orders already get `paid_at` when delivered; online orders when paid.

## Code

### money module

- `PaymentMode` gains `RAZORPAY`, `COD` (ledger only; the counter payment form keeps CASH/UPI/CARD/BANK).
- `LedgerEntry.Kind` gains `WEB_PAYMENT`, `WEB_REFUND`.
- `LedgerService.MOVEMENTS` gains:
  - `web_order` with `paid_at IS NOT NULL` → `WEB_PAYMENT`, mode `RAZORPAY` (ONLINE) or `COD`, at `paid_at`, amount
    `total_rupees`, reference `number`, party `contact_name`.
  - `web_order` with `refund_status = 'PROCESSED'` → `WEB_REFUND`, mode `RAZORPAY`, at `refunded_at`,
    amount `-refunded_rupees`.
  - `web_order_return` refunds processed → `WEB_REFUND` the same way (reference = return number).
- New public `WebInvoiceService.issueFor(WebInvoiceRequest)`: returns the order's live invoice or issues one in the
  caller's transaction. `WebInvoiceRequest` = web order id + number, party id, buyer name/phone/address/state, lines
  (description, qty, unit), shipping. Tax: same `InvoiceService.tax` and billing settings as shop invoices.
  `money` does not import `checkout`.
- Shared snapshot builder used by shop and web issue: title = "Tax Invoice" when `billing.gstApplies()`, else
  "Invoice"; seller name = company legal name, else shop name; seller address = registered office, else shop address.
- `issued_on` = today in Asia/Kolkata (both kinds).
- `Invoice` response gains the snapshot fields and `webOrderNumber`; invoice list shows Shop/Web.
- New public `StoreInvoice` record (no admin-only fields) + `WebInvoiceService.forCustomer(orderNumber, customerId)`.

### settings module

- `BillingSettings.company` record: `legalName` (≤160), `cin` (`^[LU][0-9]{5}[A-Z]{2}[0-9]{4}[A-Z]{3}[0-9]{6}$`),
  `registeredOffice` (≤300), `state` (one of the state list).
- `IndianStates`: fixed list of states/UTs with GST codes (e.g. Telangana 36), exposed for the dropdown.

### checkout module

- `ShiprocketShipping.ship`: before `createOrder`, call `issueFor` in its own transaction; pass the invoice number
  to `ShiprocketClient.createOrder`. Send it as Shiprocket's invoice-number field if its create-order API has one
  (check the docs when planning); otherwise put "Invoice <number>" in the order `comment`.
- `CheckoutService.ship` (manual): call `issueFor` in the same transaction (no-op if one exists).
- Cancelling a web order that has a live invoice cancels the invoice.
- Customer endpoint `GET /api/v1/customer/orders/{number}/invoice` → `StoreInvoice`; 404 when not theirs or none.

### admin

- Billing page: "Company" card (legal name, CIN, registered office, state dropdown showing the GST code).
- Web order view: "Invoice INV/26-27/0007" link once issued.
- Invoice view prints from snapshot fields (title, seller block with CIN and state code, buyer block).
- Ledger filters include the two new kinds; mode labels for Razorpay and COD.

### storefront

- Account order page: "View invoice" when the order has one.
- `/account/orders/[number]/invoice`: server-rendered printable page, print button.

## Errors

- Invoice issue failure aborts the ship; nothing is sent to Shiprocket.
- Double ship click reuses the live invoice; the partial unique index stops a race.
- Customer invoice endpoint returns 404 for someone else's order or no invoice.
- Empty company fields are left off the print; names fall back to the shop's.

## Testing

Unit tests added, not run: title choice, state codes, CIN pattern, web invoice lines. Typecheck and lint the
frontend. Owner checks by hand:

1. Fill in the Company card; save; reload.
2. Ship an online order through Shiprocket → invoice `INV/26-27/000N` in admin, with the company block; Shiprocket
   order shows the invoice number.
3. Account page of that order → "View invoice" → printable page.
4. Ship and deliver a COD order → ledger shows a `COD` row on the delivery date.
5. Ledger mode totals: Razorpay total equals the Razorpay dashboard for the range.
6. Return or cancel-refund an online order → ledger `WEB_REFUND` row once the refund is processed.
7. A shop order invoice still issues on delivery and prints the new blocks.
