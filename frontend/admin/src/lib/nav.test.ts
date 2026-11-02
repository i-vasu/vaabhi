import { describe, expect, it } from "vitest";
import { isActive, primaryNav, searchableNav } from "./nav";

describe("navigation", () => {
  it("marks Home only on the home page and other items on their sub-pages", () => {
    const home = primaryNav.find((item) => item.href === "/");
    const orders = primaryNav.find((item) => item.href === "/orders/");
    expect(isActive(home!, "/")).toBe(true);
    expect(isActive(home!, "/orders/")).toBe(false);
    expect(isActive(orders!, "/orders")).toBe(true);
    expect(isActive(orders!, "/orders/view/")).toBe(true);
    expect(isActive(orders!, "/")).toBe(false);
  });

  it("lists each page once for search", () => {
    const hrefs = searchableNav().map((item) => item.href);
    expect(new Set(hrefs).size).toBe(hrefs.length);
    expect(hrefs).toContain("/notifications/");
    expect(hrefs).toEqual(
      expect.arrayContaining([
        "/orders/",
        "/customers/",
        "/alterations/",
        "/price-list/",
        "/workshop/",
        "/workshop/board/",
        "/products/",
      ]),
    );
  });
});
