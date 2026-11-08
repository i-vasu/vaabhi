import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { SiteFooter } from "./site-footer";
import { SiteHeader } from "./site-header";

describe("SiteHeader", () => {
  it("has the announcement, logo and labelled icon actions", () => {
    render(<SiteHeader />);
    expect(screen.getByText(/Free shipping above ₹1,999/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "VAABHI" })).toHaveAttribute("href", "/");
    for (const name of ["Open menu", "Account"]) expect(screen.getByRole("button", { name })).toBeInTheDocument();
    for (const name of ["Search", "Shopping bag"]) expect(screen.getByRole("link", { name })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Wishlist" })).not.toBeInTheDocument();
  });
});

describe("SiteFooter", () => {
  // An async server component (Shop lists the categories from the API, mocked in test/server), so it is awaited.
  it("lists shop, help, policy and about links", async () => {
    render(await SiteFooter());
    for (const name of ["Shop", "Help", "Policies", "About"])
      expect(screen.getByRole("navigation", { name })).toBeInTheDocument();
    expect(screen.getByText(/Handwoven sarees, lehengas and kurta sets/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Sarees" })).toHaveAttribute("href", "/c/sarees");
    expect(screen.getByRole("link", { name: "Track order" })).toHaveAttribute("href", "/track");
    expect(screen.getByRole("link", { name: "Returns & exchanges" })).toHaveAttribute("href", "/policies/returns");
    expect(screen.getByRole("link", { name: "Meet the weavers" })).toHaveAttribute("href", "/weavers");
    expect(screen.getByText(/Made in India/)).toBeInTheDocument();
  });
});
