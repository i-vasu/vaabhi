import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";
import { urls } from "../playwright.config";

test.describe("Storefront", () => {
  test("home page lists categories from the API @smoke", async ({ page }) => {
    await page.goto(urls.storefront);

    await expect(page.getByRole("heading", { level: 1 })).toHaveText("New season, new you");
    await expect(page.getByRole("listitem").first()).toBeVisible();
    await expect(page.getByText("The catalogue is unavailable right now.")).toHaveCount(0);
  });

  test("health endpoint is up @smoke", async ({ request }) => {
    const response = await request.get(`${urls.storefront}/api/health`);
    expect(await response.json()).toEqual({ status: "UP" });
  });

  test("home page has no serious accessibility violations", async ({ page }) => {
    await page.goto(urls.storefront);
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === "serious" || v.impact === "critical");
    expect(serious, JSON.stringify(serious, null, 2)).toEqual([]);
  });
});
