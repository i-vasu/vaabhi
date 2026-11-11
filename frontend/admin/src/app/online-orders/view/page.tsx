"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense } from "react";
import { useStaff } from "@/components/app-shell";
import { OnlineOrderCard } from "@/components/online-order-card";
import { LoadState, PageHeader } from "@/components/ui";
import { api } from "@/lib/api";
import { useLoad } from "@/lib/tailoring";

/** One website order: where to send it, then ship, track, deliver and handle returns (refunds: owner only). */
function OnlineOrder() {
  const number = useSearchParams().get("number") ?? "";
  const staff = useStaff();
  const order = useLoad(() => api.admin.onlineOrder(number), [number]);

  const invoiceNumber = order.value?.invoiceNumber ?? null;
  const invoice = useLoad(
    async () =>
      invoiceNumber
        ? ((await api.admin.money.invoices({ q: invoiceNumber })).items.find((i) => i.number === invoiceNumber) ?? null)
        : null,
    [invoiceNumber],
  );

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader title={number || "Online order"} backHref="/orders/?source=ONLINE" badge="Online" />
      {!order.value ? (
        <LoadState loading={order.loading} error={order.error} />
      ) : (
        <ul>
          {invoiceNumber && invoice.value && (
            <li className="mb-3">
              <Link href={`/money/invoices/view/?id=${invoice.value.id}`} className="text-accent-ink underline">
                Invoice {invoiceNumber}
              </Link>
            </li>
          )}
          <OnlineOrderCard order={order.value} onChange={order.setValue} canRefund={staff?.role === "OWNER"} />
        </ul>
      )}
    </section>
  );
}

export default function OnlineOrderPage() {
  return (
    <Suspense fallback={<p className="text-ink-soft">Loading…</p>}>
      <OnlineOrder />
    </Suspense>
  );
}
