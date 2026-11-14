import { api } from "@/lib/api";
import { crossSite, forbidden, jsonBody, problemResponse, withCustomer } from "@/lib/auth-server";

/** Sends a WhatsApp code to add or change the signed-in customer's mobile number. */
export async function POST(request: Request): Promise<Response> {
  if (crossSite(request)) return forbidden();
  const body = await jsonBody<{ phone?: unknown }>(request);
  if (typeof body?.phone !== "string") {
    return Response.json({ detail: "Enter your mobile number", errors: [] }, { status: 400 });
  }
  const phone = body.phone;
  try {
    const sent = await withCustomer((token) => api.customer.requestPhoneChange(token, { phone }));
    if (!sent) return Response.json({ detail: "Please log in again", errors: [] }, { status: 401 });
    return Response.json(sent, { headers: { "Cache-Control": "no-store" } });
  } catch (error) {
    return problemResponse(error, "We could not send the code. Please try again.");
  }
}
