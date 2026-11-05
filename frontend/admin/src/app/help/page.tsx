"use client";

import { HelpIcon } from "@/components/icons";
import { PageHeader, SectionLabel } from "@/components/ui";

const TOPICS: { q: string; a: string }[] = [
  {
    q: "How do I book an order?",
    a: "Orders → New order. Pick the customer (or add one), add each garment from the price list, check the measurements, take an advance and book. The total and balance update as you type.",
  },
  {
    q: "How does work move through the workshop?",
    a: "Work board: give each item to a tailor and move it to the next stage when that step is done. When every item reaches Ready the order shows as ready; mark it delivered from the order page.",
  },
  {
    q: "When is the invoice made?",
    a: "With auto-invoice on (Settings → Billing), the invoice is made when the order is delivered. You can also issue one from the order page. Cancelled invoices keep their number.",
  },
  {
    q: "How do I message a customer?",
    a: "On an order or a customer's page, choose Message. The text comes from Settings → Printing & messages; it opens in WhatsApp (or SMS) on this phone ready to send.",
  },
  {
    q: "How is salary worked out?",
    a: "Payroll previews the month from attendance, finished work and advances. Absent and leave days reduce a monthly salary; unmarked days are paid. Finalise the month, then pay each payslip.",
  },
  {
    q: "Why can't someone see a page?",
    a: "Each role sees only the areas the owner allows (Staff → Roles & permissions). Changes apply when they next sign in.",
  },
  {
    q: "Prices, stock and coupons",
    a: "Price list holds what you stitch and each stage's piece rate. Stock tracks cloth and material in and out. Coupons are applied on New order and fill in the discount.",
  },
];

/** How the dashboard works, and where to get help. */
export default function HelpPage() {
  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader title="Help & support" backHref="/more/" />
      <SectionLabel>Common questions</SectionLabel>
      <div className="flex flex-col gap-3">
        {TOPICS.map((t) => (
          <details key={t.q} className="rounded-2xl border border-line bg-panel p-4">
            <summary className="cursor-pointer font-medium">{t.q}</summary>
            <p className="mt-2 text-ink-soft">{t.a}</p>
          </details>
        ))}
      </div>
      <SectionLabel>Still stuck?</SectionLabel>
      <div className="flex items-start gap-3 rounded-2xl border border-line bg-panel p-4">
        <HelpIcon className="mt-0.5 text-accent-ink" />
        <p className="text-ink-soft">
          Ask the shop owner first; for problems with the dashboard itself, note the page, what you did and the app
          version (Settings → App & account) and send it to whoever looks after your vaabhi setup.
        </p>
      </div>
    </section>
  );
}
