# Vaabhi — Fashion E-commerce + Boutique Platform Plan

A modular-monolith fashion e-commerce platform with an integrated point-of-sale (POS)
for an offline ladies boutique. Backend and frontends live in one repository.

## 1. Decisions

| Area | Choice | Cost |
|---|---|---|
| Backend | Java 21, Spring Boot 3.x, Gradle (Kotlin DSL), Spring Modulith | — |
| Frontend | Next.js (App Router, TypeScript), pnpm workspaces + Turborepo | — |
| Server | AIC Cloud VPS, 1 GB RAM | already paid |
| Database | **Neon Postgres, free tier** (0.5 GB, scale-to-zero) | free |
| Images | **Cloudflare Images** (direct creator upload, variants) | low usage-based |
| Videos | **Cloudflare Stream** (direct creator upload, HLS) | low usage-based |
| Edge / DNS / TLS | Cloudflare (proxied DNS, WAF, Cloudflare Tunnel to VPS) | free |
| Admin hosting | **Cloudflare Pages** (static export) | free |
| Storefront hosting | VPS (Next.js standalone, behind Cloudflare) | — |
| Payments | **Razorpay** (online checkout + UPI QR at boutique counter) | per-transaction |
| Observability | Grafana Cloud free tier (OTLP direct, Faro RUM) | free |
| CI/CD | GitHub Actions, images in GHCR | free tier |
| Email | Resend / Brevo free tier | free |

Constraints: no AWS, no Redis, no MinIO, no self-hosted collectors; nothing is built on the VPS.

## 2. Repository layout

```
vaabhi/
├── backend/
│   ├── src/main/java/com/vaabhi/
│   │   ├── catalog/        # products, variants (size × colour), categories, collections
│   │   ├── inventory/      # stock per SKU per location, movements, transfers
│   │   ├── cart/
│   │   ├── order/          # online orders, fulfilment, returns/exchanges
│   │   ├── payment/        # Razorpay adapter, webhooks
│   │   ├── customer/       # accounts, addresses, wishlist, walk-in customers
│   │   ├── promotion/
│   │   ├── media/          # Cloudflare Images + Stream
│   │   ├── pos/            # boutique billing, cash register, receipts
│   │   ├── alteration/     # stitching/alteration orders, measurements
│   │   ├── invoice/        # GST invoices (online + POS)
│   │   ├── staff/          # admin/staff users, roles
│   │   ├── reporting/      # sales, stock, GST reports
│   │   └── shared/
│   ├── src/main/resources/db/migration/
│   └── build.gradle.kts
├── frontend/
│   ├── storefront/         # customer site (VPS)
│   ├── admin/              # admin dashboard + POS (Cloudflare Pages, static export)
│   ├── packages/
│   │   ├── ui/             # shared components (Tailwind + shadcn/ui)
│   │   ├── api-client/     # TS client generated from backend OpenAPI
│   │   └── config/         # eslint, tsconfig, tailwind presets
│   ├── pnpm-workspace.yaml
│   └── turbo.json
├── infra/
│   ├── docker-compose.dev.yml    # local Postgres for development only
│   ├── docker-compose.prod.yml   # backend, storefront, cloudflared (mem limits)
│   └── cloudflare/               # tunnel config, Pages/wrangler config
├── .github/workflows/
└── docs/
```

## 3. Backend

- Modular monolith; Spring Modulith verifies module boundaries and carries in-process
  events (`OrderPlaced`, `PosSaleCompleted`, `StockLow`, `MediaReady`).
- Spring Web, Spring Data JPA, Flyway, Bean Validation, springdoc-openapi.
- Spring Security issuing and validating JWTs (no Keycloak). Roles:
  `CUSTOMER`, `OWNER`, `MANAGER`, `CASHIER`, `TAILOR`.
- Caffeine for in-process caching (catalog, categories, settings).
- Cart persisted in Postgres (guest cart keyed by cookie id, merged on login).
- Search: Postgres full-text + `pg_trgm`.
- Scheduled jobs via `@Scheduled` (orphan media cleanup, low-stock alerts, daily reports).
- HTTP to Cloudflare and Razorpay via Spring `RestClient` (no vendor SDKs needed).
- API namespaces: `/api/v1/store/**`, `/api/v1/admin/**`, `/api/v1/pos/**`, `/api/v1/webhooks/**`.

### Fashion domain

