import AxeBuilder from "@axe-core/playwright";
import { createHmac } from "node:crypto";
import { expect, test } from "@playwright/test";
import { urls } from "../playwright.config";

/** An admin token signed with the CI backend's secret (the dev default unless JWT_SECRET is set). */
function adminToken(): string {
  const secret = process.env.JWT_SECRET ?? "dev-only-secret-change-me-0123456789abcdef";
  const b64 = (value: object) => Buffer.from(JSON.stringify(value)).toString("base64url");
  const now = Math.floor(Date.now() / 1000);
  const unsigned = `${b64({ alg: "HS256", typ: "JWT" })}.${b64({ sub: "e2e", roles: ["ADMIN"], iat: now, exp: now + 600 })}`;
  return `${unsigned}.${createHmac("sha256", secret).update(unsigned).digest("base64url")}`;
}

// Writes data, so never tagged @smoke (smoke tests run against production).
test.describe("Admin products", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto(`${urls.admin}/login/`);
    await page.getByLabel("Access token").fill(adminToken());
    await page.getByRole("button", { name: "Save token" }).click();
    await expect(page.getByText("Token saved")).toBeVisible();
  });

  test("creates, publishes and lists a product in the store", async ({ page, request }) => {
    const name = `E2E Ruby Silk Saree ${Date.now()}`;
    await page.getByRole("link", { name: "Products" }).click();
    await page.getByRole("link", { name: "New product" }).click();

    await page.getByRole("textbox", { name: "Name", exact: true }).fill(name);
    await page.getByLabel("Category").selectOption("sarees");
    await page.getByText("Silk", { exact: true }).first().click();
    await page.getByRole("textbox", { name: "Colour name" }).fill("Ruby");
    await page.getByLabel("Colour group").selectOption("red");
    await page.getByRole("textbox", { name: "Price ₹", exact: true }).fill("14250");
    await page.getByRole("textbox", { name: "MRP ₹", exact: true }).fill("17800");
    await page.getByRole("button", { name: "Add colour" }).click();
    await page.getByRole("textbox", { name: "Stock for Ruby" }).fill("3");
    await page.getByRole("button", { name: "Add standard blouse options" }).click();
    await page.getByRole("button", { name: "Save & publish" }).click();

    await expect(page).toHaveURL(/\/products\/edit\/\?id=\d+/);
    await expect(page.getByText("Published", { exact: true })).toBeVisible();
    const slug = await page.getByRole("textbox", { name: /URL name/ }).inputValue();

    const detail = await request.get(`${urls.api}/api/v1/store/products/${slug}`);
    expect(detail.ok()).toBeTruthy();
    const body = await detail.json();
    expect(body.pricePaise).toBe(1425000);
    expect(body.addons).toHaveLength(3);

    await page.getByRole("button", { name: "Unpublish" }).click();
    await expect(page.getByText("Draft.")).toBeVisible();
    expect((await request.get(`${urls.api}/api/v1/store/products/${slug}`)).status()).toBe(404);
  });

  test("product list and editor have no serious accessibility violations", async ({ page }) => {
    await page.goto(`${urls.admin}/products/`);
    await expect(page.getByRole("table")).toBeVisible();
    for (const target of [`${urls.admin}/products/`, `${urls.admin}/products/new/`]) {
      await page.goto(target);
      await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
      await page.waitForLoadState("networkidle");
      const results = await new AxeBuilder({ page }).analyze();
      const serious = results.violations.filter((v) => v.impact === "serious" || v.impact === "critical");
      expect(serious, JSON.stringify(serious, null, 2)).toEqual([]);
    }
  });
});
