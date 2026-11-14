# Books Phase 6: daily backups in India — design

Status: approved in conversation 2026-11-14, awaiting written-spec review.

## Goal

The books of account live in Vaabhi's Postgres (Neon, AWS `ap-southeast-1`, Singapore). Phase 6 makes a backup of
them every night on servers physically in India, keeps them for 8 years, proves a restore works, and puts the backup
location on record for the auditor.

Done when: a restore of yesterday's backup works, and the auditor pack names where the backups live.

## Decisions

| Topic | Decision | Why |
| --- | --- | --- |
| Live database | Stays on Neon (Singapore) | Keeps point-in-time recovery; no migration. Ask the CA whether a Singapore live DB with daily India backups is enough. |
| India copy | AWS S3, `ap-south-1` (Mumbai), versioning + Object Lock | Guaranteed region and undeletable copies. Cloudflare R2 has no India jurisdiction (only EU, US, FedRAMP). AIC Cloud's object storage is unclear ("launching soon" on one page) and its terms make backups best effort with data loss excluded. |
| Second copy | None (R2 copy skipped by the owner) | |
| Where the job runs | Inside the backend, a scheduled task at 01:00 IST | Owner's choice. Status lives next to the books. |
| Encryption | S3 server-side (SSE-S3) only, no client-side keys | A lost client key would lose 8 years of books; bucket is private, key has no delete. |
| Retention | Daily backups locked 35 days; the month's last backup also stored under `monthly/`, locked 8 years | Each backup holds all history, so month-ends suffice for the long tail. ~₹5/month at today's size. |
| Provider lock-in | Plain S3 API via SigV4 over `HttpClient`; endpoint, region, bucket, keys from env | Switch to AIC or another India S3 store with config only. |

## Units

New Modulith module `backup` (`com.vaabhi.backup`). Allowed dependencies: `settings`, `shared :: storage`,
`shared :: whatsapp`. `accounts` gains `backup` as an allowed dependency (year-end check, auditor pack).

### `BackupProperties` (`vaabhi.backup.*`)
`enabled` (default false; true in `application-prod.yml`), `databaseUrl` (JDBC URL of Neon's direct, non-pooled
endpoint; env `BACKUP_DATABASE_URL`, falls back to `DATABASE_URL_UNPOOLED`), `databaseUser`, `databasePassword`,
`s3Endpoint` (default `https://s3.ap-south-1.amazonaws.com`), `s3Region` (default `ap-south-1`), `s3Bucket`,
`s3AccessKeyId`, `s3SecretAccessKey`, `locationLabel` (default `AWS S3, Mumbai (ap-south-1)`), `pgDumpPath`
(default `pg_dump`), `alertPhone` (optional E.164). `configured()` = enabled and bucket + keys + database URL set.

### `S3BackupStore`
Header-signed SigV4 requests over `java.net.http.HttpClient` (extend `shared.storage.SigV4Presigner` with a
header-signing method, or a sibling `SigV4Signer` in `shared/storage`; no AWS SDK).
- `put(String key, Path file, String sha256Base64, Instant retainUntil)`: single PUT with `Content-Length`,
  `x-amz-checksum-sha256`, `x-amz-object-lock-mode: COMPLIANCE`, `x-amz-object-lock-retain-until-date`,
  `x-amz-server-side-encryption: AES256`. S3 rejects a body whose checksum differs.
- `head(String key)` → size and `x-amz-checksum-sha256` (request with `x-amz-checksum-mode: ENABLED`).
- `get(String key, Path into)` for the monthly check.
- `list(String prefix)` not needed.
Single PUT limit is 5 GB; the dump is ~50 MB today. Multipart is a gap.

### `ObjectStorage` (R2) addition
`InputStream open(String key)` (default throws) implemented in `R2ObjectStorage` with a signed GET, so the backup
job can read bill attachments.

### `BackupService`
- `runNightly()` — `@Scheduled(cron = "0 0 1 * * *", zone = "Asia/Kolkata")`; `retry()` at `0 30 1 * * *` runs only
  when today's 01:00 run is `FAILED` or missing.
