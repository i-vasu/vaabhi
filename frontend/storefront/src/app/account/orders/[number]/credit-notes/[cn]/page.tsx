import type { Metadata } from "next";
import { CreditNoteLoader } from "@/components/auth/credit-note-loader";

export const metadata: Metadata = { title: "Credit note", robots: { index: false } };

export default async function CreditNotePage({
  params,
}: Readonly<{ params: Promise<{ number: string; cn: string }> }>) {
  const { number, cn } = await params;
  return (
    <section className="mx-auto max-w-2xl px-4 pb-20 pt-12">
      <CreditNoteLoader number={number} noteNumber={decodeURIComponent(cn)} />
    </section>
  );
}
