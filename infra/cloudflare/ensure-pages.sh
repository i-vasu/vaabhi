#!/usr/bin/env bash
# Idempotently creates the Cloudflare Pages project for the admin dashboard and attaches its
# custom domain (plus the DNS record when the zone is on the same account).
#
# Required env: CLOUDFLARE_API_TOKEN, CLOUDFLARE_ACCOUNT_ID
# Optional env: PAGES_PROJECT (default vaabhi-admin), ADMIN_DOMAIN (default admin.vaabhi.com)
#
# Token permissions: Account > Cloudflare Pages > Edit, Zone > Zone > Read, Zone > DNS > Edit.
set -euo pipefail

: "${CLOUDFLARE_API_TOKEN:?}" "${CLOUDFLARE_ACCOUNT_ID:?}"
PROJECT="${PAGES_PROJECT:-vaabhi-admin}"
DOMAIN="${ADMIN_DOMAIN:-admin.vaabhi.com}"
API="https://api.cloudflare.com/client/v4"

cf() {
  local method=$1 path=$2 body=${3:-}
  curl -sS -X "$method" "$API$path" \
    -H "Authorization: Bearer $CLOUDFLARE_API_TOKEN" \
    -H "Content-Type: application/json" \
    ${body:+--data "$body"}
}

ok() { jq -e '.success == true' >/dev/null; }

# 1. Pages project (direct upload, production branch main)
if cf GET "/accounts/$CLOUDFLARE_ACCOUNT_ID/pages/projects/$PROJECT" | ok; then
  echo "Pages project $PROJECT exists"
else
  echo "Creating Pages project $PROJECT"
  cf POST "/accounts/$CLOUDFLARE_ACCOUNT_ID/pages/projects" \
    "{\"name\":\"$PROJECT\",\"production_branch\":\"main\"}" | tee /dev/stderr | ok
fi

# 2. Custom domain on the project
if cf GET "/accounts/$CLOUDFLARE_ACCOUNT_ID/pages/projects/$PROJECT/domains/$DOMAIN" | ok; then
  echo "Custom domain $DOMAIN already attached"
else
  echo "Attaching custom domain $DOMAIN"
  cf POST "/accounts/$CLOUDFLARE_ACCOUNT_ID/pages/projects/$PROJECT/domains" "{\"name\":\"$DOMAIN\"}" \
    | tee /dev/stderr | ok
fi

# 3. DNS: CNAME admin -> <project>.pages.dev (proxied), if the zone is on this account
ZONE_NAME="${DOMAIN#*.}"
ZONE_ID=$(cf GET "/zones?name=$ZONE_NAME" | jq -r '.result[0].id // empty')
if [ -z "$ZONE_ID" ]; then
  echo "::warning::Zone $ZONE_NAME not found on this account; add a CNAME $DOMAIN -> $PROJECT.pages.dev yourself."
  exit 0
fi
if [ "$(cf GET "/zones/$ZONE_ID/dns_records?name=$DOMAIN" | jq '.result | length')" -gt 0 ]; then
  echo "DNS record for $DOMAIN exists"
else
  echo "Creating CNAME $DOMAIN -> $PROJECT.pages.dev"
  cf POST "/zones/$ZONE_ID/dns_records" \
    "{\"type\":\"CNAME\",\"name\":\"$DOMAIN\",\"content\":\"$PROJECT.pages.dev\",\"proxied\":true}" \
    | tee /dev/stderr | ok
fi
