import type { CouponQuoteRequest } from "@vaabhi/api-client";
import { api } from "@/lib/api";
import { crossSite, forbidden, jsonBody, problemResponse, withCustomer } from "@/lib/auth-server";

/** Quotes a coupon against the bag for the signed-in customer; the answer shows the discount or why it fails. */
export async function POST(request: Request): Promise<Response> {
  if (crossSite(request)) return forbidden();
  const body = await jsonBody<CouponQuoteRequest>(request);
  if (!body || !Array.isArray(body.lines) || !body.code) {
    return Response.json({ detail: "Your bag or coupon code is missing", errors: [] }, { status: 400 });
  }
  try {
    const quote = await withCustomer((token) => api.customer.quoteCoupon(token, body));
    if (!quote) return Response.json({ detail: "Please log in to check out", errors: [] }, { status: 401 });
    return Response.json(quote, { headers: { "Cache-Control": "no-store" } });
  } catch (error) {
    return problemResponse(error);
  }
}
