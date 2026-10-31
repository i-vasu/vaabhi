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

export type AdminProduct = Schemas["AdminProduct"];
export type AdminProductSummary = Schemas["AdminProductSummary"];
export type AdminProductPage = Schemas["AdminProductPage"];
export type ProductForm = Schemas["ProductForm"];
export type VariantForm = Schemas["VariantForm"];
export type AddonForm = Schemas["AddonForm"];
export type CatalogOptions = Schemas["CatalogOptions"];
export type ProductStatus = AdminProduct["status"];
export type AdminListParams = NonNullable<operations["listAdminProducts"]["parameters"]["query"]>;

/** Field-level message from the API, e.g. `{ field: "variants[0].pricePaise", message: "must be greater than 0" }`. */
export type FieldError = { field: string; message: string };

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
    /** Per-field validation messages from the problem detail, if any. */
    readonly errors: FieldError[] = [],
    /** The problem detail's human-readable explanation, if any. */
    readonly detail?: string,
  ) {
    super(message);
  }
}

async function problemOf(response: Response): Promise<{ detail?: string; errors: FieldError[] }> {
  try {
    const body = (await response.json()) as { detail?: unknown; errors?: unknown };
    const errors = Array.isArray(body.errors)
      ? body.errors.filter((e): e is FieldError => typeof e?.field === "string" && typeof e?.message === "string")
      : [];
    return { detail: typeof body.detail === "string" ? body.detail : undefined, errors };
  } catch {
    return { errors: [] };
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
      const problem = await problemOf(response);
      throw new ApiError(
        response.status,
        `${init?.method ?? "GET"} ${path} failed with ${response.status}`,
        problem.errors,
        problem.detail,
      );
    }
    return (await response.json()) as T;
  }

  const send = <T>(method: "POST" | "PUT", path: string, body?: unknown) =>
    request<T>(path, {
      method,
      headers: body === undefined ? undefined : { "Content-Type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
    });

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
    admin: {
      catalogOptions: () => request<CatalogOptions>("/api/v1/admin/catalog/options"),
      listProducts: (params: AdminListParams = {}) =>
        request<AdminProductPage>(`/api/v1/admin/products${listingQueryString(params)}`),
      getProduct: (id: number) => request<AdminProduct>(`/api/v1/admin/products/${id}`),
      createProduct: (form: ProductForm) => send<AdminProduct>("POST", "/api/v1/admin/products", form),
      updateProduct: (id: number, form: ProductForm) => send<AdminProduct>("PUT", `/api/v1/admin/products/${id}`, form),
      setStatus: (id: number, action: "publish" | "unpublish" | "archive") =>
        send<AdminProduct>("POST", `/api/v1/admin/products/${id}/${action}`),
    },
  };
}

export type ApiClient = ReturnType<typeof createApiClient>;
