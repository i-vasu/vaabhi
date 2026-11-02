"use client";

import { useSearchParams } from "next/navigation";
import { Suspense } from "react";
import { AlterationEditor } from "@/components/alteration-form";
import { PageHeader } from "@/components/ui";
import { idParam, useInitialClient } from "@/lib/tailoring";

/** /alterations/new/?clientId=42&orderId=7 starts with that customer (and their order) chosen. */
function NewAlteration() {
  const params = useSearchParams();
  const clientId = idParam(params.get("clientId"));
  const orderId = idParam(params.get("orderId"));
  const initial = useInitialClient(clientId);
  const back = orderId ? `/orders/view/?id=${orderId}` : clientId ? `/customers/view/?id=${clientId}` : "/alterations/";
  return (
    <section className="mx-auto max-w-2xl">
      <PageHeader title="New alteration" backHref={back} />
      {initial.ready ? (
        <AlterationEditor initialClient={initial.client} orderId={initial.client ? orderId : null} />
      ) : (
        <p className="text-ink-soft">Loading…</p>
      )}
    </section>
  );
}

export default function NewAlterationPage() {
  return (
    <Suspense fallback={<p className="text-ink-soft">Loading…</p>}>
      <NewAlteration />
    </Suspense>
  );
}
