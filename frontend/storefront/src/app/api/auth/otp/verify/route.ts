import { api } from "@/lib/api";
import { crossSite, forbidden, jsonBody, problemResponse, saveSession } from "@/lib/auth-server";

/** Checks the code and signs the customer in (tokens go into httpOnly cookies, never to the page). */
export async function POST(request: Request): Promise<Response> {
  if (crossSite(request)) return forbidden();
  const body = await jsonBody<{ phone?: unknown; code?: unknown }>(request);
  if (typeof body?.phone !== "string" || typeof body.code !== "string") {
    return Response.json({ detail: "Enter the 6-digit code", errors: [] }, { status: 400 });
  }
  try {
    const session = await api.auth.verifyOtp(body.phone, body.code);
    await saveSession(session);
    return Response.json(
      { customer: session.customer, newCustomer: session.newCustomer },
      { headers: { "Cache-Control": "no-store" } },
    );
  } catch (error) {
    return problemResponse(error);
  }
}
