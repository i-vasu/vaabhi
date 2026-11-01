import { api } from "@/lib/api";
import { crossSite, forbidden, jsonBody, problemResponse, withCustomer } from "@/lib/auth-server";

/** Updates the signed-in customer's name. */
export async function PUT(request: Request): Promise<Response> {
  if (crossSite(request)) return forbidden();
  const body = await jsonBody<{ name?: unknown }>(request);
  if (typeof body?.name !== "string") {
    return Response.json({ detail: "Enter your name", errors: [] }, { status: 400 });
  }
  const name = body.name;
  try {
    const customer = await withCustomer((token) => api.customer.updateProfile(token, name));
    if (!customer) return Response.json({ detail: "Please sign in again", errors: [] }, { status: 401 });
    return Response.json({ customer }, { headers: { "Cache-Control": "no-store" } });
  } catch (error) {
    return problemResponse(error);
  }
}
