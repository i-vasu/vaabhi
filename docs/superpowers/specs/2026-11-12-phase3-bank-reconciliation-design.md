# Books Phase 3: bank, Razorpay and COD reconciliation

Date: 2026-11-12. Branch `feat/id-engine-parties` (PR #48, all books phases in one PR).

## Goal

At month end the ledger's `1010 Bank` balance equals the real bank statement, `1020 Razorpay clearing` and
`1030 Shiprocket COD clearing` hold only money still in transit, and gateway fees, courier/COD charges and bank
charges are booked as expenses. One reconciliation report per statement is ready for the CA.

## Decisions

| Topic | Decision |
|---|---|
| Hub | The bank statement. Every bank line ends up matched to a ledger Bank movement or posted as a new entry. |
| Bank input | CSV or XLSX upload from any bank. Column mapping chosen once, saved as a profile. |
| Razorpay | Pulled from the Razorpay API (daily job + "Sync now"). One journal entry per settlement. |
| Shiprocket COD | Owner uploads Shiprocket's COD remittance report (CSV/XLSX, column mapping like the bank). One entry per remittance. No manual amount entry. |
| Shop card/UPI | Settles into the same bank account, net of fees. Handled by "match with difference" on a bank line. |
| Undo | Every entry this phase creates can be undone by a Phase 2 reversal; the line/settlement/remittance goes back to unmatched. Period locks apply. |

Out of scope: purchases, payables, TDS, assets (Phase 4); statements and returns (Phase 5); multiple bank accounts
(one `BANK` account only; the schema keeps an `account_id` so a second bank can be added later).

## Data (migration V35)

- Accounts (seeded, with `system_key`): `5070 Payment gateway fees` `EXPENSE_GATEWAY_FEES`, `5080 Courier & COD
  charges` `EXPENSE_COURIER`, `5095 Bank charges` `EXPENSE_BANK_CHARGES`.
- `journal_entry.source_type` CHECK gains `RAZORPAY_SETTLEMENT`, `COD_REMITTANCE`, `BANK_LINE`;
  `source_step` CHECK gains `SETTLE`, `MATCH`, `POST`.
- `import_profile`: `id`, `kind` (`BANK`, `COD_REMITTANCE`), `name`, `mapping` (JSONB: header row index, column
  index per field, date format, amount style `DEBIT_CREDIT` or `SIGNED`), `created_at`. One active profile per kind
  is enough; the import picks the latest of its kind and lets the owner change the mapping.
- `bank_statement`: `id`, `account_id` (→ `account`, the BANK account), `file_name`, `period_from`, `period_to`,
  `opening_rupees`, `closing_rupees`, `line_count`, `uploaded_by`, `uploaded_at`.
- `bank_line`: `id`, `statement_id`, `account_id`, `line_date`, `description` (≤300), `reference` (≤100, UTR/cheque
  no., nullable), `debit_rupees`, `credit_rupees` (exactly one > 0), `balance_rupees` (nullable),
  `dedupe_key` (unique per account: sha256 of date|debit|credit|reference|balance|description),
  `status` (`UNMATCHED`, `MATCHED`, `POSTED`, `IGNORED`), `journal_entry_id` (nullable, the matched or created
  entry), `difference_entry_id` (nullable, the fee entry from "match with difference"), `matched_by`, `matched_at`.
- `razorpay_settlement`: `id` (Razorpay `setl_…`, PK), `utr`, `settled_on`, `gross_rupees`, `fee_rupees`,
  `tax_rupees`, `net_rupees`, `journal_entry_id`, `synced_at`.
- `cod_remittance`: `id`, `remittance_ref` (Shiprocket CRF/remittance id, unique), `utr` (nullable),
  `remitted_on`, `cod_rupees` (sum of COD collected for the AWBs), `deduction_rupees` (freight/COD charges
  deducted, may be 0), `net_rupees`, `file_name`, `journal_entry_id`, `uploaded_by`, `uploaded_at`.
- `cod_remittance_line`: `remittance_id`, `awb`, `order_number` (nullable), `cod_rupees`, `web_order_id` (nullable,
  set when the AWB matches a web order).
- All money columns `NUMERIC(12,2)`, mapped to the `Rupees` type. Audit trigger from V32 on the new tables.

## Module

All new code lives in the `accounts` module (flat package, like Phase 2). File parsing uses two new pom
dependencies: `org.apache.commons:commons-csv` and `org.dhatim:fastexcel-reader` (small; no Apache POI). It reads
checkout data only through checkout's public API:
- `checkout` exposes `RazorpaySettlements` (public interface): `List<SettlementSummary> since(LocalDate from)`,
  implemented in `RazorpayClient` with `GET /v1/settlements?from=&to=&count=100&skip=` (paged) and, for fee and tax,
  the settlement's `fees` and `tax` fields. If the API returns no fee split, `fee = gross - net`, `tax = 0`, and the
  problem list shows a warning.
- `checkout` exposes `WebOrderLookup.byAwb(String awb)` → web order id + number, for remittance lines.

## Flows

### 1. Bank statement import
1. Admin uploads a CSV or XLSX (≤5 MB). The backend parses the first 20 rows and returns them with the saved BANK
   profile (if any).
2. Admin confirms or changes the mapping: header row, date, description, reference, debit, credit (or one signed
   amount), balance, date format. Saved as the BANK profile.
3. Backend parses all rows, skips blank/total rows (no valid date), computes `dedupe_key`, drops lines already
   present, saves the statement and new lines as `UNMATCHED`. Opening = first balance − first credit + first debit;
   closing = last balance (both nullable when the file has no balance column; the admin can type them).
4. Auto-match runs (flow 4). Response: counts of new, duplicate, auto-matched lines.

### 2. Razorpay settlement sync
- `RazorpaySettlementSync` runs daily at 06:00 IST and on "Sync now". It asks checkout for settlements since the
  latest `settled_on` (or the books start date) and stores new ones.
- Each new settlement posts `RAZORPAY_SETTLEMENT / SETTLE` dated `settled_on`: Dr Bank `net`, Dr Gateway fees
  `fee`, Dr Input GST `tax`, Cr Razorpay clearing `gross`. Zero-amount lines are left out.
- If `settled_on` falls in a locked period or before the books start date, the settlement is stored, not posted,
  and shows in posting problems.
- Razorpay keys missing (dev): the sync logs and does nothing; the button shows "Razorpay not configured".

### 3. COD remittance import
1. Admin uploads Shiprocket's remittance report. Same preview + mapping step with a COD_REMITTANCE profile:
   remittance ref, remittance date, UTR (optional), AWB, order number (optional), COD amount, deduction (optional,
   per row or per remittance), net remitted (optional).
2. Rows group by remittance ref. Per group: `cod = Σ COD amount`, `deduction = Σ deduction` (or `cod − net` when
   only net is given), `net = cod − deduction`.
3. Remittance refs already imported are skipped. Each AWB is looked up in checkout; unknown AWBs are kept with
   `web_order_id = null` and listed as warnings (they still post, since the money is real).
4. Each new remittance posts `COD_REMITTANCE / SETTLE` dated `remitted_on`: Dr Bank `net`, Dr Courier & COD
   charges `deduction`, Cr COD clearing `cod`. Same locked-period rule as Razorpay.
5. Auto-match runs.

### 4. Auto-match
For each `UNMATCHED` bank line, candidates are ledger lines on the BANK account, same side (bank credit ↔ ledger
debit), not already linked to a bank line, dated within ±3 days:
1. If the bank line's `reference` equals a Razorpay settlement or COD remittance UTR → that entry.
2. Else exact amount match. Exactly one candidate → match. Zero or several → leave `UNMATCHED`; the UI shows the
   candidates.
Matching only links (`status = MATCHED`, `journal_entry_id`); it posts nothing.

### 5. Manual actions on a bank line
- **Match**: pick a candidate entry (any BANK ledger line, any date). If the amounts differ, the admin picks a
  difference account (default Bank charges for debits, Payment gateway fees for credits) and a
  `BANK_LINE / MATCH` entry posts the difference so Bank equals the statement. Example: card sale ₹1,000 in the
  ledger, ₹980 credited → Dr Payment gateway fees 20, Cr Bank 20.
- **Post as**: pick an account and a narration (default = description). Posts `BANK_LINE / POST` dated
  `line_date`: bank credit → Dr Bank, Cr chosen account; bank debit → Dr chosen account, Cr Bank. `status = POSTED`.
- **Ignore**: for lines that are not money movements (e.g. a reversed-then-reversed pair the bank prints). Needs a
  reason; listed on the report.
- **Undo**: MATCHED → unlink and reverse any difference entry; POSTED → reverse the entry; IGNORED → back to
  UNMATCHED. Reversal goes through `JournalService.reverse`, so locked periods block it.

### 6. Reconciliation report (per statement)
- Statement closing balance vs ledger Bank balance at `period_to`, and the difference.
- Explained by: unmatched bank lines (in the bank, not the ledger) and BANK ledger lines in the period not linked to
  any bank line (in the ledger, not the bank). If both lists are empty and the difference is non-zero, show
  "opening balance mismatch".
- Clearing balances at `period_to`: Razorpay clearing and COD clearing, with the oldest unsettled web payment date.
- CSV download.

## API (admin, `BankController` in `accounts`)

Base path `/api/v1/admin/accounts/bank`, so the existing `/api/v1/admin/accounts/**` OWNER rule in
`SecurityConfig` applies with no security change.

- `POST /statements/preview` (multipart) → first rows + saved mapping.
- `POST /statements` (multipart + mapping JSON) → import result.
- `GET /statements`, `GET /statements/{id}` (lines page, filter by status), `GET /statements/{id}/report`,
  `GET /statements/{id}/report.csv`.
- `GET /lines/{id}/candidates`, `POST /lines/{id}/match`, `/lines/{id}/post`, `/lines/{id}/ignore`,
  `/lines/{id}/undo`.
- `POST /razorpay/sync`, `GET /razorpay/settlements`.
- `POST /cod-remittances/preview`, `POST /cod-remittances`, `GET /cod-remittances`.
- API record names are unique (springdoc collides on simple names: no `Row`, `Line`, `Item`).

## Admin UI (`frontend/admin/src/app/accounts/bank/`)

- **Bank** page: statements list (period, lines, unmatched count, difference) + "Upload statement", Razorpay
  settlements tab with "Sync now", COD remittances tab with "Upload remittance report".
- **Upload wizard** (shared for bank and remittance): pick file → preview table with a column picker per field →
  import → result counts.
- **Statement** page: lines table with status filter; each unmatched line opens a sheet with candidates, Match,
  Post as, Ignore; matched/posted lines show their entry number with Undo.
- **Report** page: the reconciliation report with CSV download.
- Honest empty states everywhere; no sample data.

## Errors

- Bad file / no mappable rows → 400 with a message the wizard shows.
- Mapping row fails (bad date, both debit and credit) → the row is skipped and listed in the import result.
- Posting into a locked period or before books start → 409 with the existing books error shape.
- Razorpay API failure → sync returns the error; nothing partial is posted (each settlement posts in its own
  transaction).

## Testing

DeepSeek writes the code and tests; Claude reviews. Tests run through IntelliJ MCP (owner allowed this for Phase 3).
- Unit: bank file parser (CSV + XLSX, debit/credit and signed styles, blank/total rows, dedupe key), remittance
  grouping, auto-match selection.
- IT (Testcontainers): import → auto-match against a posted shop receipt; Razorpay settlement posting with a stubbed
  `RazorpaySettlements`; COD remittance posting; match with difference; post-as; undo reverses; locked period → 409;
  report difference = 0 after everything is matched.

## Owner checks by hand

1. Set books start date, record a shop UPI payment, upload a bank CSV containing it → line auto-matches.
2. Sync Razorpay (test keys) → settlement entry posts; Razorpay clearing drops by gross; fee shows in 5070.
3. Upload a Shiprocket remittance report → entry posts; COD clearing drops; deductions in 5080.
4. Post a bank-charges line with "Post as" → 5095 moves; report difference becomes 0.
5. Undo one match → line returns to unmatched, report shows it.
6. Trial balance still balances.
