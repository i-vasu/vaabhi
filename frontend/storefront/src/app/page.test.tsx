import { render, screen, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it } from "vitest";
import { API, categories, server } from "@/test/server";
import { listing, product } from "@/test/listing-fixture";
import HomePage from "./page";

const FEATURED = `${API}/api/v1/store/featured`;
const HOME = `${API}/api/v1/store/home`;
const CATEGORY_PRODUCTS = `${API}/api/v1/store/categories/:slug/products`;
const SEARCH = `${API}/api/v1/store/products`;

const banner = (over: Record<string, unknown>) => ({
  id: 1,
  slot: "HERO",
  position: 1,
  eyebrow: null,
  title: "Banner",
  subtitle: null,
  ctaLabel: null,
  link: null,
  imageKey: "site/00000000-0000-0000-0000-000000000000",
  altText: null,
  active: true,
  startsOn: null,
  endsOn: null,
  live: true,
  ...over,
});

describe("HomePage", () => {
  let featuredUrl: string | undefined;
  beforeEach(() => {
    featuredUrl = undefined;
    server.use(
      http.get(HOME, () =>
        HttpResponse.json({
          hero: [
            banner({
              title: "Heirloom silks for the season",
              eyebrow: "THE WEDDING EDIT · 2026",
              ctaLabel: "Explore the edit",
              link: "/c/sarees?occasion=wedding",
            }),
          ],
          occasions: [],
          craft: banner({ id: 3, slot: "CRAFT", title: "Woven by hand, worn for generations" }),
        }),
      ),
      // Categories show only when they have products; occasions are the tags on products ("Wedding" in the fixture).
      http.get(CATEGORY_PRODUCTS, () => HttpResponse.json(listing({ total: 2 }))),
      http.get(SEARCH, () => HttpResponse.json(listing({ total: 3 }))),
      http.get(FEATURED, ({ request }) => {
        featuredUrl = request.url;
        return HttpResponse.json([
          product({ slug: "emerald-kanjeevaram", name: "Emerald Kanjeevaram", badge: "BESTSELLER" }),
          product({ slug: "ruby-banarasi", name: "Ruby Banarasi", priceRupees: 12499, mrpRupees: 15999 }),
        ]);
      }),
    );
  });

  it("shows the hero, categories from the API, occasions and bestsellers from the API", async () => {
    render(await HomePage());

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("Heirloom silks for the season");
    expect(screen.getByRole("link", { name: "Explore the edit" })).toHaveAttribute(
      "href",
      "/c/sarees?occasion=wedding",
    );

    const categoryNav = screen.getByRole("navigation", { name: "Shop by category" });
    const links = within(categoryNav).getAllByRole("link");
    // Named by the label; the tile's initial (shown when there is no photo) is aria-hidden.
    expect(links).toHaveLength(categories.length);
    categories.forEach((c, i) => expect(links[i]).toHaveAccessibleName(c.name));
    expect(links[0]).toHaveAttribute("href", `/c/${categories[0]!.slug}`);

    expect(screen.getByRole("link", { name: /Wedding/ })).toHaveAttribute("href", "/search?occasion=wedding");

    expect(featuredUrl).toBe(`${FEATURED}?badge=BESTSELLER&limit=8`);
    const bestsellers = screen.getByRole("region", { name: "Bestsellers" });
    expect(within(bestsellers).getAllByRole("article")).toHaveLength(2);
    expect(within(bestsellers).getAllByRole("link", { name: /Ruby Banarasi/ })[0]).toHaveAttribute(
      "href",
      "/p/ruby-banarasi",
    );
    expect(within(bestsellers).getByText("₹12,499")).toBeInTheDocument();
    expect(within(bestsellers).getByText("22% off")).toBeInTheDocument();
  });

  it("shows a friendly message when the API is down, and the rest of the page still renders", async () => {
    server.use(
      http.get(`${API}/api/v1/store/categories`, () => new HttpResponse(null, { status: 503 })),
      http.get(FEATURED, () => new HttpResponse(null, { status: 503 })),
    );

    render(await HomePage());

    expect(screen.getByText("The catalogue is unavailable right now.")).toBeInTheDocument();
    expect(screen.queryByRole("navigation", { name: "Shop by category" })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Bestsellers" })).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Woven by hand, worn for generations" })).toBeInTheDocument();
  });

  it("leaves out banner sections that have nothing live, without a made-up hero button", async () => {
    server.use(http.get(HOME, () => HttpResponse.json({ hero: [], occasions: [], craft: null })));

    render(await HomePage());

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("Handwoven sarees, lehengas & kurtis");
    // Occasions come from product tags, not banners, so only the craft section goes.
    expect(screen.queryByRole("heading", { name: "Woven by hand, worn for generations" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Explore the edit" })).not.toBeInTheDocument();
  });
});
