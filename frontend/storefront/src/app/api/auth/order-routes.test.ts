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

const checkout = await import("./checkout/route");
const payment = await import("./checkout/[number]/payment/route");
const orders = await import("./orders/route");
const pay = await import("./orders/[number]/pay/route");
const cancel = await import("./orders/[number]/cancel/route");
const returns = await import("./orders/[number]/returns/route");

const CUSTOMER = `${API}/api/v1/customer`;
const params = { params: Promise.resolve({ number: "WEB-1001" }) };
const req = (path: string, body?: string, headers: Record<string, string> = {}) =>
  new Request(`http://localhost/api/auth/${path}`, {
    method: "POST",
    body,
    headers: { host: "localhost", ...headers },
  });
const evil = { origin: "https://evil.example" };
const ORDER = { number: "WEB-1001", status: "PLACED" };

/** Records the token and body each API call received. */
const seen: { path: string; auth: string | null; body: unknown }[] = [];
const answer =
  (value: unknown, status = 200) =>
  async ({ request }: { request: Request }) => {
    const text = request.method === "GET" ? "" : await request.text();
    seen.push({
      path: new URL(request.url).pathname,
      auth: request.headers.get("Authorization"),
      body: text ? JSON.parse(text) : null,
    });
    return HttpResponse.json(value as never, { status });
  };

describe("/api/auth order routes", () => {
  beforeEach(() => {
    jar.clear();
    seen.length = 0;
  });

  it("refuses cross-site posts", async () => {
    expect((await checkout.POST(req("checkout", "{}", evil))).status).toBe(403);
    expect((await payment.POST(req("checkout/WEB-1001/payment", "{}", evil), params)).status).toBe(403);
    expect((await pay.POST(req("orders/WEB-1001/pay", undefined, evil), params)).status).toBe(403);
    expect((await cancel.POST(req("orders/WEB-1001/cancel", "{}", evil), params)).status).toBe(403);
    expect((await returns.POST(req("orders/WEB-1001/returns", "{}", evil), params)).status).toBe(403);
  });

  it("rejects bodies without the required fields", async () => {
    expect((await checkout.POST(req("checkout", '{"lines":[]}'))).status).toBe(400);
    expect((await payment.POST(req("checkout/WEB-1001/payment", '{"razorpayOrderId":"o"}'), params)).status).toBe(400);
    expect((await returns.POST(req("orders/WEB-1001/returns", '{"reason":"x","items":[]}'), params)).status).toBe(400);
  });

  it("asks a signed-out shopper to sign in", async () => {
    expect((await checkout.POST(req("checkout", '{"lines":[],"address":{}}'))).status).toBe(401);
    expect((await orders.GET()).status).toBe(401);
    expect((await pay.POST(req("orders/WEB-1001/pay"), params)).status).toBe(401);
    expect((await cancel.POST(req("orders/WEB-1001/cancel", "{}"), params)).status).toBe(401);
    expect(
      (
        await returns.POST(
          req("orders/WEB-1001/returns", '{"reason":"x","items":[{"position":0,"quantity":1}]}'),
          params,
        )
      ).status,
    ).toBe(401);
    expect(
      (
        await payment.POST(
          req("checkout/WEB-1001/payment", '{"razorpayOrderId":"o","razorpayPaymentId":"p","razorpaySignature":"s"}'),
          params,
        )
      ).status,
    ).toBe(401);
  });

  it("passes the customer's calls to the API with their own token", async () => {
    jar.set("vb_at", "access-1");
    server.use(
      http.post(`${CUSTOMER}/checkout`, answer({ orderNumber: "WEB-1001" }, 201)),
      http.post(`${CUSTOMER}/orders/WEB-1001/payment`, answer(ORDER)),
      http.get(`${CUSTOMER}/orders`, answer([ORDER])),
      http.post(`${CUSTOMER}/orders/WEB-1001/pay`, answer({ orderNumber: "WEB-1001" })),
      http.post(`${CUSTOMER}/orders/WEB-1001/cancel`, answer({ ...ORDER, status: "CANCELLED" })),
      http.post(`${CUSTOMER}/orders/WEB-1001/returns`, answer(ORDER, 201)),
    );

    expect((await checkout.POST(req("checkout", '{"lines":[],"address":{}}'))).status).toBe(201);
    const paid = await payment.POST(
      req("checkout/WEB-1001/payment", '{"razorpayOrderId":"o","razorpayPaymentId":"p","razorpaySignature":"s"}'),
      params,
    );
    expect(await paid.json()).toEqual(ORDER);
    expect(await (await orders.GET()).json()).toEqual({ orders: [ORDER] });
    expect((await pay.POST(req("orders/WEB-1001/pay"), params)).status).toBe(200);
    const cancelled = await cancel.POST(req("orders/WEB-1001/cancel", '{"reason":"Twice"}'), params);
    expect((await cancelled.json()).status).toBe("CANCELLED");
    const asked = await returns.POST(
      req("orders/WEB-1001/returns", '{"reason":"Too long","items":[{"position":0,"quantity":1}]}'),
      params,
    );
    expect(asked.status).toBe(201);

    expect(seen.every((s) => s.auth === "Bearer access-1")).toBe(true);
    expect(seen.find((s) => s.path.endsWith("/cancel"))?.body).toEqual({ reason: "Twice" });
    expect(seen.find((s) => s.path.endsWith("/returns"))?.body).toEqual({
      reason: "Too long",
      items: [{ position: 0, quantity: 1 }],
    });
  });

  it("passes on the API's problems", async () => {
    jar.set("vb_at", "access-1");
    server.use(
      http.post(`${CUSTOMER}/orders/WEB-1001/cancel`, () =>
        HttpResponse.json({ detail: "This order has already been shipped", status: 409 }, { status: 409 }),
      ),
      http.post(`${CUSTOMER}/orders/WEB-1001/pay`, () => HttpResponse.json({ detail: "Not needed" }, { status: 409 })),
    );
    const refused = await cancel.POST(req("orders/WEB-1001/cancel", "{}"), params);
    expect(refused.status).toBe(409);
    expect((await refused.json()).detail).toBe("This order has already been shipped");
    expect((await pay.POST(req("orders/WEB-1001/pay"), params)).status).toBe(409);
  });
});
