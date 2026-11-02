import { render, screen } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { afterEach, describe, expect, it, vi } from "vitest";
import { saveSession } from "@/lib/auth";
import { API, server } from "@/test/server";
import { owner, validToken } from "@/test/tokens";
import HomePage from "./page";

describe("HomePage", () => {
  afterEach(() => vi.useRealTimers());

  it("greets the signed-in staff member and shows API status and categories", async () => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date(2026, 10, 2, 9, 0));
    saveSession({ accessToken: validToken(), expiresIn: 3600, staff: owner });
    render(<HomePage />);

    expect(screen.getByRole("heading", { name: "Good morning, Asha" })).toBeInTheDocument();
    expect(await screen.findByText("UP")).toBeInTheDocument();
    expect(await screen.findByText("3")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Add product/ })).toHaveAttribute("href", "/products/new/");
  });

  it("reports the API as down when health fails", async () => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date(2026, 10, 2, 19, 0));
    server.use(
      http.get(`${API}/actuator/health`, () => HttpResponse.error()),
      http.get(`${API}/api/v1/store/categories`, () => HttpResponse.error()),
    );

    render(<HomePage />);

    expect(screen.getByRole("heading", { name: "Good evening" })).toBeInTheDocument();
    expect(await screen.findByText("DOWN")).toBeInTheDocument();
    expect(screen.getByText("—")).toBeInTheDocument();
  });

  it("says good afternoon after noon", () => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date(2026, 10, 2, 14, 0));
    render(<HomePage />);

    expect(screen.getByRole("heading", { name: "Good afternoon" })).toBeInTheDocument();
  });
});
