import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Static export deployed to Cloudflare Pages; the dashboard talks to the API from the browser.
  output: "export",
  trailingSlash: true,
  transpilePackages: ["@vaabhi/ui", "@vaabhi/api-client"],
  images: {
    unoptimized: true,
  },
};

export default nextConfig;
