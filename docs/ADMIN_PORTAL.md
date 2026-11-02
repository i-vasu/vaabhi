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
| 4 Money | Invoices (auto on delivery), payments (outstanding, collections), expenses, financial ledger | done |
| 5 Staff | Staff, roles & permissions, attendance (day / month), requests (leave, permission, off day, comp-off, advance), payroll (late, overtime, advances, salary), piece rates, production & work ledgers | done |
| 6 Stock & extras | Inventory (items, units, movements, stock ledger), design & customer galleries, rewards & coupons, Google review requests, activity log, notifications feed, help & support, print formats and form layouts, WhatsApp/SMS sending | done (paid sending needs a provider; see below) |
| 7 Reports | Reports hub (KPIs, 6-month revenue, dues ageing), sales, GST register, sales orders, financial ledger, product profitability, orders, deliveries, customers, alterations, workshop, production, work, stock, rewards, AI usage; CSV export | planned |

## Sign-in

- `POST /api/v1/auth/staff/login` with `{email, password}` returns `{accessToken, expiresIn, staff}`. The token is an
  HS256 JWT (`sub: staff:<id>`, `roles: ["ADMIN"]` plus `"OWNER"` for owners, `permissions`, `staffRole`, `name`)
  valid for `STAFF_TOKEN_TTL` (12 h). Staff without a password (most tailors) cannot sign in and get the same
  answer as a wrong password.
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

## Money

The `money` module (`/api/v1/admin/money`, `V9__money.sql`) reads the tailoring module's orders and payments and
depends on it; nothing depends on money.

- **Invoices** — with auto-invoice on (Settings → Billing), `InvoiceService` listens for `OrderEvent.DELIVERED` and
  issues the invoice in the same transaction, so a delivered order always has one. Staff can also issue one from
  the order (`POST /orders/{orderId}/invoice`; 409 if it already has one). Lines are the order items (with colour)
  and the delivery charge; the discount is the lines minus the order total. Cancelling keeps the number in the
  register and allows a new invoice; cancelling an order cancels its live invoice.
- **GST** — applies only when Billing has GST on and a GSTIN. The default rate is used (tailoring services have no
  per-category rate yet). With prices including tax, the invoice total is the order total and the GST is taken out
  of it; otherwise GST is added on top and the customer owes the invoice total. The tax is split evenly into CGST
  and SGST (SGST takes an odd paisa), rounding half up. HSN/SAC codes are not printed yet.
- **Dues** — `GET /outstanding`: orders and charged alterations, not cancelled, that still owe money, oldest first.
  An invoiced order owes its invoice total. Dues on work already handed over are totalled separately.
- **Expenses** — categories rent, salary, material, utilities, transport, maintenance, marketing, other; listed by
  month with totals per category.
- **Ledger** — `GET /ledger?from&to&kind`: order payments, refunds, alteration payments (now recorded per payment
  with its mode, in `alteration_payment`) and expenses, oldest first, with the opening balance before the range, a
  running balance, totals in and out, and per payment mode. At most 400 days per request. Expenses count from the
  start of their day.

Admin pages under More → Money: `/money/` (this month in and out, what is owed), `/money/dues/`, `/money/invoices/`
(and `/money/invoices/view/?id=`, printable), `/money/expenses/`, `/money/ledger/`. The order page links to its
invoice or offers to issue one; alteration hand-over asks how the customer paid.

## Staff, permissions and payroll

`V10__staff_payroll.sql`. The `staff` module owns people, roles, attendance and requests; the `payroll` module
(payslips, production ledger, statements) reads staff, tailoring and money and nothing depends on it.

- **Staff** — `/api/v1/admin/staff`: name, mobile, designation, role, pay (MONTHLY salary, DAILY wage or PIECE),
  overtime rate per hour (also what an hour late costs), shift, grace minutes and weekly off. Email and password
  are optional: only people who use the dashboard need them. Managers manage staff only; only the owner manages
  owners and managers. Nobody can switch themselves off, and the last owner cannot be switched off or demoted.
  Switching someone off keeps their history.
