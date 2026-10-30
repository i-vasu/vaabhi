# Vaabhi — Fashion E-commerce Platform Plan

A modular-monolith fashion e-commerce platform. Backend and frontends live in one repository.
Offline-boutique functionality for the admin dashboard will be specified separately.

## 1. Decisions

| Area | Choice | Cost |
|---|---|---|
| Backend | Java 21, Spring Boot 4.1, Maven (wrapper), Spring Modulith | — |
| Frontend | Next.js (App Router, TypeScript), pnpm workspaces + Turborepo | — |
| Server | AIC Cloud VPS, 1 GB RAM | already paid |
| Database | **Neon Postgres, free tier** (0.5 GB, scale-to-zero) | free |
| Images | **Cloudflare Images** (direct creator upload, variants) | low usage-based |
| Videos | **Cloudflare Stream** (direct creator upload, HLS) | low usage-based |
| Edge / DNS / TLS | Cloudflare (proxied DNS, WAF, Cloudflare Tunnel to VPS) | free |
| Admin hosting | **Cloudflare Pages** (static export) | free |
| Storefront hosting | VPS (Next.js standalone, behind Cloudflare) | — |
| Payments | **Razorpay** (online checkout) | per-transaction |
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
│   │   ├── customer/       # accounts, addresses, wishlist
│   │   ├── promotion/
│   │   ├── media/          # Cloudflare Images + Stream
│   │   ├── invoice/        # GST invoices
│   │   ├── staff/          # admin users, roles
│   │   ├── reporting/      # sales and stock reports
│   │   └── shared/
│   ├── src/main/resources/db/migration/
│   └── pom.xml
├── frontend/
│   ├── storefront/         # customer site (VPS)
│   ├── admin/              # admin dashboard (Cloudflare Pages, static export)
│   ├── packages/
│   │   ├── ui/             # shared components (Tailwind + shadcn/ui)
│   │   ├── api-client/     # TS client generated from backend OpenAPI
│   │   └── config/         # eslint, tsconfig, tailwind presets
│   ├── pnpm-workspace.yaml
│   └── turbo.json
├── infra/
│   ├── docker-compose.dev.yml    # local Postgres for development only
│   ├── docker-compose.prod.yml   # backend, storefront, cloudflared (mem limits)
│   ├── deploy.sh                 # pull, up, health check, rollback (runs on the VPS)
│   └── cloudflare/               # tunnel, Pages, Images setup notes
├── .github/workflows/
└── docs/
```

## 3. Backend

- Modular monolith; Spring Modulith verifies module boundaries and carries in-process
  events (`OrderPlaced`, `StockLow`, `MediaReady`).
- Spring Web, Spring Data JPA, Flyway, Bean Validation, springdoc-openapi.
- Spring Security issuing and validating JWTs (no Keycloak). Roles: `CUSTOMER`, `ADMIN`
  (more staff roles later).
- Caffeine for in-process caching (catalog, categories, settings).
- Cart persisted in Postgres (guest cart keyed by cookie id, merged on login).
- Search: Postgres full-text + `pg_trgm`.
- Scheduled jobs via `@Scheduled` (orphan media cleanup, low-stock alerts, daily reports).
- HTTP to Cloudflare and Razorpay via Spring `RestClient` (no vendor SDKs needed).
- API namespaces: `/api/v1/store/**`, `/api/v1/admin/**`, `/api/v1/webhooks/**`.

### Fashion domain

- Product → Variant (size × colour) → SKU + barcode.
- Images per colour; size chart per category; fabric, care, fit attributes.
- Collections, seasonal drops, lookbooks.
- Returns and exchanges (size swap flow).

### JVM tuning for 1 GB

```
-Xmx200m -Xss512k -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC
-XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError
spring.threads.virtual.enabled=true
spring.datasource.hikari.maximum-pool-size=5
```

Fallback if memory is still tight: GraalVM native image via Spring Boot AOT (~100 MB RSS).

## 4. Frontend

**storefront/** (VPS)
- Server components + ISR for home, listing, product pages; SEO metadata, JSON-LD, sitemap.
- Facets in URL params; size/colour picker; cart drawer; Razorpay Checkout; account,
  orders, wishlist, returns.
- `next/image` with a custom Cloudflare Images loader; no image optimization on the VPS.
- Node started with `--max-old-space-size=128`.

**admin/** (Cloudflare Pages, `output: 'export'`)
- TanStack Query + Table, react-hook-form + zod.
- Modules: dashboard KPIs, products & variants (bulk CSV import), media library,
  inventory, orders & fulfilment, returns, customers, promotions, reports.
- Auth client-side with JWT from the backend; route guards per role.

**Shared:** Vitest + Testing Library, Playwright e2e, ESLint, Prettier.

## 5. Media (Cloudflare Images + Stream)

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

## 6. Production topology

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

## 7. CI/CD (GitHub Actions)

| Workflow | Trigger | Steps |
|---|---|---|
| `backend-ci.yml` | PR/push, `backend/**` | Temurin 21 + Maven cache → `./mvnw verify` (unit tests, Testcontainers Postgres ITs, Modulith verification) |
| `frontend-ci.yml` | PR/push, `frontend/**` | pnpm cache → `turbo lint typecheck test build` (matrix: storefront, admin) → Playwright smoke |
| `publish.yml` | push to `main`, tags `v*` | backend image (layered jar on `eclipse-temurin:21-jre-alpine`) + storefront image (`node:22-alpine`, standalone) → GHCR (`sha`, semver, `latest`) |
| `deploy.yml` | after publish | SSH to VPS → `docker compose pull && up -d` → health check `/actuator/health` → auto-rollback to previous tag; prod gated by GitHub Environment approval |
| `deploy-admin.yml` | push to `main`, `frontend/admin/**` | static export → `cloudflare/wrangler-action` → Pages (previews on PRs) |
| `db-backup.yml` (later) | nightly | `pg_dump` from Neon → compressed, stored as a private artifact / Cloudflare R2 free tier |
| `codeql.yml` | weekly + PRs | CodeQL (Java, TS); Dependabot for Maven, npm, Actions, Docker |

Conventional Commits + release-please for versions and changelog. Flyway migrations run
on backend startup. A deploy restarts containers (a few seconds of downtime — not enough
RAM for blue/green).

Secrets (GitHub Environments → VPS env file): `NEON_DATABASE_URL`, `JWT_SECRET`,
`RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET`, `CF_ACCOUNT_ID`,
`CF_API_TOKEN`, `CF_TUNNEL_TOKEN`, `CF_WEBHOOK_SECRET`, `GRAFANA_OTLP_ENDPOINT`,
`GRAFANA_OTLP_TOKEN`, `VPS_SSH_KEY`, `VPS_HOST`.

## 8. Observability (Grafana Cloud)

- Backend: `micrometer-registry-otlp` (metrics), `micrometer-tracing-bridge-otel` +
  OTLP exporter (traces, ~10% sampling in prod), structured JSON logs with trace ids via
  OTLP logging. All sent directly to the Grafana Cloud OTLP gateway.
- Storefront server: `instrumentation.ts` with `@vercel/otel`.
- Browsers: Grafana Faro in storefront and admin (Web Vitals, JS errors).
- Synthetic monitoring on `/actuator/health` and the storefront home page.
- Dashboards: JVM, API RED, Web Vitals, business (GMV, orders, conversion, top SKUs).
- Alerts: heap > 85 %, container restarts, checkout error rate, payment webhook failures,
  p95 latency, health check down, low stock.
- Keep within free tier: low-cardinality metrics, sampled traces, INFO logs only.

## 9. Roadmap

1. **Foundation (done: skeleton)** — repo scaffold, CI/CD workflows, backend skeleton
   (health, OpenAPI, JWT resource server, sample catalogue endpoint), both Next.js apps,
   shared packages, Cloudflare Tunnel compose, Grafana OTLP wiring.
2. **Auth** — admin and customer login, token issuance, refresh, roles.
3. **Catalogue & media** — products/variants, Cloudflare Images/Stream uploads,
   storefront listing and product pages.
4. **Online commerce** — cart, Razorpay checkout + webhooks, orders, customer accounts.
5. **Admin operations** — fulfilment, returns, promotions, reports.
6. **Offline boutique** — to be specified.
7. **Hardening** — backups, alerts/SLOs, k6 load test, SEO/performance.
