import type { Metadata } from "next";
import { InvoiceLoader } from "@/components/auth/invoice-loader";

export const metadata: Metadata = { title: "Invoice", robots: { index: false } };

export default async function InvoicePage({ params }: Readonly<{ params: Promise<{ number: string }> }>) {
  const { number } = await params;
  return (
    <section className="mx-auto max-w-2xl px-4 pb-20 pt-12">
      <InvoiceLoader number={number} />
    </section>
  );
}
