"use client";

import { useState } from "react";
import type { RequestStatus } from "@vaabhi/api-client";
import { InboxIcon, PlusIcon } from "@/components/icons";
import { RequestSheet, RequestSummary } from "@/components/request-form";
import { ErrorNote } from "@/components/tailoring";
import { ChipGroup, EmptyState, LoadState, PageHeader, buttonPrimary, buttonSecondary } from "@/components/ui";
import { api } from "@/lib/api";
import { useAction, useLoad } from "@/lib/tailoring";

/** Everyone's requests: pending first, approve or reject; raise one for someone. */
export default function StaffRequestsPage() {
  const [status, setStatus] = useState<RequestStatus | "ALL">("PENDING");
  const requests = useLoad(() => api.admin.staff.requests({ status: status === "ALL" ? undefined : status }), [status]);
  const staff = useLoad(() => api.admin.staff.list(), []);
  const [raising, setRaising] = useState(false);
  const action = useAction();

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader
        title="Requests"
        backHref="/staff/"
        actions={
          <button type="button" className={buttonPrimary} onClick={() => setRaising(true)} disabled={!staff.value}>
            <PlusIcon size={18} /> New request
          </button>
        }
      />
      <div className="mb-4">
        <ChipGroup
          label="Show requests"
          options={[
            { value: "PENDING", label: "Waiting" },
            { value: "APPROVED", label: "Approved" },
            { value: "REJECTED", label: "Rejected" },
            { value: "ALL", label: "All" },
          ]}
          value={status}
          onChange={setStatus}
        />
      </div>
      <ErrorNote>{requests.error ?? action.error}</ErrorNote>
      {!requests.value ? (
        <LoadState loading={requests.loading} error={null} />
      ) : requests.value.length === 0 ? (
        <EmptyState icon={InboxIcon} title="Nothing here">
          Requests staff raise from My requests appear here.
        </EmptyState>
      ) : (
        <ul className="divide-y divide-line rounded-2xl border border-line bg-panel">
          {requests.value.map((r) => (
            <li key={r.id} className="flex flex-wrap items-center gap-3 px-4 py-4">
              <div className="min-w-0 flex-1">
                <RequestSummary request={r} showName />
              </div>
              {r.status === "PENDING" && (
                <div className="flex gap-2">
                  <button
                    type="button"
                    className={buttonPrimary}
                    aria-label={`Approve ${r.staffName}'s request`}
                    disabled={action.busy}
                    onClick={async () => {
                      if (await action.run(() => api.admin.staff.decide(r.id, true, null))) requests.reload();
                    }}
                  >
                    Approve
                  </button>
                  <button
                    type="button"
                    className={`${buttonSecondary} text-bad`}
                    aria-label={`Reject ${r.staffName}'s request`}
                    disabled={action.busy}
                    onClick={async () => {
                      if (await action.run(() => api.admin.staff.decide(r.id, false, null))) requests.reload();
                    }}
                  >
                    Reject
                  </button>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
      {raising && staff.value && (
        <RequestSheet
          staff={staff.value}
          onClose={() => setRaising(false)}
          onSubmit={async (form) => {
            const saved = await api.admin.staff.createRequest(form);
            requests.reload();
            return saved;
          }}
        />
      )}
    </section>
  );
}
