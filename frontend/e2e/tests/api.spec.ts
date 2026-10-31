import { expect, test } from "@playwright/test";
import { urls } from "../playwright.config";

test.describe("API", () => {
  test("is healthy @smoke", async ({ request }) => {
    const response = await request.get(`${urls.api}/actuator/health`);
    expect(response.ok()).toBeTruthy();
    expect(await response.json()).toMatchObject({ status: "UP" });
  });

  test("serves the catalogue categories @smoke", async ({ request }) => {
    const response = await request.get(`${urls.api}/api/v1/store/categories`);
    expect(response.ok()).toBeTruthy();
    const categories: Array<{ slug: string; name: string }> = await response.json();
    expect(categories.length).toBeGreaterThan(0);
    expect(categories[0]).toHaveProperty("slug");
  });

  test("protects admin endpoints @smoke", async ({ request }) => {
    const response = await request.get(`${urls.api}/api/v1/admin/ping`);
    expect(response.status()).toBe(401);
  });

  for (const path of ["/api/v1/store/categories", "/actuator/health"]) {
    test(`allows the admin origin via CORS on ${path} @smoke`, async ({ request }) => {
      const response = await request.get(`${urls.api}${path}`, { headers: { Origin: urls.admin } });
      expect(response.headers()["access-control-allow-origin"]).toBe(urls.admin);
    });
  }
});
