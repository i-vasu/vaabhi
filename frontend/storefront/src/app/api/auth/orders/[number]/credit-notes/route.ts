import { api } from "@/lib/api";
import { problemResponse, withCustomer } from "@/lib/auth-server";

/** The credit notes issued against one of the signed-in customer's orders (empty when none). */
export async function GET(_request: Request, { params }: { params: Promise<{ number: string }> }): Promise<Response> {
  const { number } = await params;
  try {
    const notes = await withCustomer((token) => api.customer.myCreditNotes(token, number));
    if (!notes) return Response.json({ detail: "Please log in", errors: [] }, { status: 401 });
    return Response.json(notes, { headers: { "Cache-Control": "no-store" } });
  } catch (error) {
    return problemResponse(error);
  }
}
