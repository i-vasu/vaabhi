# Phase 1: GST engine and credit notes

Date: 2026-11-12. Plan: "Vaabhi books without accounting software", Phase 1. Builds on Phase 0 (PR #48).
Done when: every invoice line carries its HSN/SAC code and rate, out-of-state web orders carry IGST, returns and
post-ship cancels produce credit notes, and a month's GSTR-1 tables download as CSV. With GST off (today: no
GSTIN), bills print exactly as now.

## Decisions

| Question | Decision |
| --- | --- |
| Build now while unregistered | Yes, all of it; it switches on when a GSTIN is set. |
| How a rate is chosen | Tax classes with an optional price slab (e.g. 5% up to ₹2,500 a piece, then 18%). |
| Web lines | Class from the product's category. |
| Shop lines | Class from the garment template or service product; else the default class. |
| Credit notes | Automatic for web (processed return refund, cancel after ship); button for shop invoices. Cancel only on the day of issue. |
| Reports | GSTR-1 summary page with CSV per table. No portal JSON. |
| Storage | Tax stored per invoice line (approach A), frozen at issue. |

Rates in the seed data are placeholders; a CA confirms them before GST is switched on.

Out of scope: debit notes, e-invoicing/IRN, GSTR-1 JSON, reverse charge, shop refunds as ledger rows, journal
entries for credit notes (Phase 4).

## Data (migration V31)

- `tax_class`: `id`, `name` (≤60, unique), `kind` (`GOODS` | `SERVICE`), `hsn_code` (4–8 digits),
  `rate_percent` NUMERIC(5,2), `slab_limit_rupees` NUMERIC(12,2) NULL, `rate_above_percent` NUMERIC(5,2) NULL
  (both or neither). Seed: "Garments" (GOODS, 6204, 5, limit 2500, above 18), "Tailoring service" (SERVICE,
  998821, 5).
- `category.tax_class_id`, `garment_template.tax_class_id`, `service_product.tax_class_id`: nullable FK to
  `tax_class`.
- Billing settings JSON: `defaultTaxClassId` (nullable) replaces `defaultGstRate` (kept in JSON for reading old
  rows, no longer used). Backfill: the "Garments" class.
- `invoice_line` gains `hsn_code`, `gst_rate`, `taxable_rupees`, `cgst_rupees`, `sgst_rupees`, `igst_rupees`,
  `web_order_line_id` (nullable FK). `invoice` gains `igst_rupees` (default 0), `place_of_supply` (2-char state
  code), `supply_type` (`INTRA` | `INTER`).
- Backfill old invoices: `supply_type = INTRA`, `place_of_supply = seller_state_code` (null allowed), lines get the
  invoice's rate, and the invoice's taxable/CGST/SGST spread across lines in proportion to `amount`, the last line
  taking the remainder so the sums match.
- `credit_note`: `id`, `number` (unique, from `CREDIT_NOTE` series `CN/{FY}/`), `invoice_id`, `reason`
  (`RETURN` | `CANCELLED` | `CORRECTION`), `note` (≤200), `issued_on`, `web_order_return_id` (nullable, unique),
  the same snapshot columns as `invoice` (title "Credit Note", buyer/seller blocks, place of supply, supply type),
  totals (`taxable`, `cgst`, `sgst`, `igst`, `total`), `created_by`, `created_at`.
- `credit_note_line`: `credit_note_id`, `position`, `invoice_line_id`, `description`, `hsn_code`, `quantity`,
  `unit_rupees`, `gst_rate`, `taxable_rupees`, `cgst_rupees`, `sgst_rupees`, `igst_rupees`, `amount_rupees`.

## Code

### money module

- `TaxClassService`: list, create, update, delete (409 when referenced). `resolve(@Nullable Long classId,
  BigDecimal unitPrice)` → `(hsnCode, rate)`: falls back to the default class; applies `rate_above` when
  `unitPrice > slab_limit`.
- `TaxEngine.compute(lines, shipping, sellerStateCode, buyerStateCode, gstApplies, pricesIncludeTax)`, pure:
  - Discount spread across lines by value before resolving the slab; slab tested on the discounted unit price.
  - Same state (or either code null) → CGST + SGST, halves, SGST takes the odd paisa; else IGST at the full rate.
  - Shipping is its own line, SAC 9965, at the highest goods rate on the invoice.
  - `gstApplies` false → every rate 0, HSN codes still recorded.
  - Replaces `InvoiceService.tax()`; with one rate and intra-state, results equal today's.
- `InvoiceService.issue` (shop): class per item from its template or service product; place of supply = seller
  state.
