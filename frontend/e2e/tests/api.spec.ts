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

  // Needs the demo catalogue (CI); not @smoke because production has no demo products.
  test("lists products with filters and shows a product page", async ({ request }) => {
    const listing = await request.get(`${urls.api}/api/v1/store/categories/sarees/products?fabric=silk&sort=price_asc`);
    expect(listing.ok()).toBeTruthy();
    const body = await listing.json();
    expect(body.total).toBeGreaterThan(0);
    const prices = body.items.map((item: { pricePaise: number }) => item.pricePaise);
    expect(prices).toEqual([...prices].sort((a, b) => a - b));
    expect(body.filters.find((group: { code: string }) => group.code === "fabric")).toBeTruthy();

    const product = await request.get(`${urls.api}/api/v1/store/products/${body.items[0].slug}`);
    expect(product.ok()).toBeTruthy();
    expect((await product.json()).variants.length).toBeGreaterThan(0);
  });

  test("hides unknown products @smoke", async ({ request }) => {
    const response = await request.get(`${urls.api}/api/v1/store/products/no-such-product`);
    expect(response.status()).toBe(404);
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

test("reports the release version and the deployed commit @smoke", async ({ request }) => {
  const response = await request.get(`${urls.api}/actuator/info`);
  expect(response.ok()).toBeTruthy();
  const info = await response.json();
  expect(info.build.version).toMatch(/^\d+\.\d+\.\d+/);
  expect(info.git.commit.id).toBeTruthy();

  // After a deploy, the API must be running exactly the commit that was deployed.
  const expected = process.env.EXPECTED_COMMIT;
  if (expected) {
    expect(expected.startsWith(info.git.commit.id)).toBeTruthy();
  }
});
