import { render, screen } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { afterEach, describe, expect, it, vi } from "vitest";
import { saveSession } from "@/lib/auth";
import { API, server } from "@/test/server";
import { owner, validToken } from "@/test/tokens";
import HomePage from "./page";

const DASHBOARD = `${API}/api/v1/admin/reports/dashboard`;

describe("HomePage", () => {
  afterEach(() => vi.useRealTimers());

  it("greets the signed-in staff member and shows the API dot and this month's figures", async () => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date(2026, 10, 2, 9, 0));
    saveSession({ accessToken: validToken(), expiresIn: 3600, staff: owner });
    server.use(
      http.get(DASHBOARD, () =>
        HttpResponse.json({ revenueRupees: 50000, expensesRupees: 20000, profitRupees: 30000, ordersInPipeline: 7 }),
      ),
    );
    render(<HomePage />);

    expect(screen.getByRole("heading", { name: "Good morning, Asha" })).toBeInTheDocument();
    expect(await screen.findByText("API is up")).toBeInTheDocument();
    expect(await screen.findByText("7")).toBeInTheDocument();
    expect(screen.getByText(/Revenue · November/)).toBeInTheDocument();
    expect(screen.getByText(/Profit · November/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Add product/ })).toHaveAttribute("href", "/products/new/");
    expect(screen.getByRole("link", { name: /Stock/ })).toHaveAttribute("href", "/stock/");
    expect(screen.getByRole("link", { name: /Activity log/ })).toHaveAttribute("href", "/activity/");
  });

  it("reports the API as down when health fails", async () => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date(2026, 10, 2, 19, 0));
    server.use(
      http.get(`${API}/actuator/health`, () => HttpResponse.error()),
      http.get(DASHBOARD, () => HttpResponse.error()),
    );

    render(<HomePage />);

    expect(screen.getByRole("heading", { name: "Good evening" })).toBeInTheDocument();
    expect(await screen.findByText("API is down")).toBeInTheDocument();
  });

  it("says good afternoon after noon", () => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date(2026, 10, 2, 14, 0));
    render(<HomePage />);

    expect(screen.getByRole("heading", { name: "Good afternoon" })).toBeInTheDocument();
  });
});