- Product → Variant (size × colour) → SKU + barcode.
- Images per colour; size chart per category; fabric, care, fit attributes.
- Collections, seasonal drops, lookbooks.
- Returns and exchanges (size swap flow), online and in-store.

### JVM tuning for 1 GB

```
-Xmx200m -Xss512k -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC
-XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError
spring.threads.virtual.enabled=true
spring.datasource.hikari.maximum-pool-size=5
```

Fallback if memory is still tight: GraalVM native image via Spring Boot AOT (~100 MB RSS).

## 4. Offline boutique (admin → POS module)

The admin dashboard doubles as the boutique's counter system. Online store and boutique
share **one catalogue and one inventory**, tracked per location.

### Inventory

- Locations: `BOUTIQUE` (and optionally `WAREHOUSE`). Online orders are fulfilled from a
  configurable location.
- Every change is an inventory movement (sale, return, adjustment, transfer, receipt) —
  full audit trail.
- Purchase / stock receipt entry from suppliers.
- Barcode label printing per SKU (browser print, Code128, label-printer CSS).

### Billing (POS screen)

- Barcode scanner (USB, acts as keyboard) or search to add items.
- Walk-in customer lookup by phone; create on the fly.
- Discounts (line / bill), coupons, staff discount with permission check.
- Payment modes: cash, card (external terminal, recorded), **Razorpay UPI QR**
  (dynamic QR, payment confirmed via webhook), split payments.
- GST invoice: HSN codes, CGST/SGST (intra-state) / IGST, invoice numbering per financial year.
- Receipt printing on 80 mm thermal printer (print CSS) and share via WhatsApp/SMS link
  (hosted invoice page).
- In-store returns and exchanges, credit notes, store credit.

### Cash register

- Shift open (opening float) and close (counted cash vs expected), Z-report per day.
- Cash-in / cash-out entries (petty expenses).

### Alterations & custom stitching

- Order with garment, work type, price, advance paid, trial and delivery dates.
- Customer measurements saved to profile (blouse, kurti, salwar, etc.) and reusable.
- Status board: received → in progress → trial → ready → delivered; assign to tailor.
- Reminders to customer when ready.

### Staff

- Staff accounts with roles and PIN-based quick login on the POS screen.
- Per-staff sales tracking (for incentives).

### Reports

- Daily/monthly sales by channel (online vs boutique), payment mode, staff.
- Stock on hand, low stock, dead stock, best sellers by size/colour.
- GST summary (GSTR-1 friendly export as CSV).

### Connectivity

- Phase 1: POS requires internet (it is an SPA on Cloudflare Pages calling the API).
- Later: PWA with IndexedDB queue so bills can be created during short outages and
  synced when back online.

## 5. Frontend

