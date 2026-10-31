# Testing

| Layer | Tooling | Where | Runs in |
|---|---|---|---|
| Backend unit | JUnit 5, AssertJ, Mockito | `backend/src/test/**/*Test.java` | CI/CD `backend` job |
| Backend web slice | `@WebMvcTest` + MockMvc (incl. security rules) | `*ControllerTest.java` | CI/CD `backend` job |
| Backend integration | `@SpringBootTest` + Testcontainers Postgres (Flyway, JPA, security, CORS) | `*IT.java` | CI/CD `backend` job (skipped locally without Docker) |
| Architecture | Spring Modulith (`ModularityTests`) + ArchUnit (`ArchitectureTests`) | `backend/src/test/java/com/vaabhi` | CI/CD `backend` job |
| API contract | `OpenApiContractIT`: live spec must equal `frontend/packages/api-client/openapi.json` | backend IT | CI/CD `backend` job |
| Frontend unit | Vitest | `frontend/**/src/**/*.test.ts` | CI/CD `frontend` job |
| Frontend component | Vitest + React Testing Library + MSW (mocked API, unhandled requests fail) | `frontend/{storefront,admin}/src/**/*.test.tsx` | CI/CD `frontend` job |
| End-to-end | Playwright against the full stack started in CI | `frontend/e2e/tests` | CI/CD `e2e` job (PRs, `main`) |
| Accessibility | axe-core inside Playwright (no serious/critical violations) | `frontend/e2e/tests` | CI/CD `e2e` job |
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

## Coverage and quality gates

| Gate | Tool | Threshold | Fix locally |
|---|---|---|---|
| Java coverage | JaCoCo (unit + integration merged) | 70% lines; enforced in CI (`CI=true`) | Report: `backend/target/site/jacoco/index.html` |
| Frontend coverage | Vitest V8 coverage, per package | 70% lines | `pnpm turbo run test`; report in `<pkg>/coverage/` |
| Java formatting | Spotless + Palantir Java Format | must be formatted | `cd backend && ./mvnw spotless:apply` |
| Frontend formatting | Prettier (`printWidth` 120) | must be formatted | `cd frontend && pnpm format` |
| Java bug patterns | Error Prone (compile time) | errors fail the build; fix warnings too | `./mvnw compile` |
| Lint / types | ESLint (Next.js rules), `tsc` | no errors | `pnpm turbo run lint typecheck` |
| Overall quality | SonarQube Cloud, "Sonar way" gate on **new code** (no new bugs/vulnerabilities, hotspots reviewed, ≥80% coverage, ≤3% duplication) | gate must pass | see the SonarQube Cloud report linked from the PR |

Coverage excludes framework wiring that the Playwright suite covers instead: `VaabhiApplication`, the Next.js root
`layout.tsx` and `instrumentation.ts`. Raise the 70% floors as the codebase grows; never lower them to make a build pass.
