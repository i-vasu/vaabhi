import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";
import { urls } from "../playwright.config";

test.describe("Storefront", () => {
  test("home page shows the hero and categories from the API @smoke", async ({ page }) => {
    await page.goto(urls.storefront);

    await expect(page.getByRole("heading", { level: 1 })).toHaveText("Heirloom silks for the season");
    await expect(page.getByRole("navigation", { name: "Shop by category" }).getByRole("link").first()).toBeVisible();
    await expect(page.getByText("The catalogue is unavailable right now.")).toHaveCount(0);
    await expect(page.getByRole("region", { name: "Bestsellers" }).getByRole("article")).toHaveCount(4);
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

  test("category page renders from the API @smoke", async ({ page }) => {
    await page.goto(`${urls.storefront}/c/sarees`);
    await expect(page.getByRole("heading", { level: 1 })).toHaveText("Sarees");
    await expect(page.getByText(/^\d+ results?$/)).toBeVisible();
  });

  // Needs the demo catalogue (CI), so not @smoke.
  test("filters and sorts a category listing", async ({ page }) => {
    await page.goto(`${urls.storefront}/c/sarees`);
    const products = page.getByRole("list", { name: "Sarees products" }).getByRole("article");
    await expect(products.first()).toBeVisible();

    await page.getByRole("button", { name: "Filter" }).click();
    const sheet = page.getByRole("dialog", { name: "Filters" });
    await sheet.getByRole("tab", { name: "Fabric" }).click();
    await sheet.getByText("Silk", { exact: true }).click();
    await expect(page).toHaveURL(/fabric=silk/);
    await expect(sheet.getByRole("button", { name: /^Show 4 results$/ })).toBeVisible();
    await sheet.getByRole("button", { name: /^Show/ }).click();
    await expect(products).toHaveCount(4);
    await expect(page.getByRole("link", { name: "Remove Silk" })).toBeVisible();

    await page.getByRole("button", { name: /Sort/ }).click();
    await page.getByRole("dialog", { name: "Sort by" }).getByText("Price: low to high").click();
    await expect(page).toHaveURL(/sort=price_asc/);
    await expect(products.first()).toContainText("Pochampally ikat silk saree");

    await page.getByRole("link", { name: "Remove Silk" }).click();
    await expect(page).not.toHaveURL(/fabric=/);
    await expect(products).toHaveCount(11);
  });

  test("category page has no serious accessibility violations", async ({ page }) => {
    await page.goto(`${urls.storefront}/c/sarees?fabric=silk`);
    await page.getByRole("button", { name: /Filter/ }).click();
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === "serious" || v.impact === "critical");
    expect(serious, JSON.stringify(serious, null, 2)).toEqual([]);
  });

  // Needs the demo catalogue (CI), so not @smoke.
  test("opens a product from the listing, picks a blouse and adds it to the bag", async ({ page }) => {
    await page.goto(`${urls.storefront}/c/sarees`);
    await page.getByRole("link", { name: "Kanjeevaram silk saree", exact: true }).first().click();
    await expect(page).toHaveURL(/\/p\/kanjeevaram-silk-saree-temple-border/);
    await expect(page.getByRole("heading", { level: 1 })).toHaveText("Kanjeevaram silk saree");

    await page.getByText("Stitched blouse, standard size").click();
    await page.getByRole("button", { name: "Add to bag" }).first().click();
    await expect(page.getByText("Choose a blouse size.")).toBeVisible();
    await page.locator("label").filter({ hasText: /^36$/ }).click();
    await page.getByRole("button", { name: "Add to bag" }).first().click();
    await expect(page.getByText("Added to bag.")).toBeVisible();
    await expect(page.getByRole("link", { name: "Shopping bag, 1 item" })).toBeVisible();

    await page.getByRole("textbox", { name: "Pincode" }).fill("600001");
    await page.getByRole("button", { name: "CHECK" }).click();
    await expect(page.getByText(/Estimated delivery .* to 600001/)).toBeVisible();
  });

  test("product page has no serious accessibility violations", async ({ page }) => {
    await page.goto(`${urls.storefront}/p/velvet-bridal-lehenga-zardozi`);
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === "serious" || v.impact === "critical");
    expect(serious, JSON.stringify(serious, null, 2)).toEqual([]);
  });
});
