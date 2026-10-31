import path from "node:path";
import type { NextConfig } from "next";
import { buildInfoEnv } from "@vaabhi/config/build-info.mjs";

const nextConfig: NextConfig = {
  // Release version + commit, shown in the app and reported to Grafana Faro.
  env: buildInfoEnv(import.meta.dirname),
  // Self-contained server bundle for the Docker image on the VPS.
  output: "standalone",
  outputFileTracingRoot: path.join(import.meta.dirname, ".."),
  transpilePackages: ["@vaabhi/ui", "@vaabhi/api-client", "@vaabhi/observability"],
  images: {
    // Cloudflare Images does resizing at the edge; nothing is optimised on the VPS.
    loader: "custom",
    loaderFile: "./src/lib/cloudflare-image-loader.ts",
  },
};

export default nextConfig;
