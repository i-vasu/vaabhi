import { render, screen } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { API, server } from "@/test/server";
import SignupPage from "./page";

vi.mock("next/server", () => ({ connection: async () => undefined }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace: vi.fn() }) }));

const options = (body: unknown, status = 200) =>
  server.use(http.get(`${API}/api/v1/auth/options`, () => HttpResponse.json(body as never, { status })));

async function renderPage() {
  render(await SignupPage({ searchParams: Promise.resolve({ next: "/checkout" }) }));
}

describe("SignupPage", () => {
  beforeEach(() => {
    vi.stubEnv("GOOGLE_CLIENT_ID", "client-1");
    window.google = { accounts: { id: { initialize: vi.fn(), renderButton: vi.fn() } } } as never;
  });
  afterEach(() => {
    vi.unstubAllEnvs();
    delete window.google;
  });

  it("hides phone sign-in while the shop cannot send codes", async () => {
    options({ phoneSignIn: false, googleSignIn: true });
    await renderPage();
    expect(screen.getByRole("heading", { name: "Login" })).toBeInTheDocument();
    expect(screen.queryByLabelText("Mobile number")).not.toBeInTheDocument();
  });

  it("offers phone sign-in when codes can be sent", async () => {
    options({ phoneSignIn: true, googleSignIn: false });
    await renderPage();
    expect(screen.getByLabelText("Mobile number")).toBeInTheDocument();
  });

  it("says so when nothing works, and falls back to phone when the API cannot be asked", async () => {
    options({ phoneSignIn: false, googleSignIn: false });
    await renderPage();
    expect(screen.getByRole("status")).toHaveTextContent("Login is not available right now.");

    document.body.innerHTML = "";
    options({ detail: "down" }, 503);
    await renderPage();
    expect(screen.getByLabelText("Mobile number")).toBeInTheDocument();
  });
});
