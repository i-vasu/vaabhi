# Books Phase 6: Daily Backups in India Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every night at 01:00 IST the backend dumps the Neon books database and new bill attachments to an
Object-Locked AWS S3 bucket in Mumbai, records each run, checks a backup monthly, and shows backup health in the
admin, the year-end checklist and the auditor pack.

**Architecture:** New Modulith module `backup`. `pg_dump` runs as a child process into a temp file; the file is
hashed and sent with one header-signed SigV4 PUT (no AWS SDK) carrying `x-amz-checksum-sha256` and an Object Lock
retain-until date. Runs are rows in `backup_run`; copied attachments in `backup_file`. `accounts` reads
`BackupStatusService` for the year-end check and the auditor pack.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Modulith, JdbcClient, Flyway, `java.net.http.HttpClient`,
Testcontainers (Postgres, MinIO); Next.js 16 static-export admin.

**Spec:** `docs/superpowers/specs/2026-11-14-phase6-backups-india-design.md`

**Deviations from spec (wiring only):**
- REST lives under `/api/v1/admin/accounts/backups/...` (not `/api/v1/admin/backups`) so the existing
  SecurityConfig rules apply unchanged: GET = MONEY area, POST = OWNER.
- `pg_dump` sits behind a `DatabaseDumper` interface so ITs can use a stub that writes bytes (the test host may not
  have `pg_dump`); the real `PgDumpDumper` is unit-tested on its command line and version parsing.

## Global Constraints

- Migration `V38__backups.sql`; never edit V1–V37.
- Schedules in `Asia/Kolkata`: backup `0 0 1 * * *`, retry `0 30 1 * * *`, monthly check `0 0 2 1 * *`.
- Daily key `db/yyyy/MM/vaabhi-yyyy-MM-ddTHHmm.dump` (IST), locked 35 days. Month-end key
  `monthly/vaabhi-yyyy-MM.dump`, locked 8 years + 1 day. Attachments `attachments/<source_key>`, locked 8 years + 1 day.
- Object Lock mode `COMPLIANCE`; `x-amz-server-side-encryption: AES256`; no delete calls anywhere in `backup`.
- Neon password only via `PGPASSWORD` env of the child process; never in argv or logs.
- Config prefix `vaabhi.backup`, env `BACKUP_*`; `enabled` false by default, true in `application-prod.yml`.
- Copy: "Backups aren't set up", "Last night's backup didn't run", "A backup is running",
  "pg_dump <c> can't back up Postgres <s>; update the backend image", "Upload check failed".
- API record names globally unique (springdoc). Static-export admin: no dynamic routes.
- Tests written with each task, **not run** (CLAUDE.md). Backend check: scratch copy `./mvnw -q -o compile
  test-compile -DskipTests`. Frontend: `pnpm typecheck` + `pnpm lint --filter admin`.
- Implementation via `bash .claude/scripts/deepseek-coder.sh <spec>`; main session reviews, compiles, commits. Stage
  `backend/src`, `backend/pom.xml`, `backend/Dockerfile`, `frontend`, `docs`, `infra`; never `git add backend`.

## Review Focus

1. Month-end detection at 01:00 IST on the 1st vs the last day: the run at 01:00 on 31 Jan IST is the month-end
   backup of January (`monthly/vaabhi-2027-01.dump`); the run on 1 Feb is not. Test in Task 1.
2. A `RUNNING` row left by a crash (backend restarted mid-dump) must not block every later night: older than 2 h →
   `FAILED` "Interrupted", and the next run proceeds. Test in Task 3.
3. The temp dump file is deleted on success and on every failure path (disk is 10 GB). Test in Task 3.
4. `missedLastNight` before 02:00 IST looks at yesterday, after 02:00 at today; a manual "Back up now" counts. Test
   in Task 4.
5. An attachment whose R2 object is gone (deleted bill file) is noted once per night and never fails the DB backup.
   Test in Task 4.

---

### Task 1: Schema, properties, key rules

**Files:**
- Create: `backend/src/main/resources/db/migration/V38__backups.sql`
- Create: `backend/src/main/java/com/vaabhi/backup/package-info.java`, `BackupProperties.java`, `BackupKeys.java`
- Modify: `backend/src/main/resources/application.yml` (defaults), `application-prod.yml` (`enabled: true`, env refs)
- Test: `backend/src/test/java/com/vaabhi/backup/BackupKeysTest.java`

