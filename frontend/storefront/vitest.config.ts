import path from "node:path";
import { defineConfig } from "vitest/config";

export default defineConfig({
  resolve: {
    alias: { "@": path.resolve(import.meta.dirname, "src") },
  },
  test: {
    environment: "jsdom",
    // turbo runs every package's jsdom tests in parallel; on 2-core CI runners the heavier component and
    // page tests take 5–8 s. Assertions still fail fast; only the overall budget is longer.
    testTimeout: 15_000,
    setupFiles: ["./src/test/setup.ts"],
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