- **Permissions** — areas ORDERS (orders, customers, alterations, price list, an order's invoice), WORKSHOP, MONEY,
  STAFF, PAYROLL, CATALOGUE, SETTINGS (changing settings; everyone may read them). The owner has all of them; what
  managers and staff get is set by the owner at `PUT /staff/roles/{role}` (defaults: managers everything but payroll
  and settings, staff orders and workshop). The permissions go into the token at sign-in and `SecurityConfig`
  checks them per URL area, so **a change reaches a person at their next sign-in** (at most 12 h). `/me` returns
  the current permissions and the dashboard hides what the person cannot use. Everyone may raise their own
  requests at `/me/requests`.
- **Attendance** — `/staff/attendance?day=` lists everyone active with their mark, or a suggestion (HOLIDAY when the
  shop is closed per Settings → Holidays, OFF on their weekly off). Marking PRESENT or HALF_DAY with times works out
  late minutes (all of them, once past the grace period) and overtime (after shift end). The month view gives a
  status per day and totals.
- **Requests** — leave, permission (hours), off day, comp-off (up to 31 days) and salary advance. Someone other than
  the requester approves or rejects. Approving leave, an off day or a comp-off marks those days (not days already
  marked worked).
- **Payroll** — `GET /payroll/months/{yyyy-mm}` is a preview until `POST …/finalize` freezes the payslips. Monthly
  salary is reduced only by ABSENT and LEAVE days and half of HALF_DAY; **unmarked days are paid**, so a shop that
  does not keep attendance pays full salaries. Daily wages are for days marked worked. Piece workers get the price
  list's current piece rate for each work stage they finished (from the phase 3 stage log; an item moved back and
  finished again counts once per stage); others see their piece count only. Overtime and lateness use the hourly
  rate. Approved advances not yet taken back are deducted, and finalising marks them recovered; a net can go
  negative. Future months cannot be finalised; a month can be reopened until any payslip is paid. Paying a
  payslip records a SALARY expense, so it shows in the ledger.
- **Production ledger** — `GET /payroll/production`: stages finished per person, at piece rates. **Statement** —
  `GET /payroll/staff/{id}/statement`: earned (finalised pay before advances), advances and pay handed over, with
  what the shop owes them.

Admin pages: `/staff/` (and `new`, `edit`, `view?id=` with the statement), `/staff/roles/` (owner),
`/staff/attendance/` (day) and `/staff/attendance/month/`, `/staff/requests/`, `/requests/` (my requests),
`/payroll/` and `/payroll/production/`.

## Stock, engage, gallery, activity (phase 6)

`V11__stock_engage_activity.sql`. New permissions: STOCK, MARKETING (coupons, rewards, gallery) and REPORTS (reports,
activity log); managers get all three by default.

- **Inventory** (`/api/v1/admin/stock`) — items with a unit (metres, pieces, rolls…), reorder level and unit cost.
  Every change is a movement (IN, OUT, or a signed ADJUST), optionally for an order; on hand is the sum of
  movements and can never go below zero. The item page is its stock ledger with the balance after each movement.
- **Coupons** (`/engage/coupons`) — percent or rupee codes with a minimum order, dates and a use limit; uses are the
  orders (not cancelled) booked with the code. New order's **Apply** checks the code (`/engage/coupons/check`,
  allowed for ORDERS) and fills in the discount. Booking does not re-check the code: staff could already type any
  discount.
- **Rewards** (`/engage/rewards`) — off until switched on. A delivered order earns points (whole ₹100s × points per
  ₹100, once per order); points are redeemed or corrected by hand on the customer page. Redeeming records the
  points; give their value as the discount on the next booking.
- **Messages and review requests** (`/engage/messages`) — fill the Settings → Messages templates for an order or
  customer (order booked / ready / delivered, payment, review request with the shop's Google review link, reward
  code, or your own text) and log them. **No paid WhatsApp/SMS provider is set up:** each message comes back as a
  wa.me / sms: link that opens on the shop's phone ready to send. A provider is added by implementing
  `engage.MessageProvider` as a bean; messages are then sent and logged as SENT or FAILED.
- **Gallery** (`/api/v1/admin/gallery`) — design ideas and customers' finished garments with tags and garment;
  photos upload as 800 px WebP to R2 like product photos (needs R2; locally the upload says storage is not set up).
- **Activity log** (`/api/v1/admin/activity`, REPORTS) — written in the same transaction as order, payment,
  alteration and settings events. **Notifications** (`/me/notifications`, everyone) — live alerts (overdue, due
  today, ready, low stock, waiting requests) plus recent order and alteration updates; opening the page marks them
  seen.
- **Printing** — `/print/order/?id=` and `/print/alteration/?id=` print on the shop's paper formats (A4, A5, 80 mm,
  58 mm) following Settings → Printing (measurements, bank/UPI, terms, staff name, signature); the invoice page
  prints too. The app frame hides itself when printing. **Help** — `/help/`.

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
