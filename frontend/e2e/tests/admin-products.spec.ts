import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";
import { urls } from "../playwright.config";
import { signIn } from "./admin-session";

// Writes data, so never tagged @smoke (smoke tests run against production).
test.describe("Admin products", () => {
  test.beforeEach(async ({ page }) => {
    await signIn(page);
  });

  // Publishing needs a photo and a video, which CI cannot store (no R2 credentials): this covers everything up to
  // that point. AdminCatalogueIT covers publish → visible in the store.
  test("creates a draft with photos picked before saving; publishing waits for a video", async ({ page, request }) => {
    const name = `E2E Ruby Silk Saree ${Date.now()}`;
    await page.getByRole("link", { name: "Products", exact: true }).click();
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
    const publish = page.getByRole("button", { name: "Save & publish" });
    await expect(publish).toBeDisabled();
    await expect(page.getByLabel("Add video")).toBeDisabled();

    // A photo (a 6×8 maroon PNG) picked before the first save waits, and unlocks the video.
    const png = Buffer.from(
      "iVBORw0KGgoAAAANSUhEUgAAAAYAAAAICAIAAABVpBlvAAAAEUlEQVR4nGPI0zJFQwyDQQgAdS0mcc+VYwAAAAAASUVORK5CYII=",
      "base64",
    );
    await page.getByLabel("Add photos").setInputFiles({ name: "front.png", mimeType: "image/png", buffer: png });
    await expect(page.getByText("Uploads when you save", { exact: true })).toBeVisible();
    await expect(page.getByLabel("Add video")).toBeEnabled();
    await expect(publish).toBeDisabled();

    // Saving creates the draft, then uploads the photo: the API answers 503 because CI has no R2 credentials.
    await page.getByRole("button", { name: "Save", exact: true }).click();
    await expect(page).toHaveURL(/\/products\/edit\/\?id=\d+/);
    await expect(page.getByRole("alert")).toContainText(/front.png: Photo storage is not configured/);
    await expect(page.getByText("Draft", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "Save & publish" })).toBeDisabled();

    const slug = await page.getByRole("textbox", { name: /URL name/ }).inputValue();
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
