"use client";

import { useSearchParams } from "next/navigation";
import { Suspense } from "react";
import { OrderEditor } from "@/components/order-form";
import { PageHeader } from "@/components/ui";
import { idParam, useInitialClient } from "@/lib/tailoring";

/** /orders/new/?clientId=42 starts with that customer chosen. */
function NewOrder() {
  const clientId = idParam(useSearchParams().get("clientId"));
  const initial = useInitialClient(clientId);
  return (
    <section className="mx-auto max-w-2xl">
      <PageHeader title="New order" backHref={clientId ? `/customers/view/?id=${clientId}` : "/orders/"} />
      {initial.ready ? <OrderEditor initialClient={initial.client} /> : <p className="text-ink-soft">Loading…</p>}
    </section>
  );
}

export default function NewOrderPage() {
  return (
    <Suspense fallback={<p className="text-ink-soft">Loading…</p>}>
      <NewOrder />
    </Suspense>
  );
}
