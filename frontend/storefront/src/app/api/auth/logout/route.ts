import { crossSite, endSession, forbidden } from "@/lib/auth-server";

/** Ends the session on the server and deletes the cookies. */
export async function POST(request: Request): Promise<Response> {
  if (crossSite(request)) return forbidden();
  await endSession();
  return new Response(null, { status: 204 });
}
