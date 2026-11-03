import { render, screen, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { API, server } from "@/test/server";
import { product } from "@/test/listing-fixture";
import NewArrivals from "./page";

const NEW = `${API}/api/v1/store/new-arrivals`;

describe("New arrivals page", () => {
  it("lists the newest products and loads more pages", async () => {
    const requested: string[] = [];
    server.use(
      http.get(NEW, ({ request }) => {
        const page = Number(new URL(request.url).searchParams.get("page"));
        requested.push(new URL(request.url).search);
        return HttpResponse.json({
          items: [product({ slug: `saree-${page}`, name: `Saree ${page}` })],
          total: 3,
          page,
          pageSize: 24,
        });
      }),
    );

    render(await NewArrivals({ searchParams: Promise.resolve({ page: "2" }) }));

    expect(requested.sort()).toEqual(["?page=1&pageSize=24", "?page=2&pageSize=24"]);
    expect(screen.getByRole("heading", { level: 1, name: "New arrivals" })).toBeInTheDocument();
    const list = screen.getByRole("list", { name: "New arrivals" });
    // Each card links twice (photo and name).
    const hrefs = within(list)
      .getAllByRole("link")
      .map((a) => a.getAttribute("href"));
    expect([...new Set(hrefs)]).toEqual(["/p/saree-1", "/p/saree-2"]);
    expect(screen.getByText("Showing 2 of 3")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Load more" })).toHaveAttribute("href", "/new?page=3");
  });

  it("explains an empty list and an unreachable API", async () => {
    server.use(http.get(NEW, () => HttpResponse.json({ items: [], total: 0, page: 1, pageSize: 24 })));
    const { unmount } = render(await NewArrivals({ searchParams: Promise.resolve({ page: "x" }) }));
    expect(screen.getByText("New styles are on their way")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Load more" })).not.toBeInTheDocument();
    unmount();

    server.use(http.get(NEW, () => new HttpResponse(null, { status: 503 })));
    render(await NewArrivals({ searchParams: Promise.resolve({}) }));
    expect(screen.getByText(/The catalogue is unavailable right now/)).toBeInTheDocument();
  });
});
