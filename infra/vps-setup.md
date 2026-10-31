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
       "AllowedMethods": ["PUT"],
       "AllowedHeaders": ["content-type", "cache-control"],
       "MaxAgeSeconds": 3600
     }
   ]
   ```

4. **R2 → Manage API tokens** → Object Read & Write, limited to `vaabhi-media`.
5. Add to `/opt/vaabhi/.env` and redeploy (or `docker compose ... up -d`):

   ```bash
   R2_ACCOUNT_ID=9866817007578d38cd27b8bf64773774
   R2_ACCESS_KEY_ID=...
   R2_SECRET_ACCESS_KEY=...
   ```

Without these the site works normally; only photo upload answers "Photo storage is not configured".

## Admin access (until the auth module adds a login)

The admin app's **Sign in** page takes an access token. Create one on your computer, from the repository root; it
reads the production `JWT_SECRET` over SSH and expires after 12 hours:

```bash
JWT_SECRET=$(ssh -p 20036 deploy@148.113.17.68 "grep '^JWT_SECRET=' /opt/vaabhi/.env | cut -d= -f2-") bash infra/admin-token.sh vasu 12
```

Paste the printed token into admin.vaabhi.com → Sign in. It is kept only for that browser tab's session. Anyone with
the token can edit the catalogue until it expires, so do not share it; rotating `JWT_SECRET` (and redeploying)
invalidates every token.
