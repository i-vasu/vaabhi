"use client";

import { useSearchParams } from "next/navigation";
import { Suspense } from "react";
import { ClientEditor, formFromClient } from "@/components/client-form";
import { ErrorNote } from "@/components/tailoring";
import { LoadState, PageHeader } from "@/components/ui";
import { api } from "@/lib/api";
import { idParam, useLoad } from "@/lib/tailoring";

/** /customers/edit/?id=42 — a query string, because the static export cannot pre-render ids. */
function EditCustomer({ id }: { id: number }) {
  const client = useLoad(() => api.admin.clients.get(id), [id]);
  return (
    <section className="mx-auto max-w-2xl">
      <PageHeader title="Edit customer" backHref={`/customers/view/?id=${id}`} />
      {client.value ? (
        <ClientEditor id={id} initial={formFromClient(client.value)} />
      ) : (
        <LoadState loading={client.loading} error={client.error} />
      )}
    </section>
  );
}

function Page() {
  const id = idParam(useSearchParams().get("id"));
  return id ? <EditCustomer key={id} id={id} /> : <ErrorNote>No customer selected.</ErrorNote>;
}

export default function EditCustomerPage() {
  return (
    <Suspense fallback={<p className="text-ink-soft">Loading…</p>}>
      <Page />
    </Suspense>
  );
}
