# Admin portal

The admin dashboard (`frontend/admin`) is the shop's back office: orders and tailoring work, customers, staff,
money, stock and reports. It is built in phases; each phase is one pull request with its backend module, Flyway
migrations, API types and tests.

| Phase | Scope | Status |
|---|---|---|
| 0 Foundations | Staff sign-in (email + password, lockout, first owner from env), app frame (sidebar on desktop, bottom bar on phones, header with search and notifications), dark navy theme, shared components | this PR |
| 1 Settings | Shop profile (address, hours, links), billing & tax (GST, GSTIN, rates, prices incl. tax), order / invoice / item numbering, bank details, UPI QR, alteration wording, delivery charges, production stages, trial date, garment & measurement templates, holidays & weekly off, notification switches | planned |
| 2 Customers & orders | Customers (list, add, detail, measurements, occasions), new order (items, cloth photo, advance, discount ₹/%, coupon, notes, live totals), orders list and stage progress, alterations | planned |
| 3 Workshop | Work board by stage, assigning staff, who may work which stage, workshop overview (in production, due today, overdue, stage load) | planned |
| 4 Money | Invoices (auto on delivery), payments (outstanding, collections), expenses, financial ledger | planned |
| 5 Staff | Staff, roles & permissions, attendance (day / month), requests (leave, permission, off day, comp-off, advance), payroll (late, overtime, advances, salary), piece rates, production & work ledgers | planned |
| 6 Stock & extras | Inventory (items, units, movements, stock ledger), design & customer galleries, rewards & coupons, Google review requests, activity log, notifications feed, help & support, print formats and form layouts, WhatsApp/SMS sending | planned |
| 7 Reports | Reports hub (KPIs, 6-month revenue, dues ageing), sales, GST register, sales orders, financial ledger, product profitability, orders, deliveries, customers, alterations, workshop, production, work, stock, rewards, AI usage; CSV export | planned |

## Sign-in

- `POST /api/v1/auth/staff/login` with `{email, password}` returns `{accessToken, expiresIn, staff}`. The token is an
  HS256 JWT (`sub: staff:<id>`, `roles: ["ADMIN"]`, `staffRole`, `name`) valid for `STAFF_TOKEN_TTL` (12 h).
- Five wrong passwords in a row lock the account for 15 minutes; unknown emails and wrong passwords get the same
  answer.
- `GET /api/v1/admin/me` returns the signed-in staff member; the dashboard calls it on load and signs out on 401/403.
- The first owner is created on startup from `STAFF_OWNER_EMAIL` / `STAFF_OWNER_PASSWORD` while no staff account
  exists. The `demo` profile creates `owner@vaabhi.local` / `vaabhi-demo-owner` for local development and e2e tests.
- The dashboard keeps the token in `sessionStorage` (per tab) and sends anyone without a valid one to `/login`.

## UI conventions

Theme tokens live in `frontend/admin/src/app/globals.css` (`bg-app`, `bg-panel`, `border-line`, `text-ink`,
`text-ink-soft`, `accent`, and `ok` / `warn` / `bad` for status). Shared pieces are in
`frontend/admin/src/components/ui.tsx`: `PageHeader`, `SectionLabel`, `StatTile`, `EmptyState`, `ChipGroup`,
`SegmentedControl`, `Toggle`, `ListCard` / `ListRow`, `TextField`, `Sheet`. Navigation is declared once in
`frontend/admin/src/lib/nav.ts`; each phase adds its pages there.

## Database migrations

`V5__staff_account.sql` skips V4, which the customer sign-in branch (`feat/auth`, PR #28) uses. If this merges and
deploys first, Flyway will refuse the late V4: renumber that migration (e.g. to V6) before merging #28. Later phases
continue after the highest version on `main`.
