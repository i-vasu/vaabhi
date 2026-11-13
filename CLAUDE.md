# Working on Vaabhi

## Code map

Read this before searching. Skip `node_modules/`, `.next/`, `out/`, `coverage/`, `target/` and `.turbo/` in
searches and directory listings; they are build output and very large.

**Backend**: `backend/`, Java 21, Spring Boot 4.1, Spring Modulith, Maven (`mvnw`), Postgres + Flyway.
- Code: `backend/src/main/java/com/vaabhi/<module>/`. Each top-level package is a Modulith module. Inside a
  module: `*Controller` (REST), `*Service` (logic), `*Store`/`*Repository` (DB), `*Form`/`*Request` (input),
  `*Response`/`*Summary`/`*Page` (output).
- Modules: `catalog` (products, categories, media, bag quote; `Store*Controller` = storefront API,
  `Admin*Controller` = admin API), `checkout` (web orders, Razorpay, Shiprocket, webhooks, returns),
  `customer` (customer auth: Google + phone OTP, sessions), `staff` (staff auth, roles, attendance,
  requests), `tailoring` (shop orders, alterations, clients, measurements, workshop), `money` (invoices,
  ledger, expenses, dues), `payroll`, `inventory`, `engage` (coupons, rewards, messages), `settings`,
  `storefront` (banners, home content), `gallery`, `reports`, `activity`.
- `shared/`: `rupees` (money type), `security` (`SecurityConfig`), `web` (`ApiExceptionHandler`,
  `OpenApiConfig`), `storage` (R2), `whatsapp` (MSG91 WhatsApp; no SMS), `email`, `cache`, `database`,
  `observability`.
- Config: `backend/src/main/resources/application.yml`, `-dev.yml`, `-prod.yml`. Migrations:
  `resources/db/migration/V<n>__*.sql`, numbered upwards with no gaps, so new = next number. Demo data:
  `resources/db/demo/R__demo_catalogue.sql`.
- Tests: `backend/src/test/java/com/vaabhi/` (`*IT` = Testcontainers, `ArchitectureTests`/`ModularityTests`).
- Runs on `:8080`; OpenAPI at `/v3/api-docs`. IntelliJ run config: `VaabhiApplication`.

**Frontend**: `frontend/`, pnpm + Turborepo, Next.js 16, React 19, Tailwind 4, Vitest.
- `storefront/` (`:3000`, server-rendered): pages in `src/app/` (`p/` product, `c/` category, `bag/`,
  `checkout/`, `account/`, `new/`, `bestsellers/`, `signup/`), route handlers in `src/app/api/`
  (`auth`, `bag`, `health`), components in `src/components/`, data/helpers in `src/lib/`
  (`api.ts` = API calls, `bag.ts`, `razorpay.ts`, `auth-server.ts`, `session.ts`).
- `admin/` (`:3001`, static export `output: "export"`): pages in `src/app/<section>/`, shared UI in
  `src/components/` (`ui.tsx`, `app-shell.tsx`, `product-editor.tsx`), API + helpers in `src/lib/`
  (`api.ts`, `auth.ts`, one file per area). Static export means no server code in admin pages.
- `packages/api-client/`: typed client. `src/schema.d.ts` and `openapi.json` are generated from the
  running backend with `pnpm generate:api`. Don't hand-edit them. After changing a backend endpoint,
  regenerate.
- `packages/ui/`: shared components (`product-card`, `button`, `chip` …) and `theme.css`.
- `packages/observability/`: Grafana Faro (browser telemetry). `packages/config/`: shared tsconfig.
- `e2e/`: Playwright tests.
- Checks (allowed): `pnpm typecheck`, `pnpm lint` from `frontend/`, or `--filter <app>`.

**Other**: `infra/` (docker-compose dev/prod, deploy, Cloudflare), `docs/` (`PLAN.md`, `ADMIN_PORTAL.md`,
`AUTH.md`, `TESTING.md`, `WORKFLOW.md`), `.github/workflows/` (CI).

**Main flow**: admin page (`frontend/admin/src/app/...`) → `admin/src/lib/api.ts` → `Admin*Controller` →
`*Service` → `*Store` → Postgres/R2 → `Store*Controller` → `storefront/src/lib/api.ts` → storefront page.

These rules apply to every agent and subagent working in this repository.

## Build the happy path first

- Focus on making the real end-to-end flow work: admin action → API → database/storage → storefront.
- Fix whatever blocks that flow (bugs, stale config, missing wiring) as part of the task. Fix only what is
  needed for the flow to work.
- Leave edge cases, polish and refactors unless they block the happy path. Mention them at the end instead.

## No hardcoded demo data

- Never hardcode products, prices, banners or other content in the frontend. Load it from the API.
- If the API has no endpoint for something a page needs, add the endpoint.
- If there is no data yet, show an honest empty state. Do not show placeholder items.

## Do not run tests

- Do not run test suites, integration tests, end-to-end tests, coverage or `verify` builds. The owner tests
  the end-to-end flow themselves.
- Typecheck and lint are allowed, because they are quick checks that the code compiles.
- You may add or update tests alongside a change when it is cheap, but do not run them.
- At the end, list what the owner should check by hand to confirm the flow works.

## Delegate implementation to DeepSeek

- Plan and review in the main session; hand each well-specified implementation chunk to DeepSeek through a
  background subagent. Write a tight spec to the scratchpad (files, signatures, behaviour, done criteria), then spawn
  a `general-purpose` subagent (model `haiku`, effort `lower`) whose only job is to run
  `bash .claude/scripts/deepseek-coder.sh <spec-file>` from the repo root and report the script's summary and
  `git status`. It must not edit files, run checks or commit itself.
- Independent chunks can run as parallel subagents; chunks that touch the same files run one after another.
- Afterwards the main session reviews `git diff`, runs the allowed checks, fixes small issues or re-runs with
  precise notes, and commits.
- Fallback when `DEEPSEEK_API_KEY` is missing or DeepSeek fails: the `coder` subagent. Keep secrets, `.env` files
  and `hs_err_pid*.log` out of specs.
- Subagents themselves never re-delegate: a subagent asked to implement something does the work directly.
