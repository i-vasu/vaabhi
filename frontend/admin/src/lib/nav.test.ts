import { describe, expect, it } from "vitest";
import { isActive, primaryNav, searchableNav } from "./nav";

describe("navigation", () => {
  it("marks Home only on the home page and other items on their sub-pages", () => {
    const home = primaryNav.find((item) => item.href === "/");
    const products = primaryNav.find((item) => item.href === "/products/");
    expect(isActive(home!, "/")).toBe(true);
    expect(isActive(home!, "/products/")).toBe(false);
    expect(isActive(products!, "/products")).toBe(true);
    expect(isActive(products!, "/products/edit/")).toBe(true);
    expect(isActive(products!, "/")).toBe(false);
  });

  it("lists each page once for search", () => {
    const hrefs = searchableNav().map((item) => item.href);
    expect(new Set(hrefs).size).toBe(hrefs.length);
    expect(hrefs).toContain("/notifications/");
    expect(hrefs).toEqual(expect.arrayContaining(["/orders/", "/customers/", "/alterations/", "/price-list/"]));
  });
});
