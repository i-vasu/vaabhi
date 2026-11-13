# Books Phase 4: purchases, payables, TDS, fixed assets

Date: 2026-11-13. Branch `feat/id-engine-parties` (PR #48, all books phases in one PR). Plan: "Vaabhi books without
accounting software", Phase 4.

## Goal

Vaabhi shows what is owed to each supplier and the input GST for every month. Supplier bills (typed or imported from
Shiprocket) post to the ledger with input GST and TDS; supplier payments clear them; stock bought on a bill lands in
inventory; machines and furniture sit in an asset register that depreciates itself monthly; TDS deducted is tracked
until a challan pays it.

## Decisions

| Topic | Decision |
| --- | --- |
| Bills vs expenses | Both stay. Expenses: small spends paid on the spot with no bill to track. Bills: anything from a supplier with a bill number, GST or a due date. A bill can be "paid now" (bill + payment in one step). Payroll keeps writing SALARY expenses. |
| TDS | Editable TDS sections with rate and yearly threshold; optional default section per supplier; TDS pre-filled on a bill and editable; a warning (never an automatic deduction) when the supplier's year total crosses the threshold; a challan entry clears TDS payable. Salary TDS (192), PF and ESI are out of scope. |
| Shiprocket | One supplier with a running Creditors balance. Freight invoices imported from CSV/XLSX become bills. Wallet recharges are supplier payments. The COD remittance deduction (Phase 3) now debits Creditors (Shiprocket) instead of Courier charges, so freight is expensed once, from the invoice. |
| Fixed assets | Companies Act straight-line over Schedule II useful lives, 5% residual, pro rata from put-to-use date, posted monthly by the sweeper. Disposal posts the gain or loss. Income-tax WDV blocks are for the CA. |
| Stock | Periodic. A stock line on a bill adds an IN movement and sets the item's cost; the books post Dr Purchases. Closing stock goes in at year end (Phase 5). |
| Module | New `purchases` module owns suppliers' bills, payments and the Shiprocket import. `accounts` owns posting rules (reading `purchases` tables by SQL, as Phase 2 does), TDS sections, challans, asset classes, the asset register and depreciation. |
| Input GST | One `INPUT_GST` account. The CGST/SGST/IGST split is stored on bill lines (and in Razorpay settlements as IGST) for the monthly report and GSTR-3B. |

Out of scope (Phase 5 or later): GSTR-2B matching, 26Q/24Q export, income-tax depreciation blocks, salary TDS,
PF/ESI, purchase returns and debit notes, supplier ageing buckets beyond "oldest unpaid due date".

## Data (migration V36)

All money columns `NUMERIC(12,2)` mapped to `Rupees`. The V32 audit trigger is attached to every new table.

### Accounts (seeded, with `system_key`)

- `5100 Purchases` `PURCHASES` (EXPENSE).
- `5200 Depreciation` `DEPRECIATION` (EXPENSE).
- `4900 Gain on asset disposal` `ASSET_GAIN` (INCOME); `5210 Loss on asset disposal` `ASSET_LOSS` (EXPENSE).
- Per seeded asset class, an asset account and an accumulated-depreciation account (ASSET, contra, credit balance):
  `1500 Plant & machinery` / `1501 Acc. depreciation – plant & machinery`, `1510 Furniture & fittings` / `1511`,
  `1520 Office equipment` / `1521`, `1530 Computers` / `1531`. `system_key` `ASSET_<CLASS>` and `ACCDEP_<CLASS>`.
- `journal_entry.source_type` CHECK gains `SUPPLIER_BILL`, `SUPPLIER_PAYMENT`, `TDS_CHALLAN`, `ASSET`;
  `source_step` CHECK gains `PAYMENT`, `DEPRECIATION`, `DISPOSAL` (`ISSUE` and `CANCEL` exist).
- `import_profile.kind` CHECK gains `SHIPROCKET_INVOICE`.

### Tables

- `tds_section`: `id`, `code` (unique, e.g. `194C`), `name` (≤80), `rate_percent`, `no_pan_rate_percent`,
  `year_threshold_rupees` (nullable), `single_threshold_rupees` (nullable), `active`. Seeded (placeholders for the
  CA): 194C contractor (individual) 1%, 194C contractor (company) 2%, 194I(b) rent of land/building 10%, 194I(a)
  rent of plant/machinery 2%, 194J professional fees 10%, 194H commission 2%; no-PAN rate 20%; thresholds as
  currently notified, editable.
- `party.tds_section_id` (nullable → `tds_section`): the supplier's default section.
- `supplier_bill`: `id`, `party_id` (→ party, must be a supplier), `bill_no` (≤40), `bill_date`, `due_date`
  (nullable), `supply_state_code` (2 chars, the supplier's state; IGST when it differs from the company's state),
  `taxable_rupees`, `tax_rupees`, `total_rupees`, `tds_section_id` (nullable), `tds_rupees` (≥ 0, 0 when no TDS),
  `payable_rupees` (= total − TDS), `source` (`MANUAL`, `SHIPROCKET`), `attachment_key` (nullable, R2),
  `notes` (≤300), `status` (`OPEN`, `CANCELLED`), `cancelled_on` (nullable), `cancel_reason` (nullable),
  `created_by`, `created_at`. Unique (`party_id`, lower(`bill_no`)).
- `supplier_bill_line`: `id`, `bill_id`, `position`, `kind` (`EXPENSE`, `STOCK`, `ASSET`), `description` (≤200),
  `account_id` (EXPENSE: an EXPENSE account; STOCK: `PURCHASES`; ASSET: the class's asset account),
  `stock_item_id` (STOCK only), `quantity` (STOCK only, > 0), `asset_class_id` (ASSET only), `hsn` (nullable),
  `taxable_rupees`, `gst_rate_percent`, `cgst_rupees`, `sgst_rupees`, `igst_rupees`, `total_rupees`.
- `supplier_payment`: `id`, `party_id`, `paid_on`, `mode` (counter modes: CASH, UPI, CARD, BANK), `amount_rupees`,
  `reference` (≤100, UTR/cheque), `notes`, `bank_line_id` (nullable, when created from a bank line),
  `created_by`, `created_at`.
- `supplier_payment_allocation`: `payment_id`, `bill_id`, `amount_rupees` (> 0); PK (`payment_id`, `bill_id`).
  Σ allocations ≤ payment amount; Σ allocations on a bill ≤ its `payable_rupees`. The unallocated part of a payment
  is an advance to the supplier.
- `tds_challan`: `id`, `tds_section_id`, `period_month` (first of month the TDS was deducted in), `amount_rupees`,
  `interest_rupees` (default 0), `challan_no` (≤20), `bsr_code` (≤7), `paid_on`, `mode`, `created_by`,
  `created_at`.
- `asset_class`: `id`, `name`, `life_years`, `residual_percent` (default 5), `asset_account_id`,
  `accdep_account_id`, `active`. Seeded: Plant & machinery 15, Furniture & fittings 10, Office equipment 5,
  Computers 3.
- `fixed_asset`: `id`, `name` (≤120), `asset_class_id`, `cost_rupees`, `put_to_use_on`, `bill_line_id` (nullable,
  unique), `opening_accdep_rupees` (default 0; for assets bought before the books start date), `disposed_on`
  (nullable), `disposal_proceeds_rupees` (nullable), `disposal_mode` (nullable), `created_by`, `created_at`.
- `asset_depreciation`: `asset_id`, `month` (first of month), `amount_rupees`, `journal_entry_id`; PK
  (`asset_id`, `month`). One row per posted month.
- Settings section `books` gains `shiprocketPartyId` (nullable).

### Guards (triggers, like `expense_protect` in V33)

- A bill whose ISSUE entry is posted cannot change amounts, lines, party or date; it can only be cancelled.
- A payment whose PAYMENT entry is posted cannot change; corrections are a reversal (owner) plus a new payment.
- A bill with allocations cannot be cancelled; remove the payment's allocations first (only while the payment is
  unposted) or record a refund payment from the supplier as a negative-side bank "Post as".

## Module `purchases` (new)

Flat package like `accounts`. Depends on `party` (supplier lookup/save), `inventory` (stock IN movements through
`InventoryService.move`), `settings` (company state, numbering not needed: bills keep the supplier's number),
`shared.storage` (attachments), and only the public `TabularFiles` facade of `accounts` (file parsing). It never
posts or reads journal entries through `accounts` code; `accounts` reads `purchases` tables by SQL only, so there is
no module cycle.

- `SupplierService`: list suppliers with balances (billed, paid, TDS, balance, oldest unpaid due date), get,
  create/update through `PartyService.save(..., Role.SUPPLIER)` plus `tds_section_id`. Balance = Σ open bills'
  `payable_rupees` − Σ payments (so an advance shows as negative = supplier owes us / wallet credit).
- `SupplierBillService`: create, update (only while not posted: checks `journal_entry` by SQL through a small
  `BooksPosted` query on the `journal_entry` table by source; no import of `accounts`), cancel, get, list (filter by
  supplier, status, month, source), attach file (R2 key `bills/<id>/<uuid>.<ext>`, ≤10 MB, image or PDF).
  Tax per line: `cgst = sgst = taxable × rate / 2` (rounded half-up to paise, sgst takes the remainder) when
  `supply_state_code` equals the company state code, else `igst = taxable × rate`. With GST off (no company GSTIN)
  tax is still recorded as billed by the supplier; it is input tax either way.
  TDS: default from the supplier's section; amount = Σ taxable × rate (the owner can override or clear). Response
  carries `tdsWarning` when the supplier's financial-year Σ taxable (non-cancelled bills, including this one)
  crosses the section's yearly threshold, or this bill crosses the single threshold, and no TDS is set.
  STOCK lines: on create, one IN movement per line (`quantity`, note "Bill <bill_no>"), and the item's cost is set
  to `taxable / quantity`. On cancel, one OUT movement reverses it. Update of an unposted bill replaces its movements
  (OUT the old, IN the new).
  ASSET lines: on create, a `fixed_asset` row per line (name = description, cost = taxable, put-to-use = bill date,
  editable later in the register). On cancel, the asset is removed if it has no posted depreciation, else the cancel
  is refused with "dispose the asset first".
  "Paid now": creates a `supplier_payment` (mode, reference) for `payable_rupees`, allocated to this bill.
- `SupplierPaymentService`: create (allocation default: oldest open bills first by due date then bill date;
  editable list in the request), list per supplier, delete (only while not posted). `fromBankLine(bankLineId,
  partyId, allocations)`: reads the bank line by SQL (`bank_line`, debit side only), creates the payment with
  `mode = BANK`, `paid_on = line_date`, `bank_line_id` set. The `accounts` side marks the bank line MATCHED to the
  payment's entry once posted (see rules).
- `ShiprocketInvoiceImport`: preview + import, reusing `accounts`' parsing through a public `TabularFiles` facade
  that `accounts` exposes (`preview(file)`, `rows(file, mapping)`; the existing `BankFileParser` and
  `ImportProfileService` behind it). Fields: invoice number, invoice date, taxable (freight), GST amount, total
  (optional; else taxable + GST), IGST/CGST/SGST columns optional (else split by the state rule using the
  Shiprocket party's state). Rows group by invoice number (sum). Each new invoice becomes a bill for the configured
  Shiprocket supplier: one EXPENSE line on `EXPENSE_COURIER`, `source = SHIPROCKET`, due date = invoice date.
  Invoice numbers already present for that supplier are skipped. No Shiprocket supplier set → 409 "Pick the
  Shiprocket supplier in Settings first".
- `PurchasesController`: base `/api/v1/admin/money/purchases` (MONEY area may read and write, like expenses).

## `accounts` additions

- Public facade `TabularFiles` (see above) so `purchases` reuses the CSV/XLSX parsing and column mapping.
- `TdsService`: sections CRUD (owner), challans create/list/reverse, summary per section × month: deducted (Σ bill
  TDS by bill month, non-cancelled), paid (Σ challans by period month), balance.
- `AssetService`: classes CRUD (owner), register list (cost, accumulated depreciation = opening + Σ posted months,
  book value, status), create an opening asset (bought before the books start; `opening_accdep_rupees` given),
  update name/class/put-to-use date (only while nothing is posted for it), dispose (date, proceeds, mode).
- `InputGstReport`: per month: Σ taxable, CGST, SGST, IGST from non-cancelled bills by bill date, plus Razorpay
  settlement `tax_rupees` as IGST by `settled_on`; total; CSV download.

### Posting rules (`accounts/rules`, run by the sweeper; party = the supplier)

| Rule | Date | Debit | Credit |
| --- | --- | --- | --- |
| `SUPPLIER_BILL / ISSUE` | bill date | Each line's account (taxable): Purchases, the expense account or the class asset account; Input GST (Σ tax) | Creditors (`payable_rupees`); TDS payable (`tds_rupees`, if > 0) |
| `SUPPLIER_BILL / CANCEL` (only when ISSUE is posted) | `cancelled_on` | Mirror of ISSUE | |
| `SUPPLIER_PAYMENT / PAYMENT` | `paid_on` | Creditors (amount) | Mode account |
| `TDS_CHALLAN / PAYMENT` | `paid_on` | TDS payable (amount); Interest on TDS → `EXPENSE_OTHER` (interest, if > 0) | Mode account |
| `ASSET / DEPRECIATION` | last day of the month | Depreciation | The class's accumulated-depreciation account |
| `ASSET / DISPOSAL` | `disposed_on` | Accumulated depreciation (total to date); mode account (proceeds, if > 0); Loss on disposal (if book value > proceeds) | Asset account (cost); Gain on disposal (if proceeds > book value) |
| `COD_REMITTANCE / SETTLE` (changed) | as Phase 3 | Bank (net); **Creditors, party = Shiprocket** (deduction) | COD clearing |

- Depreciation is keyed by (asset, month), so the rule's `pending` returns asset ids that have a due, unposted
  month; `build` posts the earliest one and writes the `asset_depreciation` row in the same transaction (the sweeper
  loops). Due months: from the later of `put_to_use_on` and the books start date, to the last fully ended month,
  stopping at the month of disposal (the disposal month gets depreciation up to `disposed_on`). Monthly amount =
  (cost − residual) / (life × 12), rounded to paise; the first and last months are pro rata by days; the final
  month is capped so accumulated depreciation never exceeds cost − residual. Opening assets count
  `opening_accdep_rupees` toward the cap. `ASSET` entries use `source_id` = asset id and `source_step`
  `DEPRECIATION`; the unique (`source_type`, `source_id`, `source_step`) index from V32 would block a second month,
  so V36 replaces it with a unique index that excludes `source_step = 'DEPRECIATION'`, and `asset_depreciation`'s PK
  is the idempotency guard for those.
- The cancel rule only fires when ISSUE is posted; a bill cancelled before posting never posts at all (ISSUE's
  `pending` skips cancelled bills).
- COD rule: `books.shiprocketPartyId` unset and deduction > 0 → posting problem "Pick the Shiprocket supplier in
  Settings". Nothing is posted yet anywhere (books start date unset), so no old entries need reversing.
- A supplier payment created from a bank line: after the PAYMENT entry posts, the rule links the bank line
  (`status = MATCHED`, `journal_entry_id` = the payment entry) in the same transaction.
- Phase 3's auto-match finds supplier payments and challans as candidates with no change (they are Bank ledger
  lines).
- Opening assets: their cost and opening accumulated depreciation are entered by the owner on the existing opening
  balances screen against the class accounts; the register does not post them.

## API

`purchases` (base `/api/v1/admin/money/purchases`, MONEY area):
- `GET /suppliers`, `GET /suppliers/{id}` (with statement: bills, payments, running balance), `POST /suppliers`,
  `PUT /suppliers/{id}`.
- `GET /bills`, `GET /bills/{id}`, `POST /bills`, `PUT /bills/{id}`, `POST /bills/{id}/cancel`,
  `POST /bills/{id}/attachment` (multipart), `GET /bills/{id}/attachment` (redirect to a signed URL),
  `POST /bills/tds-check` (party, date, taxable → suggested TDS and warning, for the form).
- `GET /payments`, `POST /payments`, `DELETE /payments/{id}`, `POST /payments/from-bank-line`.
- `POST /shiprocket-invoices/preview`, `POST /shiprocket-invoices` (multipart + mapping).

`accounts` (existing rule: GET needs MONEY, writes need OWNER):
- `GET/POST/PUT /api/v1/admin/accounts/tds/sections`, `GET /tds/summary?from=&to=`, `GET/POST /tds/challans`.
- `GET/POST/PUT /assets/classes`, `GET /assets`, `POST /assets` (opening asset), `PUT /assets/{id}`,
  `POST /assets/{id}/dispose`.
- `GET /input-gst?from=&to=`, `GET /input-gst.csv?from=&to=`.
- Books settings gain `shiprocketPartyId`.

API record names unique (springdoc collides on simple names: no `Row`, `Line`, `Item`). Regenerate the api-client
after the backend endpoints exist.

## Admin UI

Under `frontend/admin/src/app/money/purchases/` (Money nav gains "Purchases"):
- **Suppliers**: list (name, GSTIN, balance, oldest unpaid due date, overdue badge), add/edit sheet (name, phone,
  GSTIN, PAN, address, state, default TDS section). Supplier page: statement with bills, payments, running balance;
  "New bill", "Pay".
- **Bills**: list with filters; bill form: supplier, bill no, dates, state (defaults from supplier), lines (kind
  picker → account / stock item + qty / asset class; taxable, GST rate, tax computed and shown), TDS (pre-filled,
  editable, warning text), attachment, "Paid now" (mode, reference). Posted bills are read-only with Cancel.
- **Pay supplier** sheet: amount, mode, date, reference, allocation table pre-filled oldest first.
- **Shiprocket invoices**: the Phase 3 upload wizard component with the `SHIPROCKET_INVOICE` fields, result counts.

Under `frontend/admin/src/app/money/accounts/` (existing books area):
- **Fixed assets**: register table with totals, "Add opening asset", dispose sheet; asset classes editor (owner).
- **TDS**: summary per section × month (deducted, paid, balance), "Record challan" sheet; sections editor (owner).
- **Input GST**: month table with CSV download.
- **Bank statement page**: unmatched debit line gains a "Supplier payment" action (supplier + allocation).
- **Books settings**: Shiprocket supplier picker.

Honest empty states everywhere; no sample data.

## Errors

- Bill number already used for that supplier → 409 "This supplier's bill <no> is already recorded".
- Party not a supplier → 400. Line kind fields missing (stock line without item/qty, asset line without class) → 400.
- Edit/delete of a posted bill or payment → 409 with the existing books error shape.
- Allocation over the payment or over the bill's payable → 400.
- Cancel with allocations, or with an asset that has posted depreciation → 409 with the reason.
- Posting into a locked period or before books start → posting problem (sweeper), as in Phase 2.
- Shiprocket import: bad file → 400; unparseable rows skipped and listed in the result.

## Testing

DeepSeek writes the code and tests; Claude reviews. Per CLAUDE.md, tests are written but not run here.
- Unit: tax split (intra vs inter state, paise rounding), TDS suggestion and threshold warning, depreciation
  schedule (pro rata first month, cap at residual, disposal month, opening accumulated depreciation), allocation
  (oldest first, over-allocation rejected), Shiprocket invoice grouping.
- IT (Testcontainers): bill with stock + expense + asset lines → stock IN movement, asset row, ISSUE entry balances
  with TDS; cancel → CANCEL entry and stock OUT; payment allocation and PAYMENT entry; payment from a bank line marks
  it matched; challan clears TDS payable; three months of depreciation then disposal with a gain; COD remittance
  deduction lands on Creditors (Shiprocket); Shiprocket import skips a repeated invoice number.

## Gaps

Logged at the end of the phase in `docs/BOOKS_GAPS.md` under "Phase 4". Hand checks happen once all phases are done.
