import { render, screen, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { API, categories, server } from "@/test/server";
import HomePage from "./page";

describe("HomePage", () => {
  it("shows the hero, categories from the API, occasions and bestsellers", async () => {
    render(await HomePage());

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("Heirloom silks for the season");

    const categoryNav = screen.getByRole("navigation", { name: "Shop by category" });
    const links = within(categoryNav).getAllByRole("link");
    expect(links.map((link) => link.textContent)).toEqual(categories.map((c) => c.name));
    expect(links[0]).toHaveAttribute("href", `/c/${categories[0]!.slug}`);

    expect(screen.getByRole("link", { name: /Haldi & Mehendi/ })).toHaveAttribute("href", "/occasion/haldi-mehendi");

    const bestsellers = screen.getByRole("region", { name: "Bestsellers" });
    expect(within(bestsellers).getAllByRole("article")).toHaveLength(4);
    expect(within(bestsellers).getByText("₹12,499")).toBeInTheDocument();
    expect(within(bestsellers).getByText("22% off")).toBeInTheDocument();
  });

  it("shows a friendly message when the API is down, and the rest of the page still renders", async () => {
    server.use(http.get(`${API}/api/v1/store/categories`, () => new HttpResponse(null, { status: 503 })));

    render(await HomePage());

    expect(screen.getByText("The catalogue is unavailable right now.")).toBeInTheDocument();
    expect(screen.queryByRole("navigation", { name: "Shop by category" })).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Bestsellers" })).toBeInTheDocument();
  });
});
