import { expect, type Page } from "@playwright/test";
import { urls } from "../playwright.config";

/** The owner the backend's `demo` profile creates (CI and local only; never production). */
export const demoOwner = {
  email: process.env.E2E_STAFF_EMAIL ?? "owner@vaabhi.local",
  password: process.env.E2E_STAFF_PASSWORD ?? "vaabhi-demo-owner",
};

export async function signIn(page: Page, path = "/"): Promise<void> {
  await page.goto(`${urls.admin}/login/?next=${encodeURIComponent(path)}`);
  await page.getByLabel("Email").fill(demoOwner.email);
  await page.getByLabel("Password").fill(demoOwner.password);
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page.getByRole("button", { name: "Account" })).toBeVisible();
}
