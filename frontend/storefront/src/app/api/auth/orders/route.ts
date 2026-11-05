import { api } from "@/lib/api";
import { problemResponse, withCustomer } from "@/lib/auth-server";

/** The signed-in customer's online orders, newest first. */
export async function GET(): Promise<Response> {
  try {
    const orders = await withCustomer((token) => api.customer.listOrders(token));
    if (!orders) return Response.json({ detail: "Please sign in", errors: [] }, { status: 401 });
    return Response.json({ orders }, { headers: { "Cache-Control": "no-store" } });
  } catch (error) {
    return problemResponse(error);
  }
}
