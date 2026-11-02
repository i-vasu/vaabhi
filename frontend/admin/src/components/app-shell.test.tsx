import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { getToken, saveSession } from "@/lib/auth";
import { API, server } from "@/test/server";
import { owner, tokenFor, validToken } from "@/test/tokens";
import { AppShell, initials } from "./app-shell";

const replace = vi.fn();
let pathname = "/";
vi.mock("next/navigation", () => ({ usePathname: () => pathname, useRouter: () => ({ replace }) }));

const signIn = () => saveSession({ accessToken: validToken(), expiresIn: 3600, staff: owner });

describe("AppShell", () => {
  beforeEach(() => {
    replace.mockReset();
    pathname = "/";
    server.use(http.get(`${API}/api/v1/admin/me`, () => HttpResponse.json(owner)));
  });

  it("renders the login page without the frame", () => {
    pathname = "/login/";
    render(<AppShell>sign-in form</AppShell>);

    expect(screen.getByText("sign-in form")).toBeInTheDocument();
    expect(screen.queryByRole("navigation")).not.toBeInTheDocument();
  });

  it("sends visitors without a sign-in to login, remembering the page", async () => {
    pathname = "/products/";
    render(<AppShell>secret</AppShell>);

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/login/?next=%2Fproducts%2F"));
    expect(screen.queryByText("secret")).not.toBeInTheDocument();
  });

  it("treats an expired sign-in as signed out", async () => {
    saveSession({ accessToken: tokenFor({ exp: 1 }), expiresIn: 1, staff: owner });
    render(<AppShell>secret</AppShell>);

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/login/?next=%2F"));
  });

  it("shows the signed-in staff member, navigation and the page", async () => {
    signIn();
    pathname = "/products/edit/";
    render(<AppShell>page body</AppShell>);

    expect(await screen.findByText("page body")).toBeInTheDocument();
    expect(screen.getByText("Asha Rao")).toBeInTheDocument();
    expect(screen.getByText("Owner · vaabhi")).toBeInTheDocument();
    const [sidebar] = screen.getAllByRole("navigation", { name: "Main" });
    expect(within(sidebar!).getByRole("link", { name: "Products" })).toHaveAttribute("aria-current", "page");
    expect(within(sidebar!).getByRole("link", { name: "Home" })).not.toHaveAttribute("aria-current");
  });

  it("signs out when the API no longer accepts the token", async () => {
    signIn();
    server.use(http.get(`${API}/api/v1/admin/me`, () => new HttpResponse(null, { status: 401 })));
    render(<AppShell>page</AppShell>);

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/login/?next=%2F"));
    expect(getToken()).toBeUndefined();
  });

  it("keeps the session when the API is only unreachable", async () => {
    signIn();
    server.use(http.get(`${API}/api/v1/admin/me`, () => HttpResponse.error()));
    render(<AppShell>page</AppShell>);

    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(replace).not.toHaveBeenCalled();
    expect(await screen.findByText("page")).toBeInTheDocument();
  });

  it("signs out from the account sheet", async () => {
    signIn();
    render(<AppShell>page</AppShell>);

    fireEvent.click(await screen.findByRole("button", { name: /^Account/ }));
    const sheet = screen.getByRole("dialog", { name: /^Account/ });
    expect(within(sheet).getByText("asha@vaabhi.local")).toBeInTheDocument();
    fireEvent.click(within(sheet).getByRole("button", { name: "Sign out" }));

    expect(replace).toHaveBeenCalledWith("/login/");
    expect(getToken()).toBeUndefined();
  });

  it("finds pages by name in Go to", async () => {
    signIn();
    render(<AppShell>page</AppShell>);

    fireEvent.click(await screen.findByRole("button", { name: "Search" }));
    const sheet = screen.getByRole("dialog", { name: "Go to" });
    fireEvent.change(within(sheet).getByLabelText("Search pages"), { target: { value: "order updates" } });
    expect(within(sheet).getByRole("link", { name: /Notifications/ })).toHaveAttribute("href", "/notifications/");
    expect(within(sheet).queryByRole("link", { name: /Products/ })).not.toBeInTheDocument();

    fireEvent.change(within(sheet).getByLabelText("Search pages"), { target: { value: "zzz" } });
    expect(within(sheet).getByText("No page matches “zzz”.")).toBeInTheDocument();

    fireEvent.keyDown(document, { key: "Escape" });
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});

describe("initials", () => {
  it("uses first and last name", () => {
    expect(initials("Kannam Charitha")).toBe("KC");
    expect(initials("Asha Devi Rao")).toBe("AR");
    expect(initials("vasu")).toBe("VA");
    expect(initials("  ")).toBe("?");
  });
});
