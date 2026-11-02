"use client";

import { ClientEditor, emptyClientForm } from "@/components/client-form";
import { PageHeader } from "@/components/ui";

export default function NewCustomerPage() {
  return (
    <section className="mx-auto max-w-2xl">
      <PageHeader title="Add customer" backHref="/customers/" />
      <ClientEditor initial={emptyClientForm} />
    </section>
  );
}
