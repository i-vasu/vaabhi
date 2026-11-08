import type { CancelWebOrderForm } from "@vaabhi/api-client";
import { api } from "@/lib/api";
import { crossSite, forbidden, jsonBody, problemResponse, withCustomer } from "@/lib/auth-server";

/** The customer cancels an order that has not been shipped yet. */
export async function POST(request: Request, { params }: { params: Promise<{ number: string }> }): Promise<Response> {
  if (crossSite(request)) return forbidden();
  const { number } = await params;
  const body = await jsonBody<CancelWebOrderForm>(request);
  try {
    const order = await withCustomer((token) =>
      api.customer.cancelOrder(token, number, { reason: body?.reason ?? null }),
    );
    if (!order) return Response.json({ detail: "Please sign in again", errors: [] }, { status: 401 });
    return Response.json(order, { headers: { "Cache-Control": "no-store" } });
  } catch (error) {
    return problemResponse(error);
  }
}
