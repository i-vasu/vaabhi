import type { components } from "./schema";

export type Category = components["schemas"]["CategoryResponse"];

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

export function createApiClient({ baseUrl, getToken }: ApiClientOptions) {
  async function request<T>(path: string, init?: RequestInit & { next?: { revalidate?: number } }): Promise<T> {
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
      listCategories: (init?: Parameters<typeof request>[1]) =>
        request<Category[]>("/api/v1/store/categories", init),
    },
  };
}

export type ApiClient = ReturnType<typeof createApiClient>;
