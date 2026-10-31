/** Release version and commit, inlined at build time by next.config.ts. */
export function appVersion() {
  const version = process.env.NEXT_PUBLIC_APP_VERSION || "dev";
  const commit = process.env.NEXT_PUBLIC_COMMIT_SHA || "dev";
  return { version, commit, label: `v${version} (${commit})` };
}
