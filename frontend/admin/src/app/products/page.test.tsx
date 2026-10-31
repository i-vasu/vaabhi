import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import type { AdminProductSummary } from "@vaabhi/api-client";
import { API, server } from "@/test/server";
import ProductsPage from "./page";

const row = (id: number, overrides: Partial<AdminProductSummary> = {}): AdminProductSummary => ({
  id,
  slug: `product-${id}`,
  name: `Product ${id}`,
  category: { slug: "sarees", name: "Sarees" },
  status: "PUBLISHED",
  variantCount: 2,
  stockTotal: 5,
  minPricePaise: 1425000,
  updatedAt: "2026-10-31T09:00:00Z",
  ...overrides,
});

describe("ProductsPage", () => {
  it("lists products and filters by status, category and search", async () => {
    const queries: string[] = [];
    server.use(
      http.get(`${API}/api/v1/admin/products`, ({ request }) => {
        const query = new URL(request.url).searchParams;
        queries.push(query.toString());
        return HttpResponse.json({
          items: [row(1), row(2, { status: "DRAFT", stockTotal: 0, minPricePaise: null })],
          total: 120,
          page: Number(query.get("page")),
          pageSize: 50,
        });
      }),
    );
    render(<ProductsPage />);

    const table = await screen.findByRole("table");
    expect(within(table).getByRole("link", { name: "Product 1" })).toHaveAttribute(
      "href",
      expect.stringMatching(/^\/products\/edit\/?\?id=1$/),
    );
    expect(within(table).getByText("₹14,250")).toBeInTheDocument();
    expect(within(table).getByText("—")).toBeInTheDocument();
    expect(screen.getByText("120 products")).toBeInTheDocument();
    expect(screen.getByText("Page 1 of 3")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Drafts" }));
    await waitFor(() => expect(queries.at(-1)).toBe("page=1&pageSize=50&status=DRAFT"));
    fireEvent.change(await screen.findByRole("combobox", { name: "Category" }), { target: { value: "sarees" } });
    await waitFor(() => expect(queries.at(-1)).toContain("category=sarees"));
    fireEvent.change(screen.getByRole("searchbox", { name: "Search products" }), { target: { value: " ruby " } });
    fireEvent.submit(screen.getByRole("search"));
    await waitFor(() => expect(queries.at(-1)).toContain("q=ruby"));
    fireEvent.click(screen.getByRole("button", { name: "Next" }));
    await waitFor(() => expect(queries.at(-1)).toContain("page=2"));
    fireEvent.click(screen.getByRole("button", { name: "Previous" }));
    await waitFor(() => expect(queries.at(-1)).toContain("page=1"));
  });

  it("shows an empty state", async () => {
    server.use(
      http.get(`${API}/api/v1/admin/products`, () => HttpResponse.json({ items: [], total: 1, page: 1, pageSize: 50 })),
    );
    render(<ProductsPage />);
    expect(await screen.findByText("No products match.")).toBeInTheDocument();
    expect(screen.getByText("1 product")).toBeInTheDocument();
  });

  it("asks to sign in on 401 and reports other failures", async () => {
    server.use(http.get(`${API}/api/v1/admin/products`, () => new HttpResponse(null, { status: 401 })));
    const { unmount } = render(<ProductsPage />);
    expect(await screen.findByRole("alert")).toHaveTextContent("Sign in with an admin token");
    unmount();

    server.use(
      http.get(`${API}/api/v1/admin/products`, () => HttpResponse.error()),
      http.get(`${API}/api/v1/store/categories`, () => HttpResponse.error()),
    );
    render(<ProductsPage />);
    expect(await screen.findByRole("alert")).toHaveTextContent("Could not load products");
  });
});
