import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import MorePage from "./page";

vi.mock("next/navigation", () => ({ useRouter: () => ({ back: vi.fn(), push: vi.fn(), replace: vi.fn() }) }));

describe("MorePage", () => {
  it("lists the grouped pages", () => {
    render(<MorePage />);

    expect(screen.getByRole("heading", { name: "Catalogue" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Products/ })).toHaveAttribute("href", "/products/");
    expect(screen.getByRole("link", { name: /Notifications/ })).toHaveAttribute("href", "/notifications/");
  });
});
