#!/usr/bin/env bash
# Issues a short-lived admin access token (HS256 JWT) for the admin app's "Sign in" page.
# Stopgap until the auth module adds email/password login.
#
#   JWT_SECRET=... infra/admin-token.sh [name] [hours]
#
# From the repository root on your computer, reading the production secret over SSH:
#   JWT_SECRET=$(ssh -p 20036 deploy@148.113.17.68 "grep '^JWT_SECRET=' /opt/vaabhi/.env | cut -d= -f2-") \
#     bash infra/admin-token.sh vasu 12
# Paste the printed token into admin.vaabhi.com → Sign in. It expires after [hours] (default 12).
set -euo pipefail

: "${JWT_SECRET:?Set JWT_SECRET to the backend secret}"
name="${1:-admin}"
hours="${2:-12}"
now=$(date +%s)
exp=$((now + hours * 3600))

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }

header=$(printf '{"alg":"HS256","typ":"JWT"}' | b64url)
payload=$(printf '{"sub":"%s","roles":["ADMIN"],"iat":%d,"exp":%d}' "$name" "$now" "$exp" | b64url)
signature=$(printf '%s.%s' "$header" "$payload" | openssl dgst -sha256 -hmac "$JWT_SECRET" -binary | b64url)

printf '%s.%s.%s\n' "$header" "$payload" "$signature"
