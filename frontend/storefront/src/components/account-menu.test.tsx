import { act, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { sessionStore } from "@/lib/session";
import { AccountMenu } from "./account-menu";

const toggle = () => screen.getByRole("button", { name: "Account" });

describe("AccountMenu", () => {
  afterEach(() => localStorage.clear());

  it("opens a menu with profile, orders, wishlist and sign up for guests", () => {
    render(<AccountMenu />);
    expect(toggle()).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByRole("link", { name: "Profile" })).not.toBeInTheDocument();

    fireEvent.click(toggle());
    expect(toggle()).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByRole("link", { name: "Profile" })).toHaveAttribute("href", "/account");
    expect(screen.getByRole("link", { name: "Orders" })).toHaveAttribute("href", "/account/orders");
    expect(screen.getByRole("link", { name: "Wishlist" })).toHaveAttribute("href", "/wishlist");
    expect(screen.getByRole("link", { name: "Sign up / Log in" })).toHaveAttribute("href", "/signup");
    expect(screen.queryByRole("button", { name: "Log out" })).not.toBeInTheDocument();
  });

  it("shows log out for a signed-in customer and signs them out", () => {
    act(() => sessionStore.write({ name: "Asha" }));
    render(<AccountMenu />);
    fireEvent.click(toggle());
    expect(screen.getByText("Hi, Asha")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Sign up / Log in" })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Log out" }));
    expect(sessionStore.read()).toBeNull();
    fireEvent.click(toggle());
    expect(screen.getByRole("link", { name: "Sign up / Log in" })).toBeInTheDocument();
  });

  it("closes on Escape, outside click and choosing an item", () => {
    render(<AccountMenu />);
    fireEvent.click(toggle());
    fireEvent.keyDown(document, { key: "Escape" });
    expect(toggle()).toHaveAttribute("aria-expanded", "false");

    fireEvent.click(toggle());
    fireEvent.pointerDown(document.body);
    expect(toggle()).toHaveAttribute("aria-expanded", "false");

    fireEvent.click(toggle());
    fireEvent.click(screen.getByRole("link", { name: "Orders" }));
    expect(toggle()).toHaveAttribute("aria-expanded", "false");
  });
});
