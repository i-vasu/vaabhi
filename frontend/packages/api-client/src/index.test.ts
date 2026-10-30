import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, createApiClient } from "./index";

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
});
