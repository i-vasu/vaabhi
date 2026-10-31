import { readFileSync } from "node:fs";
import path from "node:path";

/**
 * Build-time env for a Next.js app: the release version from its package.json (bumped by
 * release-please) and the short commit SHA being built (COMMIT_SHA in Docker, GITHUB_SHA in Actions).
 *
 * @param {string} appDir directory containing the app's package.json
 * @returns {{ NEXT_PUBLIC_APP_VERSION: string, NEXT_PUBLIC_COMMIT_SHA: string }}
 */
export function buildInfoEnv(appDir) {
  const { version } = JSON.parse(readFileSync(path.join(appDir, "package.json"), "utf8"));
  const commit = (process.env.COMMIT_SHA || process.env.GITHUB_SHA || "dev").slice(0, 7);
  return { NEXT_PUBLIC_APP_VERSION: version, NEXT_PUBLIC_COMMIT_SHA: commit };
}
