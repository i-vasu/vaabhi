import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";
import { urls } from "../playwright.config";
import { signIn } from "./admin-session";

// Writes data, so never tagged @smoke (smoke tests run against production).
test.describe("Admin settings", () => {
  test("saves GST settings and a holiday, and they stay saved", async ({ page }) => {
    await signIn(page, "/settings/");
    await page.getByRole("link", { name: /Billing & tax/ }).click();

    await page.getByRole("switch", { name: "Charge GST" }).click();
    await page.getByLabel("GSTIN").fill("27ABCDE1234F1Z5");
    await page.getByRole("button", { name: "Save GST settings" }).click();
    await expect(page.getByRole("status").first()).toHaveText("Saved");

    await page.goto(`${urls.admin}/settings/holidays/`);
    await page.getByRole("button", { name: "Sun" }).click();
    await page.getByRole("button", { name: /Add date/ }).click();
    await page.getByLabel("Date").fill("2026-12-25");
    await page.getByLabel("Holiday name").fill("Christmas");
    await page.getByRole("button", { name: "Add holiday" }).click();
    await page.getByRole("button", { name: "Save" }).click();
    await expect(page.getByRole("status")).toHaveText("Saved");

    await page.reload();
    await expect(page.getByText("Christmas")).toBeVisible();
    await page.goto(`${urls.admin}/settings/billing/`);
    await expect(page.getByLabel("GSTIN")).toHaveValue("27ABCDE1234F1Z5");
  });

  test("settings pages have no serious accessibility violations", async ({ page }) => {
    test.setTimeout(120_000);
    await signIn(page, "/settings/");
    for (const path of ["/settings/", "/settings/shop/", "/settings/orders/", "/settings/stages/"]) {
      await page.goto(`${urls.admin}${path}`);
      await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
      await page.waitForLoadState("networkidle");
      const results = await new AxeBuilder({ page }).analyze();
      const serious = results.violations.filter((v) => v.impact === "serious" || v.impact === "critical");
      expect(serious, `${path}: ${JSON.stringify(serious, null, 2)}`).toEqual([]);
    }
  });
});
