#!/usr/bin/env sh
# Deploys IMAGE_TAG on the VPS; rolls back to the previous tag if the backend is not healthy.
set -eu

cd "$(dirname "$0")"
: "${IMAGE_TAG:?IMAGE_TAG is required}"
COMPOSE="docker compose -f docker-compose.prod.yml --env-file .env"
PREVIOUS_TAG=$(cat .current-tag 2>/dev/null || echo "")

# Settings kept as GitHub secrets (passed in by the deploy workflow) are written into .env, replacing any
# existing line for the key. Unset or empty values leave .env untouched, so values set by hand still work.
sync_env() {
  key="$1"
  value="$2"
  [ -n "$value" ] || return 0
  tmp=$(mktemp)
  grep -v "^${key}=" .env > "$tmp" 2>/dev/null || true
  printf '%s=%s\n' "$key" "$value" >> "$tmp"
  cat "$tmp" > .env # rewrite in place: keeps the file's owner and 600 permissions
  rm -f "$tmp"
  echo "Updated $key in .env"
}
sync_env R2_ACCOUNT_ID "${R2_ACCOUNT_ID:-}"
sync_env R2_ACCESS_KEY_ID "${R2_ACCESS_KEY_ID:-}"
sync_env R2_SECRET_ACCESS_KEY "${R2_SECRET_ACCESS_KEY:-}"
sync_env GOOGLE_CLIENT_ID "${GOOGLE_CLIENT_ID:-}"
sync_env MSG91_AUTH_KEY "${MSG91_AUTH_KEY:-}"
sync_env RAZORPAY_KEY_ID "${RAZORPAY_KEY_ID:-}"
sync_env RAZORPAY_KEY_SECRET "${RAZORPAY_KEY_SECRET:-}"
sync_env RAZORPAY_WEBHOOK_SECRET "${RAZORPAY_WEBHOOK_SECRET:-}"
sync_env SHIPROCKET_EMAIL "${SHIPROCKET_EMAIL:-}"
sync_env SHIPROCKET_PASSWORD "${SHIPROCKET_PASSWORD:-}"
sync_env SHIPROCKET_PICKUP_LOCATION "${SHIPROCKET_PICKUP_LOCATION:-}"
sync_env SHIPROCKET_PICKUP_PINCODE "${SHIPROCKET_PICKUP_PINCODE:-}"
sync_env SHIPROCKET_WEBHOOK_TOKEN "${SHIPROCKET_WEBHOOK_TOKEN:-}"
sync_env INSTAGRAM_ACCESS_TOKEN "${INSTAGRAM_ACCESS_TOKEN:-}"
sync_env INSTAGRAM_ACCOUNT_ID "${INSTAGRAM_ACCOUNT_ID:-}"
sync_env MAIL_HOST "${MAIL_HOST:-}"
sync_env MAIL_PORT "${MAIL_PORT:-}"
sync_env MAIL_USERNAME "${MAIL_USERNAME:-}"
sync_env MAIL_PASSWORD "${MAIL_PASSWORD:-}"
sync_env MAIL_FROM "${MAIL_FROM:-}"
sync_env MAIL_REPLY_TO "${MAIL_REPLY_TO:-}"
sync_env WHATSAPP_NUMBER "${WHATSAPP_NUMBER:-}"
sync_env WHATSAPP_NAMESPACE "${WHATSAPP_NAMESPACE:-}"
sync_env WHATSAPP_LOGIN_TEMPLATE "${WHATSAPP_LOGIN_TEMPLATE:-}"
sync_env WHATSAPP_ORDER_PLACED_TEMPLATE "${WHATSAPP_ORDER_PLACED_TEMPLATE:-}"
sync_env WHATSAPP_ORDER_SHIPPED_TEMPLATE "${WHATSAPP_ORDER_SHIPPED_TEMPLATE:-}"
sync_env WHATSAPP_ORDER_DELIVERED_TEMPLATE "${WHATSAPP_ORDER_DELIVERED_TEMPLATE:-}"
sync_env WHATSAPP_ORDER_CANCELLED_TEMPLATE "${WHATSAPP_ORDER_CANCELLED_TEMPLATE:-}"

deploy() {
  IMAGE_TAG="$1" $COMPOSE pull backend storefront
  IMAGE_TAG="$1" $COMPOSE up -d --remove-orphans
}

healthy() {
  for _ in $(seq 1 30); do
    if curl -fsS http://127.0.0.1:8080/actuator/health/readiness >/dev/null 2>&1 \
      && curl -fsS http://127.0.0.1:3000/api/health >/dev/null 2>&1; then
      return 0
    fi
    sleep 5
  done
  return 1
}

echo "Deploying $IMAGE_TAG (previous: ${PREVIOUS_TAG:-none})"
deploy "$IMAGE_TAG"

if healthy; then
  echo "$IMAGE_TAG" > .current-tag
  docker image prune -f >/dev/null
  echo "Deploy OK"
else
  echo "Health check failed" >&2
  if [ -n "$PREVIOUS_TAG" ]; then
    echo "Rolling back to $PREVIOUS_TAG" >&2
    deploy "$PREVIOUS_TAG"
  fi
  exit 1
fi
