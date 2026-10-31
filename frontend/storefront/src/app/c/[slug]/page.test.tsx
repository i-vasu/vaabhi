import { render, screen, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { describe, expect, it, vi } from "vitest";
import { listing, product } from "@/test/listing-fixture";
import { API, server } from "@/test/server";
import CategoryPage, { generateMetadata } from "./page";

vi.mock("next/navigation", async (importOriginal) => ({
  ...(await importOriginal<typeof import("next/navigation")>()),
  useRouter: () => ({ replace: vi.fn() }),
  usePathname: () => "/c/sarees",
}));

const LISTING = `${API}/api/v1/store/categories/:slug/products`;

function props(searchParams: Record<string, string> = {}, slug = "sarees") {
  return { params: Promise.resolve({ slug }), searchParams: Promise.resolve(searchParams) };
}

describe("CategoryPage", () => {
  it("renders the category, quick filters, applied filters and products", async () => {
    const requests: URL[] = [];
    server.use(
      http.get(LISTING, ({ request }) => {
        requests.push(new URL(request.url));
        return HttpResponse.json(listing({ total: 3, items: [product(), product({ slug: "two", name: "Second" })] }));
      }),
    );

    render(await CategoryPage(props({ fabric: "silk", priceMax: "5000", sort: "new" })));

    expect(requests[0]!.searchParams.toString()).toBe("fabric=silk&page=1&pageSize=24&priceMax=500000&sort=new");
    expect(screen.getByRole("heading", { level: 1, name: "Sarees" })).toBeInTheDocument();
    expect(screen.getByText("Handloom silks and cottons — 3 styles")).toBeInTheDocument();
    expect(
      within(screen.getByRole("navigation", { name: "Breadcrumb" })).getByRole("link", { name: "Home" }),
    ).toHaveAttribute("href", "/");

    const quick = within(screen.getByRole("navigation", { name: "Quick filters" }));
    expect(quick.getAllByRole("link").map((l) => l.textContent)).toEqual([
      "All",
      "Silk",
      "Cotton",
      "Ready to ship",
      "Under ₹5,000",
    ]);
    expect(quick.getByRole("link", { name: "Silk" })).toHaveAttribute("aria-current", "true");
    expect(quick.getByRole("link", { name: "All" })).not.toHaveAttribute("aria-current");
    expect(quick.getByRole("link", { name: "Under ₹5,000" })).toHaveAttribute("href", "/c/sarees?fabric=silk&sort=new");
    expect(quick.getByRole("link", { name: "Ready to ship" })).toHaveAttribute(
      "href",
      "/c/sarees?fabric=silk&inStock=true&priceMax=5000&sort=new",
    );

    const applied = within(screen.getByRole("list", { name: "Applied filters" }));
    expect(applied.getByRole("link", { name: "Remove Silk" })).toHaveAttribute(
      "href",
      "/c/sarees?priceMax=5000&sort=new",
    );
    expect(applied.getByRole("link", { name: "Clear all" })).toHaveAttribute("href", "/c/sarees?sort=new");

    expect(screen.getByText("3 results")).toBeInTheDocument();
    const grid = within(screen.getByRole("list", { name: "Sarees products" }));
    expect(grid.getAllByRole("article")).toHaveLength(2);
    expect(screen.getByText("Showing 2 of 3")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Load more" })).toHaveAttribute(
      "href",
      "/c/sarees?fabric=silk&priceMax=5000&sort=new&page=2",
    );
    expect(screen.getByRole("button", { name: "Filter, 2 applied" })).toBeInTheDocument();
  });

  it("loads every page up to the requested one", async () => {
    const pages: string[] = [];
    server.use(
      http.get(LISTING, ({ request }) => {
        const page = new URL(request.url).searchParams.get("page")!;
        pages.push(page);
        return HttpResponse.json(
          listing({ total: 2, items: [product({ slug: `p${page}`, name: `Product ${page}` })] }),
        );
      }),
    );

    render(await CategoryPage(props({ page: "2" })));

    expect(pages.sort()).toEqual(["1", "2"]);
    expect(screen.getAllByRole("article")).toHaveLength(2);
    expect(screen.queryByRole("link", { name: "Load more" })).not.toBeInTheDocument();
    expect(
      within(screen.getByRole("navigation", { name: "Quick filters" })).getByRole("link", { name: "All" }),
    ).toHaveAttribute("aria-current", "true");
  });

  it("shows an empty state", async () => {
    server.use(
      http.get(LISTING, () =>
        HttpResponse.json(
          listing({ total: 0, items: [], category: { id: 1, slug: "sarees", name: "Sarees", description: null } }),
        ),
      ),
    );

    render(await CategoryPage(props({ inStock: "true" })));

    expect(screen.getByText("Nothing matches these filters")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Clear filters" })).toHaveAttribute("href", "/c/sarees");
    expect(screen.getByText("0 results")).toBeInTheDocument();
    expect(screen.queryByText(/styles/)).not.toBeInTheDocument();
  });

  it("returns 404 for unknown categories", async () => {
    server.use(http.get(LISTING, () => HttpResponse.json({ status: 404 }, { status: 404 })));

    await expect(CategoryPage(props({}, "nope"))).rejects.toThrow();
  });

  it("shows a message when the API is down", async () => {
    server.use(http.get(LISTING, () => new HttpResponse(null, { status: 503 })));

    render(await CategoryPage(props()));

    expect(screen.getByText(/catalogue is unavailable/)).toBeInTheDocument();
  });

  it("has a title and canonical URL", async () => {
    await expect(generateMetadata(props({}, "kurta-sets"))).resolves.toEqual({
      title: "Kurta sets | Vaabhi",
      alternates: { canonical: "/c/kurta-sets" },
    });
  });
});
