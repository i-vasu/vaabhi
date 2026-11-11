import { api } from "@/lib/api";
import { problemResponse, withCustomer } from "@/lib/auth-server";

/** The signed-in customer's invoice for one of their orders. */
export async function GET(_request: Request, { params }: { params: Promise<{ number: string }> }): Promise<Response> {
  const { number } = await params;
  try {
    const invoice = await withCustomer((token) => api.customer.myInvoice(token, number));
    if (!invoice) return Response.json({ detail: "Please log in", errors: [] }, { status: 401 });
    return Response.json(invoice, { headers: { "Cache-Control": "no-store" } });
  } catch (error) {
    return problemResponse(error);
  }
}
