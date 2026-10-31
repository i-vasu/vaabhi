# Vaabhi

Fashion e-commerce platform: a Spring Boot modular monolith with two Next.js frontends.
See [docs/PLAN.md](docs/PLAN.md) for the architecture and roadmap.

| Path | What | Runs on |
|---|---|---|
| `backend/` | Java 21, Spring Boot 4.1, Maven, Spring Modulith, PostgreSQL | VPS (Docker) |
| `frontend/storefront/` | Customer store, Next.js (standalone server) | VPS (Docker) |
| `frontend/admin/` | Admin dashboard, Next.js static export | Cloudflare Pages |
| `frontend/packages/` | Shared `ui`, `api-client` (OpenAPI types), `config` | — |
| `infra/` | Docker Compose (dev/prod), deploy script, Cloudflare and VPS notes | — |

## Local development

Requirements: JDK 21, Node 22, pnpm 10, Docker.

```bash
docker compose -f infra/docker-compose.dev.yml up -d   # Postgres on :5432
```

```bash
cd backend && ./mvnw spring-boot:run                   # http://localhost:8080 (Swagger UI at /swagger-ui.html)
```

```bash
cd frontend && pnpm install && pnpm dev                # storefront :3000, admin :3001
```

Useful commands:

```bash
cd backend && ./mvnw verify                            # unit + Testcontainers integration tests
```

```bash
cd frontend && pnpm turbo run lint typecheck test build
```

```bash
cd frontend && pnpm generate:api                       # regenerate API types from the running backend
```

## CI/CD

| Workflow | Does |
|---|---|
| `pipeline.yml` (**CI/CD**) | one run per PR and per push to `main`: `changes` → `backend` · `frontend` · `e2e` → `sonar`; PRs also get an admin preview; on `main` then `publish` (GHCR images) → `deploy-vps` + `deploy-admin` (one approval) → `smoke`, plus `release-please` |
| `deploy.yml` | building block for the pipeline; run manually to roll back (`image_tag` = commit SHA) |
| `deploy-admin.yml` | building block: admin static export to Cloudflare Pages (preview or production); runnable manually |
| `smoke.yml` | read-only Playwright `@smoke` tests against production: after each deploy, daily, on demand |
| `pr-title.yml` | PR titles must follow Conventional Commits (squash-merge message, drives versioning) |
| `codeql.yml` | CodeQL for Java and TypeScript (PRs, `main`, weekly) |

### Secrets and variables

Repository **secrets**: `SONAR_TOKEN` (SonarQube Cloud), `VPS_HOST`, `VPS_USER`, `VPS_SSH_KEY`, `VPS_PORT` (SSH port, default 22), `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID`.

Repository **variables**: `PUBLIC_API_URL` (e.g. `https://api.vaabhi.com`), `CF_IMAGES_ACCOUNT_HASH`,
`FARO_URL_STOREFRONT`, `FARO_URL_ADMIN`.

Runtime configuration for the VPS lives in `/opt/vaabhi/.env` — see `infra/.env.example`
and `infra/vps-setup.md`.

See [docs/TESTING.md](docs/TESTING.md) for the test layers and how to run them, and
[docs/WORKFLOW.md](docs/WORKFLOW.md) for branching, releases, deployment and rollback.

## Observability

Backend metrics, traces and logs go straight to Grafana Cloud over OTLP (no collector on the
VPS); the storefront server uses `@vercel/otel`; both browsers use Grafana Faro. Export is off
unless `OTEL_EXPORT_ENABLED=true` / the endpoint variables are set.
