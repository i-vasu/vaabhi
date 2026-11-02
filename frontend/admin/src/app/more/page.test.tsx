import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import MorePage from "./page";

describe("MorePage", () => {
  it("lists the grouped pages", () => {
    render(<MorePage />);

    expect(screen.getByRole("heading", { name: "Catalogue" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Products/ })).toHaveAttribute("href", "/products/");
    expect(screen.getByRole("link", { name: /Notifications/ })).toHaveAttribute("href", "/notifications/");
  });
});
