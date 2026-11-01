import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { API, server } from "@/test/server";
import { POST } from "./route";

const call = (body: string) => POST(new Request("http://localhost/api/bag/quote", { method: "POST", body }));

describe("POST /api/bag/quote", () => {
  it("forwards the bag to the API and never caches the answer", async () => {
    server.use(
      http.post(`${API}/api/v1/store/bag/quote`, async ({ request }) =>
        HttpResponse.json({ echoed: await request.json() }),
      ),
    );
    const response = await call('{"lines":[{"sku":"A","addonCode":null,"quantity":1}]}');
    expect(response.status).toBe(200);
    expect(response.headers.get("cache-control")).toBe("no-store");
    expect(await response.json()).toEqual({ echoed: { lines: [{ sku: "A", addonCode: null, quantity: 1 }] } });
  });

  it("passes on validation errors and hides API failures", async () => {
    expect((await call("{not json")).status).toBe(400);
    server.use(http.post(`${API}/api/v1/store/bag/quote`, () => HttpResponse.json({}, { status: 400 })));
    expect((await call('{"lines":[]}')).status).toBe(400);
    server.use(http.post(`${API}/api/v1/store/bag/quote`, () => new HttpResponse(null, { status: 500 })));
    expect((await call('{"lines":[]}')).status).toBe(502);
  });
});
