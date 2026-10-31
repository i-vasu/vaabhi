import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";
import { urls } from "../playwright.config";

test.describe("Admin", () => {
  test("dashboard reaches the API @smoke", async ({ page }) => {
    await page.goto(urls.admin);

    await expect(page.getByRole("heading", { name: "Dashboard" })).toBeVisible();
    // "UP" proves the browser can call the API: right URL baked in, and CORS allows this origin.
    await expect(page.getByText("UP", { exact: true })).toBeVisible();
  });

  test("sign-in page renders @smoke", async ({ page }) => {
    await page.goto(`${urls.admin}/login/`);
    await expect(page.getByRole("heading", { name: "Sign in" })).toBeVisible();
  });

  test("dashboard has no serious accessibility violations", async ({ page }) => {
    await page.goto(urls.admin);
    await expect(page.getByText("UP", { exact: true })).toBeVisible();
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === "serious" || v.impact === "critical");
    expect(serious, JSON.stringify(serious, null, 2)).toEqual([]);
  });
});