- One run:
  1. Skip if not `configured()` (no row) or if a `RUNNING` row exists started < 2 h ago (overlap). A `RUNNING`
     row older than 2 h is marked `FAILED` ("Interrupted").
  2. Insert `backup_run (kind DB, status RUNNING)`.
  3. Version check: `SHOW server_version_num` on Neon vs `pg_dump --version`; client major < server major →
     fail "pg_dump 16 can't back up Postgres 17; update the backend image".
  4. `pg_dump --format=custom --compress=6 --no-owner --no-privileges --file <tmp> <url>` via `ProcessBuilder`;
     password in `PGPASSWORD` env, never on the command line; stderr captured (last 2 KB kept for the error);
     timeout 20 min. Temp file in `java.io.tmpdir`, deleted in `finally`.
  5. SHA-256 of the file. Key `db/yyyy/MM/vaabhi-yyyy-MM-ddTHHmm.dump` (IST), retain-until now + 35 days.
  6. If today is the last day of the month (IST), also put the same file at `monthly/vaabhi-yyyy-MM.dump`,
     retain-until now + 8 years + 1 day.
  7. `head` each key; size and checksum must match, else `FAILED` "Upload check failed".
  8. Copy new bill attachments (below).
  9. Mark `OK` with size, checksum, keys, finished_at.
- On any exception: `FAILED` with a one-line message. After the 01:30 retry also fails: WhatsApp alert to
  `alertPhone` through `WhatsAppSender` ("Vaabhi backup failed on 14 Nov: <message>"). With no approved template or
  phone, it only logs.
- `checkMonthly()` — `0 0 2 1 * *` IST: download the newest `OK` daily dump, verify its SHA-256, run
  `pg_restore --list` and require entries for tables `journal_entry`, `journal_line`, `account`, `invoice`,
  `supplier_bill`, `party`. Records `backup_run (kind CHECK)`.
- `status()` → `BackupStatus` for the admin and `accounts`.
- `recordDrill(LocalDate drilledOn, String note, actor)` — owner marks a manual restore drill.

### Attachment copy
After the dump: `SELECT id, attachment_key FROM supplier_bill WHERE attachment_key IS NOT NULL AND attachment_key
NOT IN (SELECT source_key FROM backup_file)` (read-only SQL on the purchases table; noted as a cross-module read).
For each: open from R2, write to temp, hash, put at `attachments/<source_key>` locked 8 years, insert
`backup_file`. A per-file failure is added to the run's `notes` and retried next night; it never fails the run.

### Data (migration `V38__backups.sql`)
```
backup_run (id BIGINT identity PK, kind VARCHAR(10) CHECK (kind IN ('DB','CHECK','DRILL')),
            status VARCHAR(10) CHECK (status IN ('RUNNING','OK','FAILED')),
            started_at TIMESTAMPTZ NOT NULL DEFAULT now(), finished_at TIMESTAMPTZ,
            size_bytes BIGINT, sha256 VARCHAR(64), object_key VARCHAR(200), monthly_key VARCHAR(200),
            attachments_copied INT NOT NULL DEFAULT 0, message VARCHAR(500), notes VARCHAR(2000),
            drilled_on DATE, created_by VARCHAR(120))
CREATE UNIQUE INDEX backup_run_one_running ON backup_run (kind) WHERE status = 'RUNNING';
backup_file (source_key VARCHAR(200) PK, backup_key VARCHAR(220) NOT NULL, size_bytes BIGINT NOT NULL,
             sha256 VARCHAR(64) NOT NULL, copied_at TIMESTAMPTZ NOT NULL DEFAULT now())
```
Audit trigger from V32 attached to both tables.

## API (`/api/v1/admin/backups`)
- `GET /status` (MONEY) → `BackupStatus(boolean configured, String locationLabel, String bucket,
  @Nullable BackupRunView lastBackup, @Nullable BackupRunView lastCheck, @Nullable BackupRunView lastDrill,
  boolean missedLastNight, int missedDaysLast7, List<BackupRunView> recent /*35*/)`
