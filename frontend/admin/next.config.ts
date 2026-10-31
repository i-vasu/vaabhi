import type { NextConfig } from "next";
import { buildInfoEnv } from "@vaabhi/config/build-info.mjs";

const nextConfig: NextConfig = {
  // Release version + commit, shown in the app and reported to Grafana Faro.
  env: buildInfoEnv(import.meta.dirname),
  // Static export deployed to Cloudflare Pages; the dashboard talks to the API from the browser.
  output: "export",
  trailingSlash: true,
  transpilePackages: ["@vaabhi/ui", "@vaabhi/api-client", "@vaabhi/observability"],
  images: {
    unoptimized: true,
  },
};

export default nextConfig;
