import { defineConfig, devices } from "@playwright/test";

/**
 * Targets come from the environment so the same suite runs against the stack started in CI
 * (the defaults) and against production after a deploy (smoke tests, read-only).
 */
export const urls = {
  storefront: process.env.STOREFRONT_URL ?? "http://localhost:3000",
  admin: process.env.ADMIN_URL ?? "http://localhost:3001",
  api: process.env.API_URL ?? "http://localhost:8080",
};

export default defineConfig({
  testDir: "./tests",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["github"], ["html", { open: "never" }]] : "list",
  use: {
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [
    {
      name: "chromium",
      use: {
        ...devices["Desktop Chrome"],
        // Use a preinstalled browser when one is provided (e.g. in sandboxes).
        launchOptions: process.env.PW_CHROMIUM_PATH ? { executablePath: process.env.PW_CHROMIUM_PATH } : {},
      },
    },
  ],
});
