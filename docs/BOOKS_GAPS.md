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
- Spotless was not run on the Phase 3 code; `verify` may need `spotless:apply` first.
- Phase 3 owner checks by hand: spec `docs/superpowers/specs/2026-11-12-phase3-bank-reconciliation-design.md`.
