import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { SiteFooter } from "./site-footer";
import { SiteHeader } from "./site-header";

describe("SiteHeader", () => {
  it("has the announcement, logo and labelled icon actions", () => {
    render(<SiteHeader />);
    expect(screen.getByText(/Free shipping above ₹1,999/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "VAABHI" })).toHaveAttribute("href", "/");
    for (const name of ["Open menu"]) expect(screen.getByRole("button", { name })).toBeInTheDocument();
    for (const name of ["Search", "Wishlist", "Shopping bag"])
      expect(screen.getByRole("link", { name })).toBeInTheDocument();
  });
});

describe("SiteFooter", () => {
  it("lists shop and help links", () => {
    render(<SiteFooter />);
    expect(screen.getByRole("navigation", { name: "Shop" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Track order" })).toHaveAttribute("href", "/track");
    expect(screen.getByText(/Made in India/)).toBeInTheDocument();
  });
});