**Interfaces:**
- Produces: `@ConfigurationProperties("vaabhi.backup") record BackupProperties(boolean enabled, @Nullable String
  databaseUrl, @Nullable String databaseUser, @Nullable String databasePassword, String s3Endpoint, String s3Region,
  @Nullable String s3Bucket, @Nullable String s3AccessKeyId, @Nullable String s3SecretAccessKey, String
  locationLabel, String pgDumpPath, String pgRestorePath, @Nullable String alertPhone) { boolean configured(); }`
  defaults: endpoint `https://s3.ap-south-1.amazonaws.com`, region `ap-south-1`, label `AWS S3, Mumbai (ap-south-1)`,
  `pg_dump`, `pg_restore`. Env: `BACKUP_ENABLED`, `BACKUP_DATABASE_URL` (fallback `${DATABASE_URL_UNPOOLED:}`),
  `BACKUP_DATABASE_USER`, `BACKUP_DATABASE_PASSWORD`, `BACKUP_S3_ENDPOINT`, `BACKUP_S3_REGION`, `BACKUP_S3_BUCKET`,
  `BACKUP_S3_ACCESS_KEY_ID`, `BACKUP_S3_SECRET_ACCESS_KEY`, `BACKUP_ALERT_PHONE`.
- Produces: `final class BackupKeys { static String daily(ZonedDateTime ist); static boolean isMonthEnd(LocalDate
  istDate); static String monthly(LocalDate istDate); static String attachment(String sourceKey); static Instant
  dailyRetainUntil(Instant now); static Instant longRetainUntil(Instant now) }`.
- `package-info`: `@ApplicationModule(allowedDependencies = {"shared :: storage", "shared :: whatsapp"})`.

- [ ] **Step 1: Test** `BackupKeysTest`: `daily(2026-11-14T01:00+05:30)` = `db/2026/11/vaabhi-2026-11-14T0100.dump`;
  `isMonthEnd(2027-01-31)` true, `isMonthEnd(2027-02-01)` false, `isMonthEnd(2028-02-29)` true;
  `monthly(2027-01-31)` = `monthly/vaabhi-2027-01.dump`; `attachment("bills/7/ab.pdf")` = `attachments/bills/7/ab.pdf`;
  `dailyRetainUntil(t)` = t + 35 days; `longRetainUntil(t)` = t + 8 years + 1 day (via `ZonedDateTime` in UTC).
