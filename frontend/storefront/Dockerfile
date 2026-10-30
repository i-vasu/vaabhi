# Build context: frontend/   (docker build -f storefront/Dockerfile frontend)
FROM node:22-alpine AS build
RUN corepack enable
WORKDIR /repo
COPY . .
ARG NEXT_PUBLIC_API_URL
ARG NEXT_PUBLIC_CF_IMAGES_ACCOUNT_HASH
ARG NEXT_PUBLIC_FARO_URL
ARG NEXT_PUBLIC_APP_VERSION
ARG NEXT_PUBLIC_DEPLOY_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
RUN pnpm install --frozen-lockfile && pnpm --filter storefront build

FROM node:22-alpine
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1 PORT=3000 HOSTNAME=0.0.0.0
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build --chown=app:app /repo/storefront/.next/standalone ./
COPY --from=build --chown=app:app /repo/storefront/.next/static ./storefront/.next/static
USER app
EXPOSE 3000
# Heap capped for the 1 GB VPS.
CMD ["node", "--max-old-space-size=128", "storefront/server.js"]
