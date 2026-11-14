# VPS setup (AIC Cloud, 1 GB RAM)

One-time setup, as root:

```bash
# 2 GB swap as a safety net
fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab
sysctl vm.swappiness=10 && echo 'vm.swappiness=10' >> /etc/sysctl.conf

# Docker Engine + compose plugin
curl -fsSL https://get.docker.com | sh

# Deploy user used by GitHub Actions
useradd -m -s /bin/bash -G docker deploy
mkdir -p /opt/vaabhi && chown deploy:deploy /opt/vaabhi
# add the public half of VPS_SSH_KEY to /home/deploy/.ssh/authorized_keys
```

Then, as `deploy`:

```bash
cp .env.example /opt/vaabhi/.env && chmod 600 /opt/vaabhi/.env   # fill in values
echo "<github-token-with-read:packages>" | docker login ghcr.io -u <github-user> --password-stdin
```

Memory budget: OS + Docker ~210 MB, cloudflared ~30 MB, backend ~320 MB, storefront ~150 MB.

## Product photos (Cloudflare R2)

Photos never pass through the VPS: the admin resizes them in the browser (400/800/1600 px WebP) and uploads them
straight to R2 with short-lived signed URLs from the API; the storefront loads them from `media.vaabhi.com`.

1. Cloudflare dashboard → **R2** → create bucket `vaabhi-media` (location: Automatic).
2. Bucket → **Settings → Custom Domains** → connect `media.vaabhi.com`.
3. Bucket → **Settings → CORS policy**:

   ```json
   [
     {
       "AllowedOrigins": ["https://admin.vaabhi.com", "http://localhost:3001"],
       "AllowedMethods": ["PUT", "GET"],
       "AllowedHeaders": ["content-type", "cache-control"],
       "MaxAgeSeconds": 3600
     }
   ]
   ```

   `PUT` is the admin uploading photos; `GET` lets the admin fetch design and fabric photos as files to share them
   on WhatsApp from a phone (without it, sharing falls back to photo links).