- [ ] **Step 2: V38** exactly the spec's `backup_run` and `backup_file` (plus `CREATE UNIQUE INDEX
  backup_run_one_running ON backup_run (kind) WHERE status = 'RUNNING'`, index on `(kind, started_at DESC)`);
  attach the V32 audit trigger to both (same loop as V36/V37).
- [ ] **Step 3: Code** the three files and yml entries.
- [ ] **Step 4: Compile.** Expected: BUILD SUCCESS.
- [ ] **Step 5: Commit** `feat(backup): schema, settings and key rules`.

### Task 2: S3 store and R2 read

**Files:**
- Create: `backend/src/main/java/com/vaabhi/shared/storage/SigV4Signer.java` (public; header signing),
  `backend/src/main/java/com/vaabhi/backup/S3BackupStore.java`, `S3ObjectInfo.java`
- Modify: `shared/storage/ObjectStorage.java` (+ `InputStream open(String key)` default throws
  `UnsupportedOperationException`), `R2ObjectStorage.java` (signed GET; 404 → `NoSuchFileException`)
- Modify: `backend/pom.xml` (+ `org.testcontainers:minio`, test scope, version from the Testcontainers BOM)
- Test: `backend/src/test/java/com/vaabhi/backup/S3BackupStoreIT.java`

**Interfaces:**
- Produces: `public final class SigV4Signer { static Map<String,String> sign(String method, URI uri, Map<String,
  String> headers, String payloadSha256Hex, String region, String service, String accessKey, String secretKey,
  Instant now) }` → headers to add (`Authorization`, `x-amz-date`, `x-amz-content-sha256`). Reuse the encode/hmac
  helpers of `SigV4Presigner` (move them to package-private statics shared by both).
- Produces: `record S3ObjectInfo(long sizeBytes, @Nullable String sha256Base64)`.
- Produces: `@Component class S3BackupStore { void put(String key, Path file, String sha256Hex, Instant
  retainUntil); Optional<S3ObjectInfo> head(String key); void get(String key, Path into) }` — path-style URLs
  `<endpoint>/<bucket>/<key>`; put headers per Global Constraints plus `Content-Length`,
  `x-amz-checksum-sha256` (base64 of the hex digest's bytes), `x-amz-object-lock-mode`,
  `x-amz-object-lock-retain-until-date` (ISO-8601 `Z`); payload hash header = the file's hex SHA-256; non-2xx →
  `IllegalStateException("S3 <status>: <first 300 chars of body>")`. `head` sends `x-amz-checksum-mode: ENABLED`.

- [ ] **Step 1: IT** `S3BackupStoreIT` (MinIO container `minio/minio` started with a bucket created with object
  lock via the MinIO admin/`mc` or a `PUT ?object-lock` bucket request): put a 3 KB temp file → `head` returns size
  3072 and the same base64 checksum; `get` writes identical bytes; put with a wrong `sha256Hex` → throws.
- [ ] **Step 2: Implement.**
- [ ] **Step 3: Compile.** Expected: BUILD SUCCESS.
- [ ] **Step 4: Commit** `feat(backup): signed S3 store and R2 reads`.

### Task 3: Nightly database backup

**Files:**
- Create: `backup/DatabaseDumper.java`, `PgDumpDumper.java`, `PgVersion.java`, `BackupRunStore.java`,
  `BackupService.java`
- Test: `backend/src/test/java/com/vaabhi/backup/PgVersionTest.java`, `PgDumpDumperTest.java`,
  `BackupServiceIT.java`

**Interfaces:**
- Consumes: `BackupProperties`, `BackupKeys` (Task 1); `S3BackupStore` (Task 2).
- Produces: `interface DatabaseDumper { void dump(Path into) throws IOException; int clientMajor(); }`;
  `PgDumpDumper` (`@Component`, `@ConditionalOnMissingBean`-free; ITs override with `@Primary @TestConfiguration`
  stub) builds `[pgDumpPath, "--format=custom", "--compress=6", "--no-owner", "--no-privileges", "--file", into,
  "--dbname", <url without password>]`, env `PGPASSWORD`, 20-min timeout, keeps last 2 KB of stderr for the error;
  `List<String> command(Path into)` package-private for the test.
- Produces: `record PgVersion(int major) { static PgVersion ofClient(String pgDumpVersionLine); static PgVersion
  ofServerNum(int serverVersionNum) }` ("pg_dump (PostgreSQL) 17.2" → 17; 170002 → 17; 160004 → 16).
- Produces: `BackupRunStore` (JdbcClient over `backup_run`): `long start(String kind, @Nullable String actor)`
  (unique index violation → returns -1), `void ok(long id, long size, String sha, String key, @Nullable String
  monthlyKey, int attachments, @Nullable String notes)`, `void fail(long id, String message)`, `int
  expireStale(Duration olderThan)` (RUNNING → FAILED "Interrupted").
- Produces: `@Service public class BackupService { @Scheduled nightly(); @Scheduled retry(); public long
  runNow(@Nullable String actor) /* 409 "A backup is running" */ }`; server version via `SHOW server_version_num`
  on a plain `DriverManager` connection to `databaseUrl` (not the app DataSource). Steps exactly as the spec's
  "One run" 1–7 and 9 (attachments come in Task 4; pass 0). Temp file via `Files.createTempFile("vaabhi-",
  ".dump")`, deleted in `finally`. Not configured → return without a row.

- [ ] **Step 1: Tests.** `PgVersionTest` (the three conversions above, garbage → `IllegalArgumentException`).
  `PgDumpDumperTest`: `command()` contains no password and ends with `--dbname`, url. `BackupServiceIT`
  (Postgres + MinIO, stub dumper writing 10 KB): (a) `runNow` → one `OK` row with size 10240, sha, key starting
  `db/`, object present with matching checksum; (b) stub throws → `FAILED` row with the message, temp dir has no
  `vaabhi-*.dump` left (Review Focus 3); (c) a `RUNNING` row started 3 h ago → `runNow` marks it `FAILED`
  "Interrupted" and succeeds (Review Focus 2); (d) a `RUNNING` row started 5 min ago → `runNow` throws 409;
  (e) stub `clientMajor()` 15 → `FAILED` with "pg_dump 15 can't back up Postgres 1x; update the backend image".
- [ ] **Step 2: Implement.**
- [ ] **Step 3: Compile.** Expected: BUILD SUCCESS.
- [ ] **Step 4: Commit** `feat(backup): nightly database backup to S3`.

### Task 4: Attachments, alert, monthly check, drills, API

**Files:**
- Create: `backup/AttachmentCopier.java`, `BackupCheck.java`, `BackupStatusService.java`, `BackupStatus.java`,
  `BackupRunView.java`, `BackupDrillForm.java`, `BackupController.java`
- Modify: `BackupService.java` (call copier, alert after failed retry)
- Test: `backend/src/test/java/com/vaabhi/backup/BackupStatusTest.java`, extend `BackupServiceIT.java`

**Interfaces:**
- Consumes: `ObjectStorage.open` (Task 2), `BackupRunStore`, `S3BackupStore`, `WhatsAppSender`.
- Produces: `AttachmentCopier.copyNew() → AttachmentCopyResult(int copied, List<String> problems)` (own file), SQL
  as the spec; problems joined into the run's `notes` (max 2000 chars).
- Produces: `record BackupRunView(long id, String kind, String status, OffsetDateTime startedAt, @Nullable
  OffsetDateTime finishedAt, @Nullable Long sizeBytes, @Nullable String objectKey, @Nullable String message,
  @Nullable String notes, int attachmentsCopied, @Nullable LocalDate drilledOn, @Nullable String createdBy)`;
  `record BackupStatus(...)` exactly as the spec's API section; `record BackupDrillForm(@NotNull LocalDate
  drilledOn, @Nullable @Size(max = 500) String note)`.
- Produces: `@Service public class BackupStatusService { BackupStatus status(); BackupStatus status(Instant now) }`
  (the second for tests), `static boolean missedLastNight(Instant now, @Nullable Instant lastOk)`,
  `int missedDaysLast7` (days in the last 7 IST dates, excluding today before 02:00, with no `OK` DB run).
- Produces: `BackupCheck` `@Scheduled(cron = "0 0 2 1 * *", zone = "Asia/Kolkata") void monthly()`: newest `OK`
  DB run → `get` to temp, verify sha, run `pg_restore --list <file>`, require TABLE DATA lines for `journal_entry`,
  `journal_line`, `account`, `invoice`, `supplier_bill`, `party`; record `kind CHECK` OK/FAILED; temp deleted.
- Produces: `BackupController` at `/api/v1/admin/accounts/backups`: `GET /status`, `POST /run` (→ status after the
  run), `POST /drills` (inserts `kind DRILL, status OK, drilled_on, message = note, created_by`) → status.
- Alert: after `retry()` ends `FAILED`, if `alertPhone` set, `WhatsAppSender` with text
  `"Vaabhi backup failed on <d MMM>: <message>"`; any send exception is logged only.

- [ ] **Step 1: Tests.** `BackupStatusTest`: lastOk 2026-11-14T00:30 IST, now 01:30 IST same day → false; now 02:30
  IST → true; lastOk 2026-11-13T01:05 IST, now 2026-11-14T01:59 IST → false (Review Focus 4). `BackupServiceIT`
  additions: two bills with attachment keys in a stub `ObjectStorage` → first run copies 2, second run copies 0;
  a key the stub reports missing → run `OK` with that key in `notes`, and again noted next run (Review Focus 5);
  `POST /drills` via `MockMvc` with an owner JWT (copy `AccountsControllerIT`'s auth helper) → status `lastDrill`
  has the date.
- [ ] **Step 2: Implement.**
- [ ] **Step 3: Compile.** Expected: BUILD SUCCESS.
- [ ] **Step 4: Commit** `feat(backup): attachments, monthly check, drills and status API`.

### Task 5: Books integration

**Files:**
- Modify: `accounts/package-info.java` (+ `"backup"`), `accounts/YearEndService.java` (6th check),
  `accounts/AuditorPackService.java` (About rows)
- Test: modify `backend/src/test/java/com/vaabhi/accounts/YearEndServiceIT.java`,
  `AuditorPackServiceIT.java`

**Interfaces:**
- Consumes: `BackupStatusService.status()` (Task 4).
- Check `BACKUPS` "Backups running": ok when `configured && missedDaysLast7 == 0`; messages "Backups aren't set up"
  / "<n> nights in the last week have no backup". `close()` skips this key when applying the checklist.
- About rows: "Electronic books backup" = "Daily to <locationLabel>, bucket <bucket>" (or "Backups aren't set up"),
  "Last backup" (IST date-time or "None"), "Last restore drill" (date or "Not done").

- [ ] **Step 1: Tests.** YearEndServiceIT: checks list has 6 keys ending with `BACKUPS`; close succeeds while
  `BACKUPS` is failing (not configured in tests). AuditorPackServiceIT: About contains "Electronic books backup".
- [ ] **Step 2: Implement.**
- [ ] **Step 3: Compile.** Expected: BUILD SUCCESS (`ModularityTests` compile; not run).
- [ ] **Step 4: Commit** `feat(accounts): backups on the year-end checklist and auditor pack`.

### Task 6: Image, setup guide, restore runbook

**Files:**
- Modify: `backend/Dockerfile` (`RUN apk add --no-cache postgresql17-client` before `USER app`),
  `infra/vps-setup.md` (section "Backups (AWS S3 Mumbai)"), `infra/.env.example` (the `BACKUP_*` keys, empty)
- Create: `docs/RESTORE.md`

- [ ] **Step 1:** vps-setup section exactly per the spec's Operations paragraph (account + $2 billing alarm,
  bucket in `ap-south-1` with Object Lock, Block Public Access, SSE-S3, the three lifecycle rules, the IAM policy as
  a JSON block with only `s3:PutObject`, `s3:PutObjectRetention`, `s3:GetObject`, `s3:GetObjectAttributes`,
  `s3:ListBucket` on `arn:aws:s3:::<bucket>` and `/*`, the env keys, then "Back up now" in admin to test).
- [ ] **Step 2:** RESTORE.md: download yesterday's `db/...dump` (console or `aws s3 cp`), `docker run` a
  `postgres:17` container, `pg_restore --no-owner --no-privileges -d postgres <file>`, compare
  `SELECT … trial balance` (copy the query shape from `JournalService.trialBalance`) at a date with the admin
  Trial balance page, then "Mark restore drill done".
- [ ] **Step 3: Commit** `docs(backup): image client, setup guide and restore runbook`.

### Task 7: Regenerate the API client

- [ ] **Step 1:** Ask the owner to restart the backend (V38). Confirm `/v3/api-docs` lists
  `/api/v1/admin/accounts/backups/status`.
- [ ] **Step 2:** `pnpm generate:api`; add `api.admin.accounts.backups.{status(), runNow(), recordDrill(form)}` and
  type exports `BackupStatus`, `BackupRunView`, `BackupDrillForm` in `frontend/packages/api-client/src/index.ts`.
- [ ] **Step 3:** `pnpm --filter @vaabhi/api-client typecheck`. Expected: no errors.
- [ ] **Step 4: Commit** `chore(api-client): backups`.

### Task 8: Admin backups card and page

**Files:**
- Modify: `frontend/admin/src/app/money/accounts/page.tsx` (Backups card)
- Create: `frontend/admin/src/app/money/accounts/backups/page.tsx`

- [ ] **Step 1:** Card per the spec's Admin UI (red when `missedLastNight` or last run FAILED, amber when not
  configured, else ok line "Last backup <time> · <size> · <locationLabel>"; last check; last drill; link).
- [ ] **Step 2:** Page: recent runs table (kind, status, started, size, message/notes), owner-only "Back up now"
  (disabled while running, shows API error inline) and "Mark restore drill done" sheet (date default today, note).
- [ ] **Step 3:** typecheck + lint. Expected: 0 errors.
- [ ] **Step 4: Commit** `feat(admin): backups status and drills`.

### Task 9: Gaps and wrap-up

- [ ] **Step 1:** `docs/BOOKS_GAPS.md` "Phase 6: backups": the spec's Gaps list, the two wiring deviations, owner
  hand checks (AWS setup, first nightly run, restore drill, CA question on live DB location).
- [ ] **Step 2:** Whole-branch review of the Phase 6 diff; spotless + prettier on changed files; compile.
- [ ] **Step 3:** Commit `docs(backup): phase 6 gaps` (+ `style:` if needed); push.
