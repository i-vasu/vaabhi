import { render, screen } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { describe, expect, it, vi } from "vitest";
import { options, product } from "@/test/admin-fixtures";
import { API, server } from "@/test/server";
import NewProductPage from "../new/page";
import EditProductPage from "./page";

let search = new URLSearchParams();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn() }),
  useSearchParams: () => search,
}));

describe("product pages", () => {
  it("edit page loads the product from ?id and shows the notice", async () => {
    search = new URLSearchParams("id=7&notice=Published.");
    server.use(
      http.get(`${API}/api/v1/admin/catalog/options`, () => HttpResponse.json(options)),
      http.get(`${API}/api/v1/admin/products/7`, () => HttpResponse.json(product())),
    );
    render(<EditProductPage />);
    expect(await screen.findByRole("heading", { level: 1, name: "Ruby silk saree" })).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("Published.");
  });

  it("edit page without an id links back to the list", () => {
    search = new URLSearchParams("id=abc");
    render(<EditProductPage />);
    expect(screen.getByRole("link", { name: "Back to products" })).toHaveAttribute(
      "href",
      expect.stringMatching(/^\/products\/?$/),
    );
  });

  it("new page starts an empty editor", async () => {
    server.use(http.get(`${API}/api/v1/admin/catalog/options`, () => HttpResponse.json(options)));
    render(<NewProductPage />);
    expect(await screen.findByRole("heading", { level: 1, name: "New product" })).toBeInTheDocument();
  });
});
