import type { Metadata } from "next";
import Link from "next/link";
import { buttonClasses } from "@vaabhi/ui";

export const metadata: Metadata = { title: "Your orders | Vaabhi", robots: { index: false } };

// Placeholder until customer sign-in and order history are built.
export default function OrdersPage() {
  return (
    <section className="mx-auto max-w-6xl px-4 pb-20 pt-12 text-center">
      <h1 className="font-serif text-[32px] font-semibold">Your orders</h1>
      <p className="mx-auto mb-6 mt-2 max-w-[40ch] text-text-muted">
        Order history is coming soon. To check on an order meanwhile, use Track order with your order number.
      </p>
      <Link href="/track" className={buttonClasses("primary")}>
        Track order
      </Link>
    </section>
  );
}
