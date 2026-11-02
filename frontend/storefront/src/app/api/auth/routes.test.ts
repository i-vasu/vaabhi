import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { API, server } from "@/test/server";

/** A minimal stand-in for next/headers' cookie store. */
const jar = new Map<string, string>();
vi.mock("next/headers", () => ({
  cookies: async () => ({
    get: (name: string) => (jar.has(name) ? { name, value: jar.get(name)! } : undefined),
    set: (name: string, value: string) => jar.set(name, value),
    delete: (options: { name: string }) => jar.delete(options.name),
  }),
}));

const google = await import("./google/route");
const logout = await import("./logout/route");
const profile = await import("./profile/route");
const session = await import("./session/route");
const otpRequest = await import("./otp/request/route");
const otpVerify = await import("./otp/verify/route");

const ASHA = { id: 1, name: "Asha", email: null, phone: "+919876543210" };
const SESSION = {
  accessToken: "access-1",
  accessTokenExpiresIn: 900,
  refreshToken: "refresh-1",
  refreshTokenExpiresIn: 5_184_000,
  customer: ASHA,
  newCustomer: true,
};

const req = (path: string, body?: string, headers: Record<string, string> = {}) =>
  new Request(`http://localhost/api/auth/${path}`, {
    method: "POST",
    body,
    headers: { host: "localhost", ...headers },
  });
const evil = { origin: "https://evil.example" };

describe("/api/auth routes", () => {
  beforeEach(() => jar.clear());

  it("refuses cross-site posts on every write route", async () => {
    expect((await google.POST(req("google", "{}", evil))).status).toBe(403);
    expect((await logout.POST(req("logout", undefined, evil))).status).toBe(403);
    expect((await profile.PUT(req("profile", "{}", evil))).status).toBe(403);
    expect((await otpRequest.POST(req("otp/request", "{}", evil))).status).toBe(403);
    expect((await otpVerify.POST(req("otp/verify", "{}", evil))).status).toBe(403);
  });

  it("rejects bodies without the required fields", async () => {
    expect((await google.POST(req("google", "not json"))).status).toBe(400);
    expect((await profile.PUT(req("profile", '{"name":1}'))).status).toBe(400);
    expect((await otpRequest.POST(req("otp/request", "{}"))).status).toBe(400);
    expect((await otpVerify.POST(req("otp/verify", '{"phone":"+919876543210"}'))).status).toBe(400);
  });

  it("sends a code and passes on the API's problems", async () => {
    server.use(http.post(`${API}/api/v1/auth/otp/request`, () => HttpResponse.json({ resendAfterSeconds: 30 })));
    const sent = await otpRequest.POST(req("otp/request", '{"phone":"+919876543210"}'));
    expect(sent.status).toBe(200);
    expect(sent.headers.get("cache-control")).toBe("no-store");
    expect(await sent.json()).toEqual({ resendAfterSeconds: 30 });

    server.use(http.post(`${API}/api/v1/auth/otp/request`, () => new HttpResponse(null, { status: 500 })));
    const failed = await otpRequest.POST(req("otp/request", '{"phone":"+919876543210"}'));
    expect(failed.status).toBe(500);
    expect((await failed.json()).detail).toBe("We could not send the code. Please try again.");
  });

  it("signs in with a code or with Google, keeping the tokens in cookies only", async () => {
    server.use(
      http.post(`${API}/api/v1/auth/otp/verify`, () => HttpResponse.json(SESSION)),
      http.post(`${API}/api/v1/auth/google`, () => HttpResponse.json(SESSION)),
    );
    const verified = await otpVerify.POST(req("otp/verify", '{"phone":"+919876543210","code":"123456"}'));
    expect(await verified.json()).toEqual({ customer: ASHA, newCustomer: true });
    expect(jar.get("vb_at")).toBe("access-1");
    expect(jar.get("vb_rt")).toBe("refresh-1");

    jar.clear();
    const viaGoogle = await google.POST(req("google", '{"credential":"id-token"}'));
    expect(await viaGoogle.json()).toEqual({ customer: ASHA, newCustomer: true });
    expect(jar.get("vb_rt")).toBe("refresh-1");
  });

  it("reports failed sign-ins", async () => {
    server.use(
      http.post(`${API}/api/v1/auth/otp/verify`, () =>
        HttpResponse.json({ detail: "Wrong code", errors: [] }, { status: 400 }),
      ),
      http.post(`${API}/api/v1/auth/google`, () => HttpResponse.error()),
    );
    const wrong = await otpVerify.POST(req("otp/verify", '{"phone":"+919876543210","code":"000000"}'));
    expect(wrong.status).toBe(400);
    expect(jar.size).toBe(0);
    expect((await google.POST(req("google", '{"credential":"id-token"}'))).status).toBe(502);
  });

  it("returns who is signed in, updates their name and signs them out", async () => {
    server.use(
      http.get(`${API}/api/v1/customer/me`, () => HttpResponse.json(ASHA)),
      http.put(`${API}/api/v1/customer/me`, async ({ request }) =>
        HttpResponse.json({ ...ASHA, ...((await request.json()) as object) }),
      ),
      http.post(`${API}/api/v1/auth/logout`, () => new HttpResponse(null, { status: 204 })),
    );
    expect(await (await session.GET()).json()).toEqual({ customer: null });
    expect((await profile.PUT(req("profile", '{"name":"Asha R"}'))).status).toBe(401);

    jar.set("vb_at", "access-1");
    jar.set("vb_rt", "refresh-1");
    expect(await (await session.GET()).json()).toEqual({ customer: ASHA });
    const renamed = await profile.PUT(req("profile", '{"name":"Asha R"}'));
    expect((await renamed.json()).customer.name).toBe("Asha R");

    expect((await logout.POST(req("logout"))).status).toBe(204);
    expect(jar.size).toBe(0);
  });

  it("hides API failures behind 502s", async () => {
    jar.set("vb_at", "access-1");
    server.use(
      http.get(`${API}/api/v1/customer/me`, () => HttpResponse.error()),
      http.put(`${API}/api/v1/customer/me`, () => HttpResponse.error()),
    );
    expect((await session.GET()).status).toBe(502);
    expect((await profile.PUT(req("profile", '{"name":"Asha R"}'))).status).toBe(502);
  });
});
