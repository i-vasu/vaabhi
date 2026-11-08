import type { CustomerDetails } from "@vaabhi/api-client";
import { api } from "@/lib/api";
import { crossSite, forbidden, jsonBody, problemResponse, withCustomer } from "@/lib/auth-server";

const signedOut = () => Response.json({ detail: "Please log in again", errors: [] }, { status: 401 });

/** The signed-in customer's date of birth, address and measurements. */
export async function GET(): Promise<Response> {
  try {
    const details = await withCustomer((token) => api.customer.getDetails(token));
    if (!details) return signedOut();
    return Response.json({ details }, { headers: { "Cache-Control": "no-store" } });
  } catch (error) {
    return problemResponse(error);
  }
}

/** Saves them (the API validates every field). */
export async function PUT(request: Request): Promise<Response> {
  if (crossSite(request)) return forbidden();
  const body = await jsonBody<CustomerDetails>(request);
  if (!body || typeof body !== "object") {
    return Response.json({ detail: "Nothing to save", errors: [] }, { status: 400 });
  }
  try {
    const details = await withCustomer((token) => api.customer.updateDetails(token, body));
    if (!details) return signedOut();
    return Response.json({ details }, { headers: { "Cache-Control": "no-store" } });
  } catch (error) {
    return problemResponse(error);
  }
}
