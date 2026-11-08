import type { PaymentConfirmation } from "@vaabhi/api-client";
import { api } from "@/lib/api";
import { crossSite, forbidden, jsonBody, problemResponse, withCustomer } from "@/lib/auth-server";

/** Hands the payment Razorpay Checkout returned to the API, which checks its signature. */
export async function POST(request: Request, { params }: { params: Promise<{ number: string }> }): Promise<Response> {
  if (crossSite(request)) return forbidden();
  const { number } = await params;
  const body = await jsonBody<PaymentConfirmation>(request);
  if (!body?.razorpayOrderId || !body.razorpayPaymentId || !body.razorpaySignature) {
    return Response.json({ detail: "The payment details are missing", errors: [] }, { status: 400 });
  }
  try {
    const order = await withCustomer((token) => api.customer.confirmPayment(token, number, body));
    if (!order) return Response.json({ detail: "Please log in again", errors: [] }, { status: 401 });
    return Response.json(order, { headers: { "Cache-Control": "no-store" } });
  } catch (error) {
    return problemResponse(error);
  }
}
