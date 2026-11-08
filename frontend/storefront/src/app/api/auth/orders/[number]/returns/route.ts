import type { ReturnRequestForm } from "@vaabhi/api-client";
import { api } from "@/lib/api";
import { crossSite, forbidden, jsonBody, problemResponse, withCustomer } from "@/lib/auth-server";

/** The customer asks to return items of a delivered order. */
export async function POST(request: Request, { params }: { params: Promise<{ number: string }> }): Promise<Response> {
  if (crossSite(request)) return forbidden();
  const { number } = await params;
  const body = await jsonBody<ReturnRequestForm>(request);
  if (!body?.reason || !Array.isArray(body.items) || body.items.length === 0) {
    return Response.json({ detail: "Choose the items to return and tell us why", errors: [] }, { status: 400 });
  }
  try {
    const order = await withCustomer((token) => api.customer.requestReturn(token, number, body));
    if (!order) return Response.json({ detail: "Please log in again", errors: [] }, { status: 401 });
    return Response.json(order, { status: 201, headers: { "Cache-Control": "no-store" } });
  } catch (error) {
    return problemResponse(error);
  }
}
