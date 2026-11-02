"use client";

import { useState } from "react";
import { InboxIcon, PlusIcon } from "@/components/icons";
import { RequestSheet, RequestSummary } from "@/components/request-form";
import { EmptyState, LoadState, PageHeader, buttonPrimary } from "@/components/ui";
import { api } from "@/lib/api";
import { useLoad } from "@/lib/tailoring";

/** Your own leave, permission, off-day, comp-off and advance requests. */
export default function MyRequestsPage() {
  const requests = useLoad(() => api.admin.myRequests(), []);
  const [raising, setRaising] = useState(false);

  return (
    <section className="mx-auto max-w-2xl">
      <PageHeader
        title="My requests"
        backHref="/more/"
        actions={
          <button type="button" className={buttonPrimary} onClick={() => setRaising(true)}>
            <PlusIcon size={18} /> New request
          </button>
        }
      />
      {!requests.value ? (
        <LoadState loading={requests.loading} error={requests.error} />
      ) : requests.value.length === 0 ? (
        <EmptyState icon={InboxIcon} title="No requests yet">
          Ask for leave, a few hours off, an off day or a salary advance.
        </EmptyState>
      ) : (
        <ul className="divide-y divide-line rounded-2xl border border-line bg-panel">
          {requests.value.map((r) => (
            <li key={r.id} className="px-4 py-4">
              <RequestSummary request={r} />
            </li>
          ))}
        </ul>
      )}
      {raising && (
        <RequestSheet
          onClose={() => setRaising(false)}
          onSubmit={async (form) => {
            const saved = await api.admin.createMyRequest(form);
            requests.reload();
            return saved;
          }}
        />
      )}
    </section>
  );
}
