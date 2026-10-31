# Branching, releases and deployment

## Branching: trunk-based

- `main` is always deployable. Nothing is pushed to it directly; every change arrives through a pull request.
- Work on short-lived branches cut from `main`, named by type: `feat/product-variants`, `fix/cart-total`,
  `chore/upgrade-next`, `docs/testing`. Keep them small and merge within days, not weeks.
- PR titles follow [Conventional Commits](https://www.conventionalcommits.org): `feat: …`, `fix: …`, `perf: …`,
  `refactor: …`, `test: …`, `build: …`, `ci: …`, `docs: …`, `chore: …` (the `pr-title` check enforces this).
  Add `!` for breaking changes: `feat!: …`.
- PRs are **squash-merged**: one commit per PR on `main`, with the PR title as the message. Delete the branch after.

## Protection rules on `main` (GitHub ruleset)

| Rule | Setting |
|---|---|
| Restrict deletions, block force pushes | on |
| Require a pull request before merging | on, 0 approvals (solo developer; raise when the team grows) |
| Allowed merge method | squash only |
| Require status checks to pass | `backend`, `frontend`, `e2e`, `sonar`, `pr-title`, `CodeQL` analyze jobs |
| Bypass | repository admin, for pull requests only (needed to merge release-please PRs, which GitHub does not run CI on) |

## Checks on every PR

| Check | What it does |
|---|---|
| `backend` | Error Prone, Spotless, unit + integration tests, JaCoCo ≥ 70% (skipped when `backend/` is untouched) |
| `frontend` | Prettier, ESLint, types, Vitest with coverage ≥ 70%, builds (skipped when `frontend/` is untouched) |
| `e2e` | Full stack in CI + Playwright + accessibility (skipped when neither app changed) |
| `sonar` | SonarQube Cloud quality gate on new code |
| `pr-title` | Conventional Commits title |
| CodeQL | Security analysis for Java and TypeScript |
| Deploy admin (preview) | Admin built and deployed to a Pages preview URL |

## Releases

[release-please](https://github.com/googleapis/release-please) keeps a **release PR** open that lists everything merged
since the last release. Merging it:

- bumps the version (pre-1.0: `feat` → minor, `fix` → patch; `!` → minor),
- updates `CHANGELOG.md`,
- creates a GitHub Release and a `vX.Y.Z` tag.

The version is kept in sync across the monorepo: release-please also bumps `backend/pom.xml` (the line marked
`x-release-please-version`) and the `version` of `frontend/storefront/package.json` and `frontend/admin/package.json`.

**Which release is live?** Every build carries the version plus the short commit SHA, e.g. `v0.2.0 (fc31d29)`:

| Where | How |
|---|---|
| Backend | `https://api.vaabhi.com/actuator/info` → `build.version`, `git.commit.id` |
| Admin | sidebar footer |
| Storefront | page source → `<meta name="app-version">` |
| Grafana Faro | app version on every frontend session |

After each VPS deploy the smoke tests check that the API reports exactly the commit that was deployed.

Deployment does not wait for releases: every merge to `main` goes to production (after approval). Tags are the named
rollback points and the changelog for humans.

## Deployment

```
merge to main ─▶ Publish images (tests + Docker → GHCR, tagged with the commit SHA)
              ─▶ Deploy to VPS  ── approval (production environment) ──▶ deploy.sh ─▶ smoke tests
              ─▶ Deploy admin   ── approval ──▶ Cloudflare Pages ─▶ smoke tests
```

- `deploy.sh` pulls the new images, restarts the containers and waits for health checks. If they fail, it redeploys
  the previous image automatically.
- The smoke workflow runs the read-only `@smoke` Playwright tests against production after every deploy and daily.
- Changes to docs only (`**/*.md`, `docs/**`) do not build or deploy anything.
- There is no staging server (the 1 GB VPS cannot run a second copy). Instead: the full stack in CI (`e2e`),
  admin preview URLs on Cloudflare Pages, and smoke tests in production.

### Rollback

1. Actions → **Deploy to VPS** → **Run workflow** → `image_tag` = the commit SHA of the last good version (find it on the
   release/tag or in the Publish run).
2. For the admin: Cloudflare dashboard → Pages → `vaabhi-admin` → Deployments → **Rollback** on the last good one, or
   revert the PR and merge.
3. Prefer a revert PR (`git revert`) for anything that stays rolled back, so `main` matches production again.

### Database migrations

- Flyway migrations are forward-only and run when the backend starts.
- Keep every migration compatible with the version currently in production (**expand → contract**): add new
  columns/tables first, switch the code over in a release, remove the old ones in a later release.
- Before a risky migration, note the time: Neon can restore the database to any point in its history window.

### Hotfix

Same flow, just faster: `fix/…` branch from `main` → PR → checks → merge → approve deploy. There are no long-lived
release branches to patch.
