import { api } from "@/lib/api";
import { crossSite, forbidden, jsonBody, problemResponse, saveSession } from "@/lib/auth-server";

/** Signs in with the ID token from Google's button; the backend verifies it with Google's keys. */
export async function POST(request: Request): Promise<Response> {
  if (crossSite(request)) return forbidden();
  const body = await jsonBody<{ credential?: unknown }>(request);
  if (typeof body?.credential !== "string") {
    return Response.json({ detail: "Google sign-in failed. Please try again.", errors: [] }, { status: 400 });
  }
  try {
    const session = await api.auth.signInWithGoogle(body.credential);
    await saveSession(session);
    return Response.json(
      { customer: session.customer, newCustomer: session.newCustomer },
      { headers: { "Cache-Control": "no-store" } },
    );
  } catch (error) {
    return problemResponse(error, "Google sign-in failed. Please try again.");
  }
}
