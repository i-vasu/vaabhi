import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { sessionStore } from "@/lib/session";
import { server } from "@/test/server";
import { AccountMenu } from "./account-menu";

vi.mock("next/navigation", () => ({ usePathname: () => "/p/banarasi-silk" }));

const toggle = () => screen.getByRole("button", { name: "Account" });
const ASHA = { id: 1, name: "Asha", email: null, phone: "+919876543210" };

describe("AccountMenu", () => {
  let signedIn: typeof ASHA | null;
  let loggedOut: boolean;

  beforeEach(() => {
    signedIn = null;
    loggedOut = false;
    server.use(
      http.get("*/api/auth/session", () => HttpResponse.json({ customer: signedIn })),
      http.post("*/api/auth/logout", () => {
        loggedOut = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );
  });
  afterEach(() => localStorage.clear());

  it("opens a menu with profile, orders, wishlist and sign in for guests", async () => {
    render(<AccountMenu />);
    expect(toggle()).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByRole("link", { name: "Profile" })).not.toBeInTheDocument();

    fireEvent.click(toggle());
    expect(toggle()).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByRole("link", { name: "Profile" })).toHaveAttribute("href", "/account");
    expect(screen.getByRole("link", { name: "Orders" })).toHaveAttribute("href", "/account/orders");
    expect(screen.getByRole("link", { name: "Wishlist" })).toHaveAttribute("href", "/wishlist");
    // Comes back to the page after signing in.
    expect(screen.getByRole("link", { name: "Sign in / Sign up" })).toHaveAttribute(
      "href",
      "/signup?next=%2Fp%2Fbanarasi-silk",
    );
    expect(screen.queryByRole("button", { name: "Log out" })).not.toBeInTheDocument();
    await waitFor(() => expect(sessionStore.read()).toBeNull());
  });

  it("picks up a session from the server, then logs out there too", async () => {
    signedIn = ASHA;
    render(<AccountMenu />);
    fireEvent.click(toggle());
    expect(await screen.findByText("Hi, Asha")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Sign in / Sign up" })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Log out" }));
    await waitFor(() => expect(sessionStore.read()).toBeNull());
    expect(loggedOut).toBe(true);
    fireEvent.click(toggle());
    expect(screen.getByRole("link", { name: "Sign in / Sign up" })).toBeInTheDocument();
  });

  it("forgets a cached session the server no longer has (signed out elsewhere or expired)", async () => {
    act(() => sessionStore.write({ name: "Asha", phone: null, email: null }));
    render(<AccountMenu />);
    await waitFor(() => expect(sessionStore.read()).toBeNull());
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
