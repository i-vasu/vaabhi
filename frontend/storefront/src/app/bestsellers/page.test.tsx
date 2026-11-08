import { render, screen, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { API, server } from "@/test/server";
import { product } from "@/test/listing-fixture";
import Bestsellers from "./page";

describe("Bestsellers page", () => {
  it("lists up to 24 bestsellers from the API", async () => {
    let url: string | undefined;
    server.use(
      http.get(`${API}/api/v1/store/featured`, ({ request }) => {
        url = request.url;
        return HttpResponse.json([product({ slug: "a", name: "A" }), product({ slug: "b", name: "B" })]);
      }),
    );
    render(await Bestsellers());
    expect(url).toBe(`${API}/api/v1/store/featured?badge=BESTSELLER&limit=24`);
    expect(within(screen.getByRole("list", { name: "Bestsellers" })).getAllByRole("article")).toHaveLength(2);
    expect(screen.getByText(/Our most-loved styles — 2 styles/)).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Load more" })).not.toBeInTheDocument();
  });
});
