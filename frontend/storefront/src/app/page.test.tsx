import { render, screen, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it } from "vitest";
import { API, categories, server } from "@/test/server";
import { product } from "@/test/listing-fixture";
import HomePage from "./page";

const FEATURED = `${API}/api/v1/store/featured`;
const HOME = `${API}/api/v1/store/home`;

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
          occasions: [
            banner({ id: 2, slot: "OCCASION", title: "Haldi & Mehendi", link: "/c/kurta-sets?occasion=haldi" }),
          ],
          craft: banner({ id: 3, slot: "CRAFT", title: "Woven by hand, worn for generations" }),
        }),
      ),
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
    expect(links.map((link) => link.textContent)).toEqual(categories.map((c) => c.name));
    expect(links[0]).toHaveAttribute("href", `/c/${categories[0]!.slug}`);

    expect(screen.getByRole("link", { name: /Haldi & Mehendi/ })).toHaveAttribute(
      "href",
      "/c/kurta-sets?occasion=haldi",
    );

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

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("Handwoven sarees, lehengas & kurta sets");
    expect(screen.queryByRole("heading", { name: "Shop by occasion" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Explore the edit" })).not.toBeInTheDocument();
  });
});
