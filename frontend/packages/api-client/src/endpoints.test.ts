import { afterEach, describe, expect, it, vi } from "vitest";
import { createApiClient } from "./index";

/**
 * Every client method, called once with placeholder arguments, must make a request to an /api/ path with a real
 * HTTP method and send any body as JSON. Catches typos in paths and methods across the whole client; the other
 * tests check the exact URLs and bodies of the important calls.
 */
describe("every client method", () => {
  afterEach(() => vi.unstubAllGlobals());

  const BASE = "https://api.example.com";
  // Works as a path segment, a number, an id and a JSON body; Object.entries(7) is empty for query objects.
  const ARG = 7;

  it("calls the API with a valid method, path and JSON body", async () => {
    const calls: { url: string; method: string; body: unknown }[] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn((url: string, init: RequestInit = {}) => {
        calls.push({ url: String(url), method: init.method ?? "GET", body: init.body });
        return Promise.resolve(new Response("{}", { headers: { "Content-Type": "application/json" } }));
      }),
    );
    const api = createApiClient({ baseUrl: BASE, getToken: () => "t" });

    // Areas can nest (e.g. admin.settings.x), so collect every function with its dotted name.
    const methods: [string, unknown][] = [];
    const collect = (prefix: string, value: unknown) => {
      if (typeof value === "function") methods.push([prefix, value]);
      else if (value && typeof value === "object")
        for (const [key, inner] of Object.entries(value)) collect(prefix ? `${prefix}.${key}` : key, inner);
    };
    collect("", api);

    // The raw helpers take any path; every other function is an endpoint.
    const RAW = new Set(["request", "send"]);
    const silent: string[] = [];
    let checked = 0;
    for (const [name, fn] of methods) {
      if (RAW.has(name.split(".").at(-1)!)) continue;
      const before = calls.length;
      try {
        await (fn as (...args: unknown[]) => Promise<unknown>)(ARG, ARG, ARG, ARG);
      } catch {
        // A placeholder argument may not parse as the method expects; the request it made still counts.
      }
      if (calls.length === before) {
        silent.push(name);
        continue;
      }
      checked += 1;
      for (const call of calls.slice(before)) {
        expect(call.url, name).toMatch(/^https:\/\/api\.example\.com\/api\/v1\//);
        expect(["GET", "POST", "PUT", "PATCH", "DELETE"], name).toContain(call.method);
        if (typeof call.body === "string") expect(() => JSON.parse(call.body as string)).not.toThrow();
      }
    }
    expect(silent, "methods that made no request").toEqual([]);
    expect(checked).toBeGreaterThanOrEqual(50);
  });
});
