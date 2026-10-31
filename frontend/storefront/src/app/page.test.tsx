import { render, screen } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { API, categories, server } from "@/test/server";
import HomePage from "./page";

describe("HomePage", () => {
  it("lists the catalogue categories", async () => {
    render(await HomePage());

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("New season, new you");
    const items = screen.getAllByRole("listitem");
    expect(items.map((item) => item.textContent)).toEqual(categories.map((c) => c.name));
  });

  it("shows a friendly message when the API is down", async () => {
    server.use(http.get(`${API}/api/v1/store/categories`, () => new HttpResponse(null, { status: 503 })));

    render(await HomePage());

    expect(screen.getByText("The catalogue is unavailable right now.")).toBeInTheDocument();
    expect(screen.queryAllByRole("listitem")).toHaveLength(0);
  });
});
