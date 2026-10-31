import { readFileSync } from "node:fs";
import path from "node:path";
import type { NextConfig } from "next";

// Release version from package.json (bumped by release-please) and the commit being built.
const { version } = JSON.parse(readFileSync(path.join(import.meta.dirname, "package.json"), "utf8")) as {
  version: string;
};
const commit = (process.env.COMMIT_SHA || process.env.GITHUB_SHA || "dev").slice(0, 7);

const nextConfig: NextConfig = {
  env: {
    NEXT_PUBLIC_APP_VERSION: version,
    NEXT_PUBLIC_COMMIT_SHA: commit,
  },
  // Static export deployed to Cloudflare Pages; the dashboard talks to the API from the browser.
  output: "export",
  trailingSlash: true,
  transpilePackages: ["@vaabhi/ui", "@vaabhi/api-client"],
  images: {
    unoptimized: true,
  },
};

export default nextConfig;
