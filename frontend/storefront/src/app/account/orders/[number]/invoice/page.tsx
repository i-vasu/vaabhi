import type { Metadata } from "next";
import { notFound, redirect } from "next/navigation";
import { ApiError } from "@vaabhi/api-client";
import { InvoiceView } from "@/components/auth/invoice-view";
import { api } from "@/lib/api";
import { withCustomer } from "@/lib/auth-server";
import { PrintButton } from "./print-button";

export const dynamic = "force-dynamic";

export const metadata: Metadata = { title: "Invoice", robots: { index: false } };

export default async function InvoicePage({ params }: Readonly<{ params: Promise<{ number: string }> }>) {
  const { number } = await params;
  let invoice;
  try {
    invoice = await withCustomer((token) => api.customer.myInvoice(token, number));
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) notFound();
    throw error;
  }
  if (!invoice) redirect(`/login?next=${encodeURIComponent(`/account/orders/${number}/invoice`)}`);

  return (
    <section className="mx-auto max-w-2xl px-4 pb-20 pt-12">
      <InvoiceView invoice={invoice} />
      <div className="mt-8">
        <PrintButton />
      </div>
    </section>
  );
}
