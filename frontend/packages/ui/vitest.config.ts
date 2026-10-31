import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    environment: "jsdom",
    // turbo runs every package's jsdom tests in parallel; on 2-core CI runners the heavier component and
    // page tests take 5–8 s. Assertions still fail fast; only the overall budget is longer.
    testTimeout: 15_000,
    setupFiles: ["./src/test-setup.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
    coverage: {
      provider: "v8",
      include: ["src/**/*.{ts,tsx}"],
      exclude: ["src/**/*.test.{ts,tsx}", "src/index.ts", "src/test-setup.ts"],
      reporter: ["text-summary", "lcov", "json-summary"],
      thresholds: { lines: 70 },
    },
  },
});
