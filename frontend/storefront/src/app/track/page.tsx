import type { Metadata } from "next";
import { TrackOrder } from "@/components/track-order";

export const metadata: Metadata = {
  title: "Track your order",
  description: "Check where your Vaabhi order is with your order number and mobile number.",
  alternates: { canonical: "/track" },
};

/** Guest order tracking. `/track?order=WEB-1042` fills in the order number (e.g. from an order email). */
export default async function TrackPage({ searchParams }: Readonly<{ searchParams: Promise<{ order?: string }> }>) {
  const { order } = await searchParams;
  return (
    <section className="mx-auto max-w-md px-4 pb-20 pt-12">
      <h1 className="mb-2 text-center font-serif text-[32px] font-semibold">Track your order</h1>
      <p className="mb-8 text-center text-text-muted">
        Enter the order number from your confirmation and the mobile number you ordered with.
      </p>
      <TrackOrder initialNumber={typeof order === "string" ? order.slice(0, 32) : ""} />
    </section>
  );
}
