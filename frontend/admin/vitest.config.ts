import path from "node:path";
import { defineConfig } from "vitest/config";

export default defineConfig({
  resolve: {
    alias: { "@": path.resolve(import.meta.dirname, "src") },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    // The product editor tests fill in dozens of fields; on shared CI runners, with every package's tests
    // running in parallel, they take 6–8 s. Assertions still fail fast; only the overall budget is longer.
    testTimeout: 15_000,
    include: ["src/**/*.test.{ts,tsx}"],
    coverage: {
      provider: "v8",
      include: ["src/**/*.{ts,tsx}"],
      // Framework wiring (root layout, OTel registration) is exercised by the Playwright suite instead.
      exclude: [
        "src/test/**",
        "src/**/*.test.{ts,tsx}",
        "src/**/*.d.ts",
        "src/app/layout.tsx",
        "src/instrumentation.ts",
      ],
      reporter: ["text-summary", "lcov", "json-summary"],
      thresholds: { lines: 70 },
    },
  },
});
