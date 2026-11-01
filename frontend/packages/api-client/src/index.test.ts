import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, createApiClient, listingQueryString } from "./index";

describe("createApiClient", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("sends the bearer token and parses JSON", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify([{ id: 1, slug: "tops", name: "Tops" }])));
    vi.stubGlobal("fetch", fetchMock);

    const api = createApiClient({ baseUrl: "https://api.example.com/", getToken: () => "t0k" });
    const categories = await api.store.listCategories();

    expect(categories[0]?.slug).toBe("tops");
    const [url, init] = fetchMock.mock.calls[0]!;
    expect(url).toBe("https://api.example.com/api/v1/store/categories");
    expect(init.headers.Authorization).toBe("Bearer t0k");
  });

  it("throws ApiError on non-2xx responses", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("nope", { status: 503 })));
    const api = createApiClient({ baseUrl: "https://api.example.com" });

    await expect(api.store.listCategories()).rejects.toBeInstanceOf(ApiError);
  });

  it("builds listing and product URLs", async () => {
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(new Response("{}")));
    vi.stubGlobal("fetch", fetchMock);
    const api = createApiClient({ baseUrl: "https://api.example.com" });

    await api.store.listProducts("kurta sets", { fabric: "silk,cotton", sort: "price_asc", inStock: true, page: 2 });
    await api.store.getProduct("banarasi/saree");
    await api.store.quoteBag({ lines: [{ sku: "A", addonCode: null, quantity: 1 }] });

    expect(fetchMock.mock.calls[0]![0]).toBe(
      "https://api.example.com/api/v1/store/categories/kurta%20sets/products?fabric=silk%2Ccotton&inStock=true&page=2&sort=price_asc",
    );
    expect(fetchMock.mock.calls[1]![0]).toBe("https://api.example.com/api/v1/store/products/banarasi%2Fsaree");
    expect(fetchMock.mock.calls[2]![0]).toBe("https://api.example.com/api/v1/store/bag/quote");
    expect(fetchMock.mock.calls[2]![1].method).toBe("POST");
  });
});

describe("admin client", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("sends JSON with the right method and path", async () => {
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(new Response("{}")));
    vi.stubGlobal("fetch", fetchMock);
    const api = createApiClient({ baseUrl: "https://api.example.com", getToken: () => "t" });
    const form = { name: "Saree" } as never;

    await api.admin.catalogOptions();
    await api.admin.listProducts({ status: "DRAFT", q: "silk" });
    await api.admin.listProducts();
    await api.admin.getProduct(7);
    await api.admin.createProduct(form);
    await api.admin.updateProduct(7, form);
    await api.admin.setStatus(7, "publish");
    await api.admin.startMediaUpload(7, { alt: "Front", colourName: null, width: 1600, height: 2133 });
    await api.admin.completeMediaUpload(7, 3);
    await api.admin.updateMedia(7, [{ id: 3, alt: "Front", colourName: null }]);
    await api.admin.deleteMedia(7, 3);

    const calls = fetchMock.mock.calls.map(([url, init]) => `${init.method ?? "GET"} ${url}`);
    expect(calls).toEqual([
      "GET https://api.example.com/api/v1/admin/catalog/options",
      "GET https://api.example.com/api/v1/admin/products?q=silk&status=DRAFT",
      "GET https://api.example.com/api/v1/admin/products",
      "GET https://api.example.com/api/v1/admin/products/7",
      "POST https://api.example.com/api/v1/admin/products",
      "PUT https://api.example.com/api/v1/admin/products/7",
      "POST https://api.example.com/api/v1/admin/products/7/publish",
      "POST https://api.example.com/api/v1/admin/products/7/media/uploads",
      "POST https://api.example.com/api/v1/admin/products/7/media/3/complete",
      "PUT https://api.example.com/api/v1/admin/products/7/media",
      "DELETE https://api.example.com/api/v1/admin/products/7/media/3",
    ]);
    expect(fetchMock.mock.calls[4]![1].body).toBe('{"name":"Saree"}');
    expect(fetchMock.mock.calls[4]![1].headers["Content-Type"]).toBe("application/json");
    expect(fetchMock.mock.calls[6]![1].body).toBeUndefined();
  });

  it("exposes field errors from problem details", async () => {
    const problem = {
      detail: "Unknown category",
      errors: [{ field: "categorySlug", message: "Unknown category" }, { x: 1 }],
    };
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify(problem), { status: 400 })));
    const api = createApiClient({ baseUrl: "https://api.example.com" });

    const error = await api.admin.getProduct(1).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).detail).toBe("Unknown category");
    expect((error as ApiError).errors).toEqual([{ field: "categorySlug", message: "Unknown category" }]);
  });
});

describe("listingQueryString", () => {
  it("drops empty values and sorts keys", () => {
    expect(listingQueryString({ work: "zari", colour: "", size: null, inStock: false, page: 1 })).toBe(
      "?page=1&work=zari",
    );
    expect(listingQueryString()).toBe("");
  });
});

describe("customer auth client", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("posts sign-in calls and sends the customer's own token for the profile", async () => {
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(new Response("{}")));
    vi.stubGlobal("fetch", fetchMock);
    const api = createApiClient({ baseUrl: "https://api.example.com" });

    await api.auth.requestOtp("9876543210");
    await api.auth.verifyOtp("9876543210", "123456");
    await api.customer.getProfile("acc-1");

    expect(fetchMock.mock.calls[0]![0]).toBe("https://api.example.com/api/v1/auth/otp/request");
    expect(JSON.parse(fetchMock.mock.calls[1]![1].body)).toEqual({ phone: "9876543210", code: "123456" });
    expect(fetchMock.mock.calls[2]![0]).toBe("https://api.example.com/api/v1/customer/me");
    expect(fetchMock.mock.calls[2]![1].headers.Authorization).toBe("Bearer acc-1");
  });

  it("returns nothing for 204 and exposes Retry-After seconds on 429", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValueOnce(new Response(null, { status: 204 }))
        .mockResolvedValueOnce(
          new Response(JSON.stringify({ detail: "Please wait", retryAfterSeconds: 30 }), { status: 429 }),
        ),
    );
    const api = createApiClient({ baseUrl: "https://api.example.com" });

    await expect(api.auth.logout("r")).resolves.toBeUndefined();
    await expect(api.auth.requestOtp("9876543210")).rejects.toMatchObject({
      status: 429,
      detail: "Please wait",
      retryAfterSeconds: 30,
    });
  });
});
