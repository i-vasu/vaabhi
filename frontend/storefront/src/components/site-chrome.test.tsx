import { render, screen } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { API, categories, server } from "@/test/server";
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
  it("lists categories, published pages and the shop's contact details", async () => {
    server.use(
      http.get(`${API}/api/v1/store/pages`, () =>
        HttpResponse.json([
          { slug: "privacy", section: "POLICY", title: "Privacy" },
          { slug: "size-guide", section: "HELP", title: "Size guide" },
        ]),
      ),
      http.get(`${API}/api/v1/store/shop`, () =>
        HttpResponse.json({
          name: "Vaabhi",
          phone: "98765 43210",
          whatsapp: "919876543210",
          email: "hello@vaabhi.com",
          address: null,
          mapsLink: null,
          instagram: "https://instagram.com/vaabhi",
          facebook: null,
          youtube: null,
          opensAt: "10:00:00",
          closesAt: "19:00:00",
        }),
      ),
    );
    render(await SiteFooter());
    for (const name of ["Shop", "Help", "Policies"])
      expect(screen.getByRole("navigation", { name })).toBeInTheDocument();
    expect(screen.queryByRole("navigation", { name: "About" })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: categories[0]!.name })).toHaveAttribute(
      "href",
      `/c/${categories[0]!.slug}`,
    );
    expect(screen.getByRole("link", { name: "Privacy" })).toHaveAttribute("href", "/policies/privacy");
    expect(screen.getByRole("link", { name: "Size guide" })).toHaveAttribute("href", "/size-guide");
    expect(screen.getByRole("link", { name: "Track your order" })).toHaveAttribute("href", "/account/orders");
    expect(screen.getByRole("link", { name: "98765 43210" })).toHaveAttribute("href", "tel:9876543210");
    expect(screen.getByRole("link", { name: "Instagram" })).toBeInTheDocument();
  });
});