- `POST /drills` (OWNER) `BackupDrillForm(@NotNull LocalDate drilledOn, @Size(max=500) String note)` → status.
- `POST /run` (OWNER) → runs a backup now (for the first setup and after fixing a failure); 409 while one runs.
- `missedLastNight` = no `OK` DB run since 00:00 IST today when now ≥ 02:00 IST, else since yesterday 00:00.

## Admin UI
- Books hub (`money/accounts/page.tsx`): a "Backups" card — last backup time, size, location label; last check;
  last drill; red banner "Last night's backup didn't run" / "failed: <message>"; amber "Backups aren't set up" when
  not configured; link to `/money/accounts/backups/`.
- `money/accounts/backups/page.tsx`: the 35 recent runs (kind, status, time, size, message), "Back up now" and
  "Mark restore drill done" (date + note sheet), both owner-only.

## Books integration
- Year-end checklist item `BACKUPS` "Backups running": ok when configured and `missedDaysLast7 == 0`. Warning only:
  `close()` ignores it (it does not change the books).
- Auditor pack `About` sheet: row "Electronic books backup" = "Daily to <locationLabel>, bucket <bucket>"; "Last
  backup" date-time; "Last restore drill" date or "Not done".

## Operations (docs, done by the owner)
`infra/vps-setup.md` gains "Backups (AWS S3 Mumbai)": create an AWS account with a billing alarm ($2); create bucket
in `ap-south-1` with Object Lock enabled (implies versioning), Block Public Access on, default encryption SSE-S3;
lifecycle rules: `db/` current versions expire after 36 days, noncurrent versions after 1 day; `monthly/` transition
to Glacier Instant Retrieval after 35 days, expire after 8 years + 30 days; `attachments/` transition to Glacier IR
after 35 days. IAM user with only `s3:PutObject`, `s3:PutObjectRetention`, `s3:GetObject`,
`s3:GetObjectAttributes`, `s3:ListBucket` on that bucket. Put `BACKUP_S3_BUCKET`, `BACKUP_S3_ACCESS_KEY_ID`,
`BACKUP_S3_SECRET_ACCESS_KEY`, `BACKUP_DATABASE_URL` (+ user/password) in `/opt/vaabhi/.env`; compose passes them
to the backend.

`docs/RESTORE.md`: restore yesterday's dump into a local Docker Postgres (`pg_restore --no-owner -d …`), compare the
trial balance at a date with the live admin, then "Mark restore drill done".

Backend image: `RUN apk add --no-cache postgresql17-client` (as root before `USER app`), giving `pg_dump` and
`pg_restore` 17 (works against Postgres ≤ 17).

## Errors
- Not configured: no run, card says "Backups aren't set up" (amber), app starts normally.
- Version mismatch, dump error, timeout, upload refused, head mismatch: `FAILED` with message; retry 01:30; alert.
- Overlap: skipped; manual "Back up now" while running → 409 "A backup is running".
- Attachment failure: noted, retried next night.

## Testing (written, not run)
- Unit: key naming and month-end detection (IST), retain-until dates, version comparison, `missedLastNight`.
- `BackupServiceIT` (Testcontainers Postgres + MinIO with object lock): run → `OK` row, object present with
  matching checksum; bucket missing → `FAILED`; attachments copied once across two runs; overlap skip.
- Owner hand checks (in `docs/BOOKS_GAPS.md`): first real nightly run; restore drill per `docs/RESTORE.md`.

## Gaps
- Single PUT (5 GB limit); no multipart.
- Live DB stays in Singapore; CA to confirm.
- WhatsApp alert needs an approved MSG91 template; until then failures show only in admin and logs.
- Attachment copy reads `supplier_bill` directly (cross-module SQL).
- No automated full restore; the monthly check proves the file is complete and readable, not that every row
  restores.
