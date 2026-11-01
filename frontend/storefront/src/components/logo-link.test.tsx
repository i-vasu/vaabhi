import { fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { LogoLink } from "./logo-link";

let pathname = "/";
vi.mock("next/navigation", async (importOriginal) => ({
  ...(await importOriginal<typeof import("next/navigation")>()),
  usePathname: () => pathname,
}));

describe("LogoLink", () => {
  const scrollTo = vi.fn();
  beforeEach(() => {
    window.scrollTo = scrollTo as unknown as typeof window.scrollTo;
  });
  afterEach(() => scrollTo.mockReset());

  it("scrolls back to the top of the home page without navigating", () => {
    pathname = "/";
    render(<LogoLink />);
    const link = screen.getByRole("link", { name: "VAABHI" });
    expect(link).toHaveAttribute("href", "/");
    const notPrevented = fireEvent.click(link);
    expect(notPrevented).toBe(false);
    expect(scrollTo).toHaveBeenCalledWith(expect.objectContaining({ top: 0 }));
  });

  it("jumps to the top and navigates home from another page", () => {
    pathname = "/c/sarees";
    render(<LogoLink />);
    fireEvent.click(screen.getByRole("link", { name: "VAABHI" }));
    expect(scrollTo).toHaveBeenCalledWith({ top: 0 });
  });
});
