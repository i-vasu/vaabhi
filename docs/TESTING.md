# Testing

| Layer | Tooling | Where | Runs in |
|---|---|---|---|
| Backend unit | JUnit 5, AssertJ, Mockito | `backend/src/test/**/*Test.java` | Backend CI |
| Backend web slice | `@WebMvcTest` + MockMvc (incl. security rules) | `*ControllerTest.java` | Backend CI |
| Backend integration | `@SpringBootTest` + Testcontainers Postgres (Flyway, JPA, security, CORS) | `*IT.java` | Backend CI (skipped locally without Docker) |
| Architecture | Spring Modulith (`ModularityTests`) + ArchUnit (`ArchitectureTests`) | `backend/src/test/java/com/vaabhi` | Backend CI |
| API contract | `OpenApiContractIT`: live spec must equal `frontend/packages/api-client/openapi.json` | backend IT | Backend CI |
| Frontend unit | Vitest | `frontend/**/src/**/*.test.ts` | Frontend CI |
| Frontend component | Vitest + React Testing Library + MSW (mocked API, unhandled requests fail) | `frontend/{storefront,admin}/src/**/*.test.tsx` | Frontend CI |
| End-to-end | Playwright against the full stack started in CI | `frontend/e2e/tests` | E2E workflow (PRs, `main`) |
| Accessibility | axe-core inside Playwright (no serious/critical violations) | `frontend/e2e/tests` | E2E workflow |
| Production smoke | Playwright `@smoke` tests, read-only, against the live sites | `frontend/e2e/tests` | After each deploy, daily, on demand |

Later: PIT mutation testing, Playwright visual snapshots, k6 load tests (results to Grafana Cloud).

## Running locally

Backend — unit, slice, architecture (integration and contract tests need Docker):

```bash
cd backend && ./mvnw verify
```

Frontend — lint, types, unit and component tests:

```bash
cd frontend && pnpm turbo run lint typecheck test
```

End-to-end — start the stack (Postgres from `infra/docker-compose.dev.yml`, backend on :8080, storefront on
:3000, admin on :3001), then:

```bash
cd frontend/e2e && pnpm exec playwright test
```

Smoke tests against production (read-only):

```bash
cd frontend/e2e && STOREFRONT_URL=https://vaabhi.com ADMIN_URL=https://admin.vaabhi.com API_URL=https://api.vaabhi.com pnpm exec playwright test --grep @smoke
```

## Conventions

- Backend: `*Test` = unit/slice (Surefire), `*IT` = needs Docker (Failsafe).
- Every API change updates the committed spec: `./mvnw verify -Dopenapi.update=true` (with Docker), then
  `pnpm generate:api` in `frontend/` to refresh the TypeScript types. CI fails if they drift.
- Component tests declare every API call with MSW; an unmocked request fails the test.
- Tag Playwright tests `@smoke` only if they are read-only and safe to run against production.
