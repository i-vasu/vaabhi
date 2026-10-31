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

    expect(fetchMock.mock.calls[0]![0]).toBe(
      "https://api.example.com/api/v1/store/categories/kurta%20sets/products?fabric=silk%2Ccotton&inStock=true&page=2&sort=price_asc",
    );
    expect(fetchMock.mock.calls[1]![0]).toBe("https://api.example.com/api/v1/store/products/banarasi%2Fsaree");
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