**storefront/** (VPS)
- Server components + ISR for home, listing, product pages; SEO metadata, JSON-LD, sitemap.
- Facets in URL params; size/colour picker; cart drawer; Razorpay Checkout; account,
  orders, wishlist, returns.
- `next/image` with a custom Cloudflare Images loader; no image optimization on the VPS.
- Node started with `--max-old-space-size=128`.

**admin/** (Cloudflare Pages, `output: 'export'`)
- TanStack Query + Table, react-hook-form + zod.
- Modules: dashboard KPIs, products & variants (bulk CSV import), media library,
  inventory, online orders & fulfilment, returns, customers, promotions,
  **POS billing, cash register, alterations, staff, reports**.
- Auth client-side with JWT from the backend; route guards per role.

**Shared:** Vitest + Testing Library, Playwright e2e, ESLint, Prettier.

## 6. Media (Cloudflare Images + Stream)

1. Admin requests `POST /api/v1/admin/media/upload-url` (`kind=IMAGE|VIDEO`).
2. Backend calls Cloudflare Images / Stream `direct_upload` API and returns a one-time URL.
3. Browser uploads directly to Cloudflare — no bytes touch the VPS.
4. Webhook / confirm call marks the media row `READY`.

```
media(id, kind, cf_image_id | stream_uid, product_id, color_code, position,
      alt_text, width, height, status, created_at)
```

Image variants: `thumb`, `card`, `pdp`, `zoom` (or flexible variants). Nightly job
removes orphaned uploads.

## 7. Production topology

```
Customers ──▶ Cloudflare (DNS, CDN, WAF) ──Tunnel──▶ VPS (1 GB)
                                                      ├── storefront :3000
                                                      └── backend    :8080 ──▶ Neon Postgres
Staff ──▶ admin.vaabhi.* (Cloudflare Pages) ──────────▶ api via Cloudflare
Media ──▶ Cloudflare Images / Stream
Apps  ──OTLP──▶ Grafana Cloud    Browsers ──Faro──▶ Grafana Cloud
```

Memory budget: OS + Docker ~210 MB, cloudflared ~30 MB, backend ~320 MB,
storefront ~150 MB → ~710 MB, plus a 2 GB swap file. Each container has `mem_limit`.

Neon note: the free tier scales to zero after inactivity; the first request after idle
takes ~0.5–1 s. Acceptable at launch; upgrade to a paid plan when traffic grows.

## 8. CI/CD (GitHub Actions)

| Workflow | Trigger | Steps |
|---|---|---|
| `backend-ci.yml` | PR/push, `backend/**` | Temurin 21 + Gradle cache → `./gradlew check` (unit, Testcontainers Postgres, Modulith, ArchUnit, Spotless) → JaCoCo |
| `frontend-ci.yml` | PR/push, `frontend/**` | pnpm cache → `turbo lint typecheck test build` (matrix: storefront, admin) → Playwright smoke |
| `publish.yml` | push to `main`, tags `v*` | backend image (Jib, `eclipse-temurin:21-jre-alpine`) + storefront image (`node:22-alpine`, standalone) → GHCR (`sha`, semver) → Trivy scan |
| `deploy.yml` | after publish | SSH to VPS → `docker compose pull && up -d` → health check `/actuator/health` → auto-rollback to previous tag; prod gated by GitHub Environment approval |
| `deploy-admin.yml` | push to `main`, `frontend/admin/**` | static export → `cloudflare/wrangler-action` → Pages (previews on PRs) |
| `db-backup.yml` | nightly | `pg_dump` from Neon → compressed, stored as a private artifact / Cloudflare R2 free tier |
| `security.yml` | weekly + PRs | CodeQL (Java, TS); Dependabot for Gradle, npm, Actions |

Conventional Commits + release-please for versions and changelog. Flyway migrations run
on backend startup. A deploy restarts containers (a few seconds of downtime — not enough
RAM for blue/green).

Secrets (GitHub Environments → VPS env file): `NEON_DATABASE_URL`, `JWT_SECRET`,
`RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET`, `CF_ACCOUNT_ID`,
`CF_API_TOKEN`, `CF_TUNNEL_TOKEN`, `CF_WEBHOOK_SECRET`, `GRAFANA_OTLP_ENDPOINT`,
`GRAFANA_OTLP_TOKEN`, `VPS_SSH_KEY`, `VPS_HOST`.

## 9. Observability (Grafana Cloud)

- Backend: `micrometer-registry-otlp` (metrics), `micrometer-tracing-bridge-otel` +
  OTLP exporter (traces, ~10% sampling in prod), structured JSON logs with trace ids via
  OTLP logging. All sent directly to the Grafana Cloud OTLP gateway.
- Storefront server: `instrumentation.ts` with `@vercel/otel`.
- Browsers: Grafana Faro in storefront and admin (Web Vitals, JS errors).
- Synthetic monitoring on `/actuator/health` and the storefront home page.
- Dashboards: JVM, API RED, Web Vitals, business (GMV, orders by channel, POS sales,
  conversion, top SKUs).
- Alerts: heap > 85 %, container restarts, checkout error rate, payment webhook failures,
  p95 latency, health check down, low stock.
- Keep within free tier: low-cardinality metrics, sampled traces, INFO logs only.

## 10. Roadmap

1. **Foundation** — repo scaffold, CI, backend skeleton (health, OpenAPI, auth), both
   Next.js apps, shared packages, Neon, Cloudflare Tunnel, Grafana wiring.
2. **Catalogue & media** — products/variants/barcodes, Cloudflare Images/Stream uploads,
   storefront listing and product pages.
3. **Online commerce** — cart, Razorpay checkout + webhooks, orders, customer accounts.
4. **Boutique POS** — locations & inventory movements, billing screen, GST invoices,
   receipts, UPI QR, cash register, staff roles.
5. **Alterations & reports** — stitching orders, measurements, sales/stock/GST reports.
6. **Hardening** — deploy pipeline to prod, backups, alerts/SLOs, k6 load test,
   SEO/performance, POS offline mode (PWA).