- `WebInvoiceRequest.Line` gains `taxClassId` and `webOrderLineId`; request gains `buyerStateCode` (from
  `IndianStates`, unknown → null, logged).
- `CreditNoteService`:
  - `issueFull(invoiceId, reason, note, actor)` and `issuePartial(invoiceId, List<CreditLine(invoiceLineId,
    quantity)>, reason, note, actor)`. Tax per line split pro rata from the invoice line; a line's credited quantity
    never exceeds its invoiced quantity across all notes (409). None on a cancelled invoice (409).
  - `issueForReturn(returnId, …)` is idempotent via the unique `web_order_return_id`.
  - Runs in the caller's transaction (`MANDATORY`), number from `NumberingService`.
- `InvoiceService.cancel`: allowed only when `issued_on` is today (IST) and the invoice has no credit notes; else
  409 "Issue a credit note instead".
- `Gstr1Service.month(YearMonth)`: B2B, B2CL (inter-state, unregistered, invoice total > ₹1,00,000), B2CS (by place
  of supply and rate), CDNR/CDNUR, HSN summary, document ranges. `csv(YearMonth, section)`. Replaces the GST
  register in `ReportService`.
- Public `StoreCreditNote` + `CreditNoteService.forCustomer(orderNumber, customerId)`.

### catalog module

- Category form and response gain `taxClassId`. Public `CatalogTax.classFor(productSlug)` → `@Nullable Long`.

### tailoring module

- Garment template and service product forms and responses gain `taxClassId`.

### checkout module

- `ShiprocketShipping`/`deliver()`: fill `taxClassId`, `webOrderLineId` and `buyerStateCode` in the request.
- Return refund becomes `PROCESSED` → `CreditNoteService.issueForReturn` in the same transaction.
- Cancel of an order with a live invoice → `issueFull(…, CANCELLED)` instead of `cancelFor`.
- `GET /api/v1/customer/orders/{number}/credit-notes` → list of `StoreCreditNote`.

### admin

- Settings → Billing: Tax classes card (list, add/edit sheet, delete); Default tax class picker replaces Default
  GST rate; note under the GST switch.
- Tax class select on the category editor, Settings → Garments and the Price list; empty = "Default (<name>)".
- Invoice print: HSN/SAC and rate columns, per-rate tax summary, place of supply, amount in words; hidden when the
  invoice has no tax.
- Invoice view: Credit note button and sheet (whole invoice or lines with quantity steppers, reason, note), list of
  notes issued; Cancel shown only on the day of issue.
- Money → Credit notes tab: list, search, view/print ("Credit Note").
- Online order view: credit note links.
- Reports → GST: month picker, the six GSTR-1 sections with totals and Download CSV, "GST is off" banner.

### storefront

- Account order page: "View credit note" links; `/account/orders/[number]/credit-notes/[cn]` printable page via an
  `/api/auth` route, same pattern as the invoice page.

## Errors

- No class and no default while GST is on → issue fails: "Set a default tax class in Settings → Billing".
- Over-credit, credit on a cancelled invoice, late cancel → 409 with a plain message.
- Credit note failure on refund or cancel rolls back the status change.
- Unknown buyer state → treated as intra-state, logged.

## Testing

Written, not run:
- `TaxEngine`: slab at exactly ₹2,500 and ₹2,500.01, discount spread changing the slab, IGST vs CGST/SGST,
  odd-paisa split, shipping at highest rate, GST off gives zeros, single-rate result equals the old `tax()`.
- `CreditNoteService`: full, partial, second partial up to the remainder, over-credit refused, cancelled invoice
  refused, return idempotency.
- Cancel refused after the day of issue.
- `Gstr1Service`: grouping into B2B, B2CL, B2CS, HSN.

Owner checks by hand:
1. Settings → Billing shows the two seeded tax classes and the default; edit one; save; reload.
2. Pick tax classes on a category, a garment template and a service product.
3. With GST off, ship a web order: the invoice prints as before; lines have HSN codes stored.
4. Turn GST on with a test GSTIN (dev only): ship a web order to Telangana → CGST + SGST; to Karnataka → IGST; a
   ₹2,800 kurta shows 18%, a ₹2,000 one 5%.
5. Process a return refund → credit note CN/26-27/0001 for the returned lines; visible in admin and on the
   customer's order page.
6. Cancel a shipped order → full credit note, invoice not cancelled.
7. Shop invoice → Credit note → two lines → CN issued; a second note can only credit the rest.
8. Cancel a shop invoice issued yesterday → refused with "Issue a credit note instead".
9. Reports → GST for this month: sections add up to the invoice list; CSVs open in a spreadsheet.
10. Old invoices still print and appear in the report.
