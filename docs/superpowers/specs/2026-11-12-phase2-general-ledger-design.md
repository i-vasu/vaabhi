# Phase 2: general ledger

Date: 2026-11-12. Plan: "Vaabhi books without accounting software", Phase 2. Builds on Phases 0–1 (PR #48, same
branch). Done when: the trial balance balances every day, and every journal line links back to its source document.

## Decisions

| Question | Decision |
| --- | --- |
| When the books start | A "books start on" date set by the owner (the OPC incorporation date). Records dated from it are posted, past ones included; earlier records stay out. Opening balances are entered as of that date. |
| Money received before an invoice | Credited to Customer advances (liability); moved to Debtors when the order's invoice is issued. |
| Who changes the books | Owner only: start date, manual journals, reversals, opening balances, period locks, chart edits, payment-account map. Staff with the MONEY area can view. |
| Payment mode → account | Default map, editable: Cash → Cash in hand; UPI, Card, Bank → Bank; RAZORPAY → Razorpay clearing; COD → Shiprocket COD clearing. |
| How entries are made | A sweeper in the new `accounts` module posts from the source records (approach A). Idempotent; a posting bug never blocks a sale. |

Out of scope: supplier bills, salary payable, TDS, PF/ESI, assets and depreciation (Phase 4); bank and gateway
settlement, fees (Phase 3); P&L, balance sheet, returns, auditor pack (Phase 5).

## Data (migration V32)

- `account`: `id`, `code` (unique, 4 digits), `name` (≤80), `type` (`ASSET`, `LIABILITY`, `EQUITY`, `INCOME`,
  `EXPENSE`), `parent_id` (nullable), `system_key` (unique, nullable), `active`, `created_at`. Accounts with a
  `system_key` cannot be deleted or deactivated.
- Seeded chart (code, name, type, system_key):
  - Assets: 1000 Cash in hand `CASH`; 1010 Bank `BANK`; 1020 Razorpay clearing `RAZORPAY_CLEARING`; 1030 Shiprocket
    COD clearing `COD_CLEARING`; 1100 Debtors `DEBTORS`; 1200 Input GST `INPUT_GST`; 1300 Stock `STOCK`.
  - Liabilities: 2000 Creditors `CREDITORS`; 2100 Customer advances `CUSTOMER_ADVANCES`; 2200 Output CGST
    `OUTPUT_CGST`; 2210 Output SGST `OUTPUT_SGST`; 2220 Output IGST `OUTPUT_IGST`; 2300 Salary payable
    `SALARY_PAYABLE`; 2400 TDS payable `TDS_PAYABLE`; 2500 Owner loan `OWNER_LOAN`.
  - Equity: 3000 Share capital `SHARE_CAPITAL`; 3100 Opening balance equity `OPENING_BALANCE`; 3200 Retained
    earnings `RETAINED_EARNINGS`.
  - Income: 4000 Sales `SALES`; 4010 Sales returns `SALES_RETURNS` (contra income, debit balance); 4100 Alteration
    income `ALTERATION_INCOME`.
  - Expenses: one per expense category, `system_key` = `EXPENSE_<CATEGORY>` (5000 Rent, 5010 Salaries
    `EXPENSE_SALARY`, 5020 Materials, 5030 Utilities, 5040 Transport, 5050 Maintenance, 5060 Marketing, 5090 Other —
    read the expense category CHECK in the latest migration and seed one per value).
- `journal_entry`: `id`, `number` (unique, from a new `JOURNAL` number sequence `JV/{FY}/`, pad 5, FY reset),
  `entry_date`, `source_type` (`INVOICE`, `CREDIT_NOTE`, `ORDER_PAYMENT`, `ALTERATION_PAYMENT`, `WEB_ORDER`,
  `WEB_RETURN`, `EXPENSE`, `MANUAL`, `OPENING`), `source_id` (nullable for MANUAL), `source_step` (`ISSUE`,
  `APPLY`, `CANCEL`, `RECEIPT`, `REFUND`, `EXPENSE`, `MANUAL`, `OPENING`, `REVERSAL`), `narration` (≤200),
  `reverses_id` (nullable, unique), `created_by`, `created_at`. Unique (`source_type`, `source_id`,
  `source_step`) where `source_id` is not null.
- `journal_line`: `id`, `entry_id`, `position`, `account_id`, `party_id` (nullable), `debit_rupees`,
  `credit_rupees` (NUMERIC(12,2), ≥ 0, exactly one > 0), `memo` (≤120).
- Triggers:
  - Deferred constraint trigger: at commit each entry's debits equal its credits and it has ≥ 2 lines.
  - `journal_entry` and `journal_line` reject UPDATE and DELETE.
  - Insert into `journal_entry` is rejected when its month is locked.
- `period_lock`: `month` (date, first of month, PK), `locked_by`, `locked_at`.
- `posting_problem`: `source_type`, `source_id`, `source_step` (PK), `message` (≤500), `first_seen`, `last_seen`.
- `audit_log`: `id`, `table_name`, `row_id`, `action` (`INSERT`, `UPDATE`, `DELETE`), `old_values` JSONB,
  `new_values` JSONB, `actor` (from `current_setting('vaabhi.actor', true)`), `at`. Row triggers on `invoice`,
  `invoice_line`, `credit_note`, `credit_note_line`, `order_payment`, `alteration_payment`, `expense`, `web_order`
  (UPDATE only, when money columns change), `web_order_return`, `account`, `journal_entry`, `journal_line`,
  `period_lock`, `tax_class`. The app sets `vaabhi.actor` with `SET LOCAL` per transaction when a staff user is
  known.
