"use client";

import { StaffEditor } from "@/components/staff-form";
import { PageHeader } from "@/components/ui";

export default function NewStaffPage() {
  return (
    <section className="mx-auto max-w-2xl">
      <PageHeader title="Add staff" backHref="/staff/" />
      <StaffEditor />
    </section>
  );
}
