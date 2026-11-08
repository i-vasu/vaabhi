import { ApiError, type TrackOrderRequest } from "@vaabhi/api-client";
import { api } from "@/lib/api";

/**
 * Same-origin guest order tracking for /track: the browser posts the order number and phone here, and the
 * storefront asks the API over the internal network.
 */
export async function POST(request: Request): Promise<Response> {
  let body: TrackOrderRequest;
  try {
    const json = (await request.json()) as Partial<TrackOrderRequest>;
    body = { number: String(json.number ?? ""), phone: String(json.phone ?? "") };
  } catch {
    return Response.json({ detail: "Enter your order number and mobile number" }, { status: 400 });
  }
  try {
    return Response.json(await api.store.trackOrder(body), { headers: { "Cache-Control": "no-store" } });
  } catch (error) {
    if (error instanceof ApiError && error.status < 500) {
      const detail = error.errors[0]?.message ?? error.detail ?? "We couldn't find that order";
      return Response.json({ detail }, { status: error.status });
    }
    return Response.json({ detail: "Order tracking isn't working right now. Please try again." }, { status: 502 });
  }
}
