import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";
import { urls } from "../playwright.config";
import { signIn } from "./admin-session";

test.describe("Admin", () => {
  test("sends visitors to sign-in @smoke", async ({ page }) => {
    await page.goto(urls.admin);

    await expect(page).toHaveURL(/\/login\/\?next=%2F/);
    await expect(page.getByRole("heading", { name: "Sign in to the shop" })).toBeVisible();
  });

  test("sign-in reaches the API @smoke", async ({ page }) => {
    await page.goto(`${urls.admin}/login/`);
    // An address no account uses: proves the API URL and CORS without touching a real account.
    await page.getByLabel("Email").fill("smoke-test@example.invalid");
    await page.getByLabel("Password").fill("not-a-password");
    await page.getByRole("button", { name: "Sign in" }).click();

    // Matched by text: the dev server adds its own role=alert route announcer.
    await expect(page.getByText("Email or password is wrong")).toBeVisible();
  });

  test("signed-in staff see the home page and can sign out", async ({ page }) => {
    await signIn(page);

    await expect(page.getByRole("heading", { name: /^Good (morning|afternoon|evening)/ })).toBeVisible();
    await expect(page.getByText("API is up", { exact: true })).toBeAttached();

    await page.getByRole("button", { name: "Account" }).click();
    await page.getByRole("button", { name: "Sign out" }).click();
    await expect(page.getByRole("heading", { name: "Sign in to the shop" })).toBeVisible();
  });

  test("home page has no serious accessibility violations", async ({ page }) => {
    await signIn(page);
    await expect(page.getByText("API is up", { exact: true })).toBeAttached();
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === "serious" || v.impact === "critical");
    expect(serious, JSON.stringify(serious, null, 2)).toEqual([]);
  });

  test("sign-in page has no serious accessibility violations", async ({ page }) => {
    await page.goto(`${urls.admin}/login/`);
    await expect(page.getByRole("heading", { name: "Sign in to the shop" })).toBeVisible();
    const results = await new AxeBuilder({ page }).analyze();
    const serious = results.violations.filter((v) => v.impact === "serious" || v.impact === "critical");
    expect(serious, JSON.stringify(serious, null, 2)).toEqual([]);
  });
});
