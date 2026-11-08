import type { CheckoutRequest } from "@vaabhi/api-client";
import { api } from "@/lib/api";
import { crossSite, forbidden, jsonBody, problemResponse, withCustomer } from "@/lib/auth-server";

/** Places the order from the bag for the signed-in customer; the answer opens Razorpay Checkout. */
export async function POST(request: Request): Promise<Response> {
  if (crossSite(request)) return forbidden();
  const body = await jsonBody<CheckoutRequest>(request);
  if (!body || !Array.isArray(body.lines) || !body.address) {
    return Response.json({ detail: "Your bag or address is missing", errors: [] }, { status: 400 });
  }
  try {
    const started = await withCustomer((token) => api.customer.startCheckout(token, body));
    if (!started) return Response.json({ detail: "Please log in to check out", errors: [] }, { status: 401 });
    return Response.json(started, { status: 201, headers: { "Cache-Control": "no-store" } });
  } catch (error) {
    return problemResponse(error);
  }
}
