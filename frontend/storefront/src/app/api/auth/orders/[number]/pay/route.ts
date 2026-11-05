import { api } from "@/lib/api";
import { crossSite, forbidden, problemResponse, withCustomer } from "@/lib/auth-server";

/** "Pay now" on an unpaid online order: what Razorpay Checkout needs to open again. */
export async function POST(request: Request, { params }: { params: Promise<{ number: string }> }): Promise<Response> {
  if (crossSite(request)) return forbidden();
  const { number } = await params;
  try {
    const started = await withCustomer((token) => api.customer.payOrderAgain(token, number));
    if (!started) return Response.json({ detail: "Please sign in again", errors: [] }, { status: 401 });
    return Response.json(started, { headers: { "Cache-Control": "no-store" } });
  } catch (error) {
    return problemResponse(error);
  }
}
