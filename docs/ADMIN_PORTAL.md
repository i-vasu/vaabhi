# Admin portal

The admin dashboard (`frontend/admin`) is the shop's back office: orders and tailoring work, customers, staff,
money, stock and reports. It is built in phases, all in one pull request with one commit per phase; each phase
brings its backend module, Flyway migrations, API types and tests.

| Phase | Scope | Status |
|---|---|---|
| 0 Foundations | Staff sign-in (email + password, lockout, first owner from env), app frame (sidebar on desktop, bottom bar on phones, header with search and notifications), dark navy theme, shared components | done |
| 1 Settings | Shop profile (address, hours, links), billing & tax (GST, GSTIN, rates, prices incl. tax), order / invoice / item numbering, bank details, UPI QR, alteration wording, delivery charges, production stages, trial date, garment & measurement templates, holidays & weekly off, notification switches | done |
| 2 Customers & orders | Customers (list, add, detail, measurements, occasions), new order (items, cloth photo, advance, discount ₹/%, coupon, notes, live totals), orders list and stage progress, payments and refunds, stitching price list with piece rates, alterations | done |
| 3 Workshop | Work board by stage, assigning staff, who may work which stage, workshop overview (in production, due today, overdue, stage load) | done |
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

## Customers, orders and alterations

The `tailoring` backend module (`V7__tailoring_orders.sql`) owns these. Shop customers are `client` rows so they
never clash with online customer accounts (`customer`, feat/auth); the two can be linked by phone number later.
Money is in paise.

- **Customers** — `/api/v1/admin/clients` (search by name or any 3+ digits of the number; sort by recent, name,
  spend or what is owed). One customer per mobile number, stored as E.164 (`9876500001` → `+919876500001`).
  Measurements are kept per garment template; the newest set per garment is copied onto each new order item, so
  later edits never change a booked order.
- **Orders** — `/api/v1/admin/orders`. Totals are worked out by the API (items + delivery package − discount, with
  the discount base from Billing settings); the form shows the same sums live (`orderTotals` in
  `frontend/admin/src/lib/tailoring.ts`). Items start at the first production stage; the order's status follows
  its items: BOOKED while all are at the first stage, IN_PROGRESS once work starts, READY when none is in a work
  stage, DELIVERED when all are handed over. CANCELLED is set by hand and cannot follow DELIVERED.
- **Payments** — the advance at booking and later payments or refunds (`/orders/{id}/payments`). A cancelled order
  only takes refunds; a refund cannot exceed what was paid.
- **Price list** — `/api/v1/admin/service-products`: what the shop stitches per garment, with each work stage's
  piece rate so profit per piece is shown. Removing an item switches it off so old orders keep it.
- **Alterations** — `/api/v1/admin/alterations`, numbered from the ALTERATION sequence. They skip production stages
  and move BOOKED → IN_PROGRESS → READY → DELIVERED (or CANCELLED) by hand; money can be collected on any move. Free
  alterations (e.g. a fit fix on the shop's own order) ignore item charges. Booking needs the customer to accept the
  alteration wording from Settings → Documents.
- **Photos** (customer, cloth, alteration garment, price-list item) use the same flow as settings images:
  `POST /api/v1/admin/tailoring/images` gives a key and a presigned URL, the browser PUTs a 400 px WebP to it, and the
  form sends the key; the API rejects keys whose file never arrived.
- `OrderEvent` and `AlterationEvent` are published for notifications, invoices (phase 4) and the activity log
  (phase 6); nothing listens yet.

Admin pages: `/customers/`, `/orders/`, `/alterations/`, `/price-list/` (detail and edit pages take `?id=`; new
order and alteration take `?clientId=` and, for alterations, `?orderId=`).

## Workshop

`/api/v1/admin/workshop` (`WorkshopService` in the tailoring module, `V8__workshop.sql`):

- **Work board** — `GET /board` lists every work and Ready stage with the open items in it (orders not delivered
  or cancelled), most urgent first; `?staffId=` shows one person's work. A stage removed in Settings keeps showing
  while items still sit in it. `POST /items/move` moves items from any number of orders to a stage in one go;
  each order's status follows as usual.
- **Assigning** — `PUT /items/{itemId}/assignee` gives an item in a work stage to a staff member, or takes it back
  with `staffId: null`. Moving an item to another stage clears the assignee, since the next stage is usually
  someone else's job.
- **Who works which stage** — `PUT /staff/{staffId}/stages`. A person with no stages ticked may take any stage;
  otherwise only the ticked work stages. Staff come from `StaffDirectory` (the staff module's public read API);
  adding and managing staff is phase 5.
- **Stage log** — every time an item leaves a stage, `order_item_stage_log` records from and to stage, who was
  assigned (the person who did that stage) and who moved it. Phase 5's production ledger and piece-rate pay read
  it together with the price list's stage costs.
- **Overview** — `GET /overview`: items in production and not yet given out, items ready for pickup, open orders
  due today, overdue and with a trial today, items per stage (with how many are late), and each person's load.
  Orders can also be listed with `filter=DUE_TODAY`.

Admin pages: `/workshop/` (overview), `/workshop/board/` (`?staffId=`, `?unassigned=1`), `/workshop/staff/`. On
phones the bottom bar is Home, Orders, Work, Customers and More; Products moved into More (Catalogue).

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