4. **R2 → Manage API tokens** → Object Read & Write, limited to `vaabhi-media`.
5. Add the three values as **GitHub secrets** (repo → Settings → Environments → `production` → Add secret):
   `R2_ACCOUNT_ID`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`. Every deploy writes them into `/opt/vaabhi/.env`
   (only those lines; nothing else changes). Re-run **Deploy to VPS** or merge anything to apply them. Rotating a
   key is the same: update the secret, redeploy.

   Or, with SSH access, put them in `/opt/vaabhi/.env` yourself and run `docker compose ... up -d`:

   ```bash
   R2_ACCOUNT_ID=9866817007578d38cd27b8bf64773774
   R2_ACCESS_KEY_ID=...
   R2_SECRET_ACCESS_KEY=...
   ```

Without these the site works normally; only photo upload answers "Photo storage is not configured".

## Books backups (AceCloud object storage, India)

The Companies (Accounts) Rules require a daily backup of the electronic books on servers in India. Cloudflare R2 has
no India location, so the nightly dump goes to a bucket in AceCloud's object storage in India. AceCloud bills in
rupees, flat, so no exchange rate or usage surprise. The backend runs the job at 01:00 IST; until it is configured
nothing runs and the admin card says "Backups aren't set up".

1. Create an **AceCloud** account.
2. **Object storage → Create bucket**: pick an India region (**Noida** or **Mumbai** — use the one your console
   shows), name it (e.g. `vaabhi-books-backup`), and keep it private. Note the **S3 endpoint URL** and the **region
   name** the console shows; both go in the env below.
3. Turn on **versioning** if AceCloud offers it.
4. If lifecycle rules are offered, add one for the `db/` prefix: delete objects after 36 days. Leave `monthly/` and
   `attachments/` alone; month-end copies are kept 8 years.
5. Create an **access key** for the backup. If AceCloud's access policies allow it, restrict the key to this bucket
   and leave out the delete permission.
6. Add these lines to `/opt/vaabhi/.env`:

   ```bash
   BACKUP_ENABLED=true
   BACKUP_DATABASE_URL=jdbc:postgresql://<endpoint>.c-3.ap-southeast-1.aws.neon.tech/neondb?sslmode=require
   BACKUP_DATABASE_USER=<neon user>
   BACKUP_DATABASE_PASSWORD=<neon password>
   BACKUP_S3_ENDPOINT=<S3 endpoint URL from the console>
   BACKUP_S3_REGION=<region name from the console>
   BACKUP_S3_BUCKET=<bucket>
   BACKUP_S3_ACCESS_KEY_ID=<access key>
   BACKUP_S3_SECRET_ACCESS_KEY=<secret key>
   BACKUP_LOCK_MODE=NONE
   BACKUP_CHECKSUM=MD5
   ```

   `BACKUP_DATABASE_URL` is Neon's direct, non-pooled endpoint (the host without `-pooler`). Optional:
   `BACKUP_ALERT_PHONE` (E.164) for a WhatsApp alert when a run fails.
7. Redeploy.
8. Admin → **Accounts → Backups** → **Back up now**. The run shows **OK**, and the object appears under `db/` in the
   AceCloud console.

AceCloud is S3-compatible, but not every S3 feature is guaranteed. `BACKUP_LOCK_MODE=NONE` sends no Object Lock
headers (and no server-side encryption header), and `BACKUP_CHECKSUM=MD5` sends a `Content-MD5` header, which every
S3-compatible store accepts. Both are the safe defaults for AceCloud.

### Ask AceCloud support (in writing)

1. Is the bucket stored only in India, with no replica outside India?
2. Is S3 Object Lock available?
3. Do lifecycle expiry rules work?
4. Can an access key be denied delete permission?

Keep the replies with the backup records; the auditor may ask. If (2) is yes, recreate the bucket with Object Lock on
and set `BACKUP_LOCK_MODE=GOVERNANCE` (or `COMPLIANCE` if AceCloud allows it), so nobody can delete a backup before
its retention date. If it is no, the no-delete key is the only protection, and dailies pile up unless lifecycle rules
work (about 18 GB a year at today's size).

### Switching to AWS S3 Mumbai later

Create a bucket in `ap-south-1` with **Object Lock** on, then change these lines in `/opt/vaabhi/.env`:

```bash
BACKUP_S3_ENDPOINT=https://s3.ap-south-1.amazonaws.com
BACKUP_S3_REGION=ap-south-1
BACKUP_LOCK_MODE=COMPLIANCE
BACKUP_CHECKSUM=SHA256
```

Redeploy.

## Admin access

The admin dashboard has an email + password sign-in for shop staff. The first **owner** account is created by the
backend on its first start, while no staff account exists, from these lines in `/opt/vaabhi/.env`:

```bash
STAFF_OWNER_NAME="Your name"
STAFF_OWNER_EMAIL=you@example.com
STAFF_OWNER_PASSWORD=<a long random password>
```

Redeploy (or restart the backend), then sign in at admin.vaabhi.com. Once an account exists the variables are ignored,
so changing them never resets a password; remove `STAFF_OWNER_PASSWORD` from the file after the first sign-in.
Five wrong passwords in a row lock the account for 15 minutes. A sign-in lasts 12 hours (`STAFF_TOKEN_TTL`).

For scripts that call the admin API directly (curl), `infra/admin-token.sh` still mints a short-lived token from the
production `JWT_SECRET`; it is not a dashboard sign-in:

```bash
JWT_SECRET=$(ssh -p 20036 deploy@148.113.17.68 "grep '^JWT_SECRET=' /opt/vaabhi/.env | cut -d= -f2-") bash infra/admin-token.sh vasu 12
```

Rotating `JWT_SECRET` (and redeploying) signs everyone out.
