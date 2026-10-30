#!/usr/bin/env sh
# Deploys IMAGE_TAG on the VPS; rolls back to the previous tag if the backend is not healthy.
set -eu

cd "$(dirname "$0")"
: "${IMAGE_TAG:?IMAGE_TAG is required}"
COMPOSE="docker compose -f docker-compose.prod.yml --env-file .env"
PREVIOUS_TAG=$(cat .current-tag 2>/dev/null || echo "")

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
