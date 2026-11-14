# Books: known gaps

Gaps left open while building the books phases, to implement after all phases are done. The owner checks every
phase by hand at the end (see each phase spec's "Owner checks by hand").

## Phase 3: bank reconciliation

- COD remittance list shows the journal number as plain text: `CodRemittanceView` has no `journalEntryId`, so the
  admin can't link to the entry. Add the field (the Razorpay view already has it).
- Opening/closing balance inputs on the upload page use `parseRupees`, which rejects negatives, so an overdrawn
  balance can't be typed (blank still takes it from the file).
- Statements listed newest-first: opening/closing defaults are taken from the first/last line in file order, so they
  come out swapped. Detect descending dates and flip.
- COD import sums the `net` column per row. If a report repeats the remittance total on every row, the deduction is
  wrong. Consider a "net is per remittance" option.
- Auto-match only runs on bank statement import. Settlements and remittances posted later by the sweeper don't
  re-trigger it; those lines must be matched by hand (candidates list finds them). Re-run auto-match after a sweep.
- `unknownAwbs` in the COD import summary is not de-duplicated.
- Razorpay settlement fee is `fees − tax`; a settlement with tax > fees would give a negative fee (journal rejects).
- Phase 3 owner checks by hand: spec `docs/superpowers/specs/2026-11-12-phase3-bank-reconciliation-design.md`.

## Phase 4: purchases, payables, TDS, assets

- Bill attachments go to the public media bucket under an unguessable key (`bills/<id>/<uuid>`). Move them to a
  private bucket with signed GETs.
- Reversing a supplier payment's journal entry does not unmatch the bank line it was created from; the line keeps
  pointing at the reversed entry.
- A supplier payment created from a bank line stays `UNMATCHED` until the next sweep posts it (up to 2 minutes, or
  "Post now").
- No purchase returns or debit notes; a wrong posted bill is cancelled and entered again.
- The TDS threshold warning ignores lower-deduction certificates and Form 15G/15H; the owner overrides the amount.
- Shiprocket wallet recharges are entered by hand as supplier payments (or from the bank line); there is no wallet
  import.
- Updating an unposted bill whose stock was already used fails with "Only X in stock" (the old quantity cannot go
  OUT); cancel and re-enter after adjusting stock.
- Renaming an asset class does not rename its two chart accounts.
- The bill form's IGST preview is a manual toggle; the backend decides the stored split from the supplier's state.
- Phase 4 owner checks by hand: spec `docs/superpowers/specs/2026-11-13-phase4-purchases-payables-design.md`.

## Phase 5: statements, returns, year end

- Overdrawn bank and debit-balance creditors are not reclassified across the balance sheet.
- GSTR-3B 3.1(c) nil/exempt and 3.1(b) zero-rated depend on what GSTR-1 rows expose; 4(B) reversals and 4(D)
  ineligible ITC are always zero. Razorpay fee GST is treated as IGST input credit.
- 26Q export is a CSV for the CA, not an FVU file; no Form 16A.
- Finished-goods closing stock is one typed figure.
- Reopen unlocks only March; the other eleven months stay locked.
- The close checklist's "every document posted" item counts all posting problems, not only the year's (problems
  carry no date). Close runs "Post now" first.
- The auditor pack's bank reconciliation sheet lists one report per bank statement whose period ends in the year,
  not one "as at 31 March" view.
- The ledgers sheet runs one query per account; fine for a year's export, slow if the chart grows very large.
- Year-end ITs share the database with other ITs, so they use far-future years and assert changes, not absolute
  balances; close can still fail there if another test leaves posting problems behind.
- Phase 5 owner checks by hand: spec `docs/superpowers/specs/2026-11-14-phase5-statements-returns-year-end-design.md`.

## Phase 6: daily backups in India

- Provider is AceCloud object storage (owner's choice for flat rupee billing). Not yet confirmed with AceCloud in
  writing: bucket stored only in India (their page says "triple replication across India and US regions"), S3
  Object Lock, lifecycle expiry, keys without delete. Until then backups run with `BACKUP_LOCK_MODE=NONE`: anyone
  with the backup key can delete them.
- The live database stays on Neon in Singapore; only the daily backups are in India. CA to confirm this is enough.
- Without lifecycle rules, daily backups accumulate (~18 GB a year at today's size).
- Single PUT per file (5 GB limit); no multipart upload.
- The WhatsApp failure alert needs an approved MSG91 template; until then a failed backup shows only in the admin
  and the logs.
- Attachment copy reads `supplier_bill.attachment_key` with SQL from the `backup` module (cross-module read).
- The monthly check proves the newest dump is complete and readable (`pg_restore --list`), not that every row
  restores; the full drill is manual (`docs/RESTORE.md`).
- REST lives under `/api/v1/admin/accounts/backups` to reuse the books' access rules; `pg_dump` sits behind
  `DatabaseDumper` so ITs use a stub.
- `infra/.env.example` lacks the `BACKUP_*` block (edits to `.env*` files are blocked for the agent); the keys are
  listed in `infra/vps-setup.md`.
- Owner hand checks: AceCloud bucket + keys in `/opt/vaabhi/.env`; first "Back up now" and first nightly run show OK;
  restore drill per `docs/RESTORE.md`, then "Mark restore drill done".

## Found in the end-to-end run (2026-11-14)

- With the company card's state empty, every supplier bill is treated as intra-state (CGST + SGST), even from
  another state. Set the state on the company card; the bill form should warn when it is missing.
- Playwright: `admin-products` assumes R2 is not configured (dev has keys, so the upload runs for real), and two
  storefront tests expect demo-catalogue counts and prices; they pass only on a fresh demo database.
