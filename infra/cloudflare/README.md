# Cloudflare setup

## Tunnel (VPS ingress)

1. Zero Trust → Networks → Tunnels → **Create tunnel** (cloudflared). Copy the token into
   `CF_TUNNEL_TOKEN` in `/opt/vaabhi/.env` on the VPS.
2. Public hostnames:

   | Hostname | Service |
   |---|---|
   | `vaabhi.com` (and `www`) | `http://storefront:3000` |
   | `api.vaabhi.com` | `http://backend:8080` |

3. Close all inbound ports on the VPS except SSH (ideally SSH only from known IPs).

## Pages (admin dashboard)

- Project name: `vaabhi-admin`, custom domain `admin.vaabhi.com`.
- Deployed by `.github/workflows/deploy-admin.yml` with `wrangler pages deploy`.
- Protect it with Cloudflare Access (free for up to 50 users) in addition to app login.

## Images and Stream (media)

- Images: note the **account hash** (Images → Overview) → `NEXT_PUBLIC_CF_IMAGES_ACCOUNT_HASH`.
  Enable **flexible variants** so the storefront can request any width.
- Stream: used for product videos (wired up in the media module, phase 2).
- API token with `Cloudflare Images:Edit` and `Stream:Edit` → backend `CF_API_TOKEN` (phase 2).

## Required GitHub secrets for Pages

- `CLOUDFLARE_API_TOKEN` — permission `Cloudflare Pages:Edit`
- `CLOUDFLARE_ACCOUNT_ID`
