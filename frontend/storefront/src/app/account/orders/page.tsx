import type { Metadata } from "next";
import { OrdersView } from "@/components/auth/orders-view";

export const metadata: Metadata = { title: "Your orders | Vaabhi", robots: { index: false } };

export default function OrdersPage() {
  return (
    <section className="mx-auto max-w-2xl px-4 pb-20 pt-12 text-center">
      <h1 className="mb-6 font-serif text-[32px] font-semibold">Your orders</h1>
      <OrdersView />
    </section>
  );
}
