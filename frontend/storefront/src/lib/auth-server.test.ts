import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { API, server } from "@/test/server";

/** A minimal stand-in for next/headers' cookie store. */
const jar = new Map<string, { value: string; maxAge?: number; path?: string; httpOnly?: boolean }>();
vi.mock("next/headers", () => ({
  cookies: async () => ({
    get: (name: string) => (jar.has(name) ? { name, value: jar.get(name)!.value } : undefined),
    set: (name: string, value: string, options: { maxAge?: number; path?: string; httpOnly?: boolean }) =>
      jar.set(name, { value, ...options }),
    delete: (options: { name: string }) => jar.delete(options.name),
  }),
}));

const { currentCustomer, crossSite, endSession, problemResponse, withCustomer } = await import("./auth-server");
const { ApiError } = await import("@vaabhi/api-client");

const ASHA = { id: 1, name: "Asha", email: null, phone: "+919876543210" };
const session = (n: number) => ({
  accessToken: `access-${n}`,
  accessTokenExpiresIn: 900,
  refreshToken: `refresh-${n}`,
  refreshTokenExpiresIn: 5_184_000,
  customer: ASHA,
  newCustomer: false,
});

describe("auth-server", () => {
  beforeEach(() => jar.clear());

  it("is signed out without cookies", async () => {
    expect(await currentCustomer()).toBeNull();
  });

  it("uses the access token, and refreshes (rotating both cookies) once it has expired", async () => {
    server.use(
      http.get(`${API}/api/v1/customer/me`, ({ request }) =>
        request.headers.get("Authorization") === "Bearer access-2"
          ? HttpResponse.json(ASHA)
          : new HttpResponse(null, { status: 401 }),
      ),
      http.post(`${API}/api/v1/auth/refresh`, async ({ request }) => {
        expect(await request.json()).toEqual({ refreshToken: "refresh-1" });
        return HttpResponse.json(session(2));
      }),
    );
    jar.set("vb_rt", { value: "refresh-1" });

    expect(await currentCustomer()).toEqual(ASHA);
    expect(jar.get("vb_at")).toMatchObject({ value: "access-2", maxAge: 870, httpOnly: true, path: "/api/auth" });
    expect(jar.get("vb_rt")).toMatchObject({ value: "refresh-2", maxAge: 5_184_000 });

    // Next page load: the access cookie is still there, no refresh needed.
    expect(await currentCustomer()).toEqual(ASHA);
  });

  it("refreshes once when the API rejects a still-present access token, and signs out if that fails", async () => {
    let refreshes = 0;
    server.use(
      http.get(`${API}/api/v1/customer/me`, ({ request }) =>
        request.headers.get("Authorization") === "Bearer access-2"
          ? HttpResponse.json(ASHA)
          : new HttpResponse(null, { status: 401 }),
      ),
      http.post(`${API}/api/v1/auth/refresh`, () => {
        refreshes += 1;
        return refreshes === 1 ? HttpResponse.json(session(2)) : new HttpResponse(null, { status: 401 });
      }),
    );
    jar.set("vb_at", { value: "access-revoked" });
    jar.set("vb_rt", { value: "refresh-1" });
    expect(await currentCustomer()).toEqual(ASHA);

    jar.set("vb_at", { value: "access-revoked" });
    expect(await currentCustomer()).toBeNull();
    expect(jar.size).toBe(0);
  });

  it("lets other errors through instead of signing the customer out", async () => {
    server.use(http.get(`${API}/api/v1/customer/me`, () => new HttpResponse(null, { status: 500 })));
    jar.set("vb_at", { value: "access-1" });
    await expect(withCustomer((t) => import("./api").then(({ api }) => api.customer.getProfile(t)))).rejects.toThrow();
    expect(jar.has("vb_at")).toBe(true);
  });

  it("ends the session on the API and clears cookies even if the API is down", async () => {
    let revoked: unknown;
    server.use(
      http.post(`${API}/api/v1/auth/logout`, async ({ request }) => {
        revoked = await request.json();
        return new HttpResponse(null, { status: 204 });
      }),
    );
    jar.set("vb_at", { value: "a" });
    jar.set("vb_rt", { value: "refresh-9" });
    await endSession();
    expect(revoked).toEqual({ refreshToken: "refresh-9" });
    expect(jar.size).toBe(0);

    server.use(http.post(`${API}/api/v1/auth/logout`, () => HttpResponse.error()));
    jar.set("vb_rt", { value: "refresh-10" });
    await endSession();
    expect(jar.size).toBe(0);
  });

  it("refuses cross-site posts", () => {
    const post = (headers: Record<string, string>) => new Request("https://vaabhi.com/api/auth/logout", { headers });
    expect(crossSite(post({ origin: "https://vaabhi.com", host: "vaabhi.com" }))).toBe(false);
    expect(crossSite(post({ origin: "https://evil.example", host: "vaabhi.com" }))).toBe(true);
    expect(crossSite(post({ origin: "https://vaabhi.com", "x-forwarded-host": "vaabhi.com", host: "storefront:3000" }))).toBe(
      false,
    );
    expect(crossSite(post({ host: "vaabhi.com" }))).toBe(false);
    expect(crossSite(post({ origin: "null", host: "vaabhi.com" }))).toBe(true);
  });

  it("passes the API's problem through and hides unexpected failures", async () => {
    const tooSoon = problemResponse(new ApiError(429, "x", [], "Please wait", 30));
    expect(tooSoon.status).toBe(429);
    expect(await tooSoon.json()).toEqual({ detail: "Please wait", errors: [], retryAfterSeconds: 30 });

    const crash = problemResponse(new ApiError(500, "x"));
    expect(crash.status).toBe(500);
    expect((await crash.json()).detail).toBe("Something went wrong. Please try again.");

    expect(problemResponse(new TypeError("fetch failed")).status).toBe(502);
  });
});
