import { api } from "@/lib/api";
import { crossSite, forbidden, jsonBody, problemResponse, withCustomer } from "@/lib/auth-server";

/** Verifies the code and saves the signed-in customer's mobile number. */
export async function PUT(request: Request): Promise<Response> {
  if (crossSite(request)) return forbidden();
  const body = await jsonBody<{ phone?: unknown; code?: unknown }>(request);
  if (typeof body?.phone !== "string" || typeof body.code !== "string") {
    return Response.json({ detail: "Enter the 6-digit code", errors: [] }, { status: 400 });
  }
  try {
    const customer = await withCustomer((token) =>
      api.customer.changePhone(token, { phone: body.phone, code: body.code }),
    );
    if (!customer) return Response.json({ detail: "Please log in again", errors: [] }, { status: 401 });
    return Response.json({ customer }, { headers: { "Cache-Control": "no-store" } });
  } catch (error) {
    return problemResponse(error);
  }
}
