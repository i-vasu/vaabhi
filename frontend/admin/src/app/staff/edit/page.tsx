"use client";

import { useSearchParams } from "next/navigation";
import { Suspense } from "react";
import { StaffEditor } from "@/components/staff-form";
import { ErrorNote } from "@/components/tailoring";
import { LoadState, PageHeader } from "@/components/ui";
import { api } from "@/lib/api";
import { idParam, useLoad } from "@/lib/tailoring";

function EditStaff({ id }: { id: number }) {
  const member = useLoad(() => api.admin.staff.get(id), [id]);
  return (
    <section className="mx-auto max-w-2xl">
      <PageHeader title="Edit staff" backHref={`/staff/view/?id=${id}`} />
      {member.value ? (
        <StaffEditor member={member.value} />
      ) : (
        <LoadState loading={member.loading} error={member.error} />
      )}
    </section>
  );
}

function Page() {
  const id = idParam(useSearchParams().get("id"));
  return id ? <EditStaff key={id} id={id} /> : <ErrorNote>No staff member selected.</ErrorNote>;
}

export default function EditStaffPage() {
  return (
    <Suspense fallback={<p className="text-ink-soft">Loading…</p>}>
      <Page />
    </Suspense>
  );
}
