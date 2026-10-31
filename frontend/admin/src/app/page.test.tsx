import { render, screen } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { API, server } from "@/test/server";
import DashboardPage from "./page";

describe("DashboardPage", () => {
  it("shows API status and the number of categories", async () => {
    render(<DashboardPage />);

    expect(await screen.findByText("UP")).toBeInTheDocument();
    expect(await screen.findByText("3")).toBeInTheDocument();
  });

  it("reports the API as down when health fails", async () => {
    server.use(
      http.get(`${API}/actuator/health`, () => HttpResponse.error()),
      http.get(`${API}/api/v1/store/categories`, () => HttpResponse.error()),
    );

    render(<DashboardPage />);

    expect(await screen.findByText("DOWN")).toBeInTheDocument();
    expect(screen.getByText("—")).toBeInTheDocument();
  });
});
