import { currentCustomer, problemResponse } from "@/lib/auth-server";

/**
 * Who is signed in ({ customer: null } for guests). Called on every page load, which also renews the
 * session: a customer who visits at least every 60 days stays signed in.
 */
export async function GET(): Promise<Response> {
  try {
    return Response.json({ customer: await currentCustomer() }, { headers: { "Cache-Control": "no-store" } });
  } catch (error) {
    return problemResponse(error);
  }
}
