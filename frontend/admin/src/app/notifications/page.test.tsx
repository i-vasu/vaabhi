import { render, screen } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { describe, expect, it, vi } from "vitest";
import { API, server } from "@/test/server";
import NotificationsPage from "./page";

vi.mock("next/navigation", () => ({ useRouter: () => ({ back: vi.fn(), push: vi.fn(), replace: vi.fn() }) }));

describe("notifications", () => {
  it("says all is quiet when nothing needs attention", async () => {
    server.use(
      http.get(`${API}/api/v1/admin/me/notifications`, () => HttpResponse.json({ alerts: [], recent: [], unread: 0 })),
      http.post(`${API}/api/v1/admin/me/notifications/seen`, () => new HttpResponse(null, { status: 204 })),
    );
    render(<NotificationsPage />);

    expect(await screen.findByRole("heading", { name: "All quiet" })).toBeInTheDocument();
  });
});
