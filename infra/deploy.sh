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
