import type { components, operations } from "./schema";

type Schemas = components["schemas"];

export type Category = Schemas["CategoryResponse"];
export type ProductListing = Schemas["ProductListingResponse"];
export type ProductSummary = Schemas["ProductSummary"];
export type FilterGroup = Schemas["FilterGroup"];
export type FilterOption = Schemas["FilterOption"];
export type ProductDetail = Schemas["ProductDetailResponse"];
export type ProductVariant = Schemas["VariantResponse"];
export type ProductAddon = Schemas["AddonResponse"];
export type Media = Schemas["MediaResponse"];

/** Listing filters and paging. Facet filters (fabric, occasion, …) take comma-separated value codes. */
export type ListingParams = NonNullable<operations["listProducts"]["parameters"]["query"]> &
  Record<string, string | number | boolean | null | undefined>;

/** Serialises listing params, dropping empty values so URLs (and caches) stay canonical. */
export function listingQueryString(params: ListingParams = {}): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params).sort(([a], [b]) => a.localeCompare(b))) {
    if (value === undefined || value === null || value === "" || value === false) continue;
    search.set(key, String(value));
  }
  const query = search.toString();
  return query ? `?${query}` : "";
}

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

export type ApiClientOptions = {
  baseUrl: string;
  /** Returns the current access token, if any (admin, logged-in customer). */
  getToken?: () => string | undefined;
};

type RequestOptions = RequestInit & { next?: { revalidate?: number; tags?: string[] } };

export function createApiClient({ baseUrl, getToken }: ApiClientOptions) {
  async function request<T>(path: string, init?: RequestOptions): Promise<T> {
    const token = getToken?.();
    const response = await fetch(`${baseUrl.replace(/\/$/, "")}${path}`, {
      ...init,
      headers: {
        Accept: "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...init?.headers,
      },
    });
    if (!response.ok) {
      throw new ApiError(response.status, `${init?.method ?? "GET"} ${path} failed with ${response.status}`);
    }
    return (await response.json()) as T;
  }

  return {
    request,
    store: {
      listCategories: (init?: RequestOptions) => request<Category[]>("/api/v1/store/categories", init),
      listProducts: (categorySlug: string, params?: ListingParams, init?: RequestOptions) =>
        request<ProductListing>(
          `/api/v1/store/categories/${encodeURIComponent(categorySlug)}/products${listingQueryString(params)}`,
          init,
        ),
      getProduct: (slug: string, init?: RequestOptions) =>
        request<ProductDetail>(`/api/v1/store/products/${encodeURIComponent(slug)}`, init),
    },
  };
}

export type ApiClient = ReturnType<typeof createApiClient>;
