import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { SiteMenu } from "./site-menu";

describe("SiteMenu", () => {
  it("opens a drawer with the site sections and closes when one is chosen", () => {
    render(<SiteMenu />);
    const button = screen.getByRole("button", { name: "Open menu" });
    expect(button).toHaveAttribute("aria-expanded", "false");

    fireEvent.click(button);
    expect(button).toHaveAttribute("aria-expanded", "true");
    const nav = within(screen.getByRole("dialog", { name: "Hey, how are you doing today?" })).getByRole("navigation", {
      name: "Site",
    });
    const links = within(nav).getAllByRole("link");
    expect(links.map((l) => [l.textContent, l.getAttribute("href")])).toEqual([
      ["Best sellers", "/bestsellers"],
      ["New arrivals", "/new"],
      ["Pre Order", "/pre-order"],
      ["Collections", "/collections"],
      ["Support", "/support"],
      ["Blogs", "/blog"],
    ]);

    fireEvent.click(within(nav).getByRole("link", { name: "New arrivals" }));
    expect(button).toHaveAttribute("aria-expanded", "false");
  });
});