- Settings section `books`: `startDate` (nullable), `modeAccounts` (map payment mode → account id; defaults as
  above).

## Code

### accounts module (new; may read other modules' tables by SQL, must not import their services)

- `ChartService`: list (with balances), create, update, deactivate.
- `JournalService`: `post(NewEntry)` (validates balance, ≥ 2 lines, active accounts, open period, takes a JV
  number; runs in the caller's transaction), `reverse(id, actor)` (owner, MANUAL/OPENING only, posts a REVERSAL
  entry dated today), list/search, get with source link, account ledger with running balance.
- `PostingRule` interface: `sourceType()`, `step()`, `pending(LocalDate start, int limit)` → source ids without an
  entry for this step, `build(long sourceId)` → `NewEntry`. One class per row in the table below.
- `JournalSweeper`: `@Scheduled` every 2 minutes and on demand; does nothing while `startDate` is null; for each rule
  posts pending sources one per transaction; records failures in `posting_problem` and clears them on success;
  logs counts. A daily check (03:30 IST) records a problem when the trial balance does not balance or an invoice's
  Debtors balance differs from its balance due.
- Rules (party = the order's client/customer party where known; entry date = the source's IST date):

| Rule | Debit | Credit |
| --- | --- | --- |
| Order payment (shop) or web payment, order has no live invoice at payment time → RECEIPT | Mode account | Customer advances |
| Same, invoice already issued → RECEIPT | Mode account | Debtors |
| Invoice → ISSUE | Debtors (total) | Sales (taxable), Output CGST, Output SGST, Output IGST (non-zero lines only) |
| Invoice whose order has advances → APPLY | Customer advances (min of advances on that order and invoice total) | Debtors |
| Invoice cancelled → CANCEL | Reverse of ISSUE (and of APPLY if posted) | |
| Credit note → ISSUE | Sales returns (taxable), Output GST parts | Debtors |
| Refund (negative order payment; web order refund processed; web return refund processed) → REFUND | Debtors if the order has a live invoice, else Customer advances | Mode account |
| Alteration payment → RECEIPT (negative amount → REFUND, reversed sides) | Mode account | Alteration income |
| Expense → EXPENSE | `EXPENSE_<category>` account | Mode account |

- Advances on an order = sum of that order's RECEIPT lines on Customer advances minus REFUND lines on it.
- Opening balances: `OpeningService` get/put; put reverses the existing OPENING entry and posts a new one dated
  `startDate`; the balancing amount goes to Opening balance equity.
- `PeriodService`: list months from start date to now with status; lock/unlock (owner).
- `AccountsStatus`: start date, last sweep, problem count, trial balance difference.

### settings / shared

- `NumberSequence.Kind.JOURNAL` and its seed row.
- `BooksSettings(startDate, modeAccounts)` stored as section `books`; setting the start date the first time
  triggers a sweep.
- A small filter or aspect sets `SET LOCAL vaabhi.actor = <staff name>` at the start of admin write transactions.

### admin

- Money → Accounts: home, chart (+ account ledger), journal (+ entry view), trial balance, manual journal, opening
  balances, periods, posting problems — as in the design (section 3 of the brainstorm). Payment accounts card on
  Settings → Billing.
- Owner-only controls hidden for other staff; the API enforces it.

### API (`/api/v1/admin/accounts`)

`accounts` (GET, POST), `accounts/{id}` (PUT), `accounts/{id}/ledger?from&to`, `journal?q&from&to&page&size`,
`journal/{id}`, `journal` (POST manual), `journal/{id}/reverse` (POST), `trial-balance?asOf`,
`opening-balances` (GET, PUT), `periods` (GET), `periods/{yyyy-MM}/lock` (PUT, DELETE), `status`, `sweep` (POST),
`problems`, `settings` (GET, PUT). GET needs the MONEY area; writes need OWNER.

## Errors

- Unbalanced or one-line manual entry → 400 "Debits and credits must be equal" / "Add at least two lines".
- Entry dated in a locked month → 409 "<Month YYYY> is locked"; for sweeps, a posting problem.
- Posting failure never fails the source action; it shows as a posting problem until fixed.
- Reversing a non-manual entry → 409 "Only manual and opening entries can be reversed".

## Testing

Written, not run: one unit test per posting rule (lines and amounts); sweeper posts once across two sweeps; DB
rejects an unbalanced entry and any update/delete of a posted line; locked month refuses an entry; advance then
invoice leaves Debtors equal to the balance due; credit note and refund entries; opening-balance replacement;
audit log captures an invoice update with old and new values.

Owner checks by hand:
1. Accounts home says the books haven't started; set the start date to the first day of this month.
2. After a sweep, the journal lists entries for this month's payments, invoices and expenses; trial balance shows
   Balanced.
3. Take an advance on a shop order → Customer advances goes up; deliver it (invoice) → advance moves to Debtors and
   the order's Debtors balance equals what is still due.
4. Ship and deliver a COD web order → COD clearing debit, Debtors cleared.
5. Record an expense → its expense account and Cash/Bank move.
6. Enter opening balances (bank, cash, share capital) → trial balance still balances; the remainder shows on
   Opening balance equity.
7. Post a manual journal for share capital; reverse it; both appear in the journal.
8. Lock last month; try a manual entry dated in it → refused.
9. Click any journal line → its source document opens.
