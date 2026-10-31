import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    coverage: {
      provider: "v8",
      include: ["src/**/*.{ts,tsx}"],
      exclude: ["src/test/**", "src/**/*.test.{ts,tsx}", "src/**/*.d.ts"],
      reporter: ["text-summary", "lcov", "json-summary"],
      thresholds: { lines: 70 },
    },
  },
});
