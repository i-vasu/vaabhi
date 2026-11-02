import { api } from "@/lib/api";
import { crossSite, forbidden, jsonBody, problemResponse } from "@/lib/auth-server";

/** Sends a sign-in code by SMS. */
export async function POST(request: Request): Promise<Response> {
  if (crossSite(request)) return forbidden();
  const body = await jsonBody<{ phone?: unknown }>(request);
  if (typeof body?.phone !== "string") {
    return Response.json({ detail: "Enter your mobile number", errors: [] }, { status: 400 });
  }
  try {
    return Response.json(await api.auth.requestOtp(body.phone), { headers: { "Cache-Control": "no-store" } });
  } catch (error) {
    return problemResponse(error, "We could not send the code. Please try again.");
  }
}
