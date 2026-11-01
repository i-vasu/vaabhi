import { ApiError, type BagQuoteRequest } from "@vaabhi/api-client";
import { api } from "@/lib/api";

/**
 * Same-origin pricing for the bag page: the browser posts here and the storefront asks the API over the internal
 * network, so the public API needs no CORS entry for the shop's own domain.
 */
export async function POST(request: Request): Promise<Response> {
  let body: BagQuoteRequest;
  try {
    body = (await request.json()) as BagQuoteRequest;
  } catch {
    return Response.json({ detail: "Invalid bag" }, { status: 400 });
  }
  try {
    return Response.json(await api.store.quoteBag(body), { headers: { "Cache-Control": "no-store" } });
  } catch (error) {
    const status = error instanceof ApiError && error.status < 500 ? error.status : 502;
    return Response.json({ detail: "Could not price the bag" }, { status });
  }
}
