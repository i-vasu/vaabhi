"use client";

import Link from "next/link";
import { useState } from "react";
import { PlusIcon, UsersIcon } from "@/components/icons";
import { ErrorNote, Pill } from "@/components/tailoring";
import { EmptyState, ListCard, ListRow, LoadState, PageHeader, ToggleRow, buttonPrimary } from "@/components/ui";
import { api } from "@/lib/api";
import { rupees } from "@/lib/settings";
import { PAY_TYPES, roleLabel } from "@/lib/staff";
import { useLoad } from "@/lib/tailoring";

export default function StaffPage() {
  const [showInactive, setShowInactive] = useState(false);
  const staff = useLoad(() => api.admin.staff.list(showInactive), [showInactive]);

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader
        title="Staff"
        actions={
          <Link href="/staff/new/" className={buttonPrimary}>
            <PlusIcon size={18} /> Add staff
          </Link>
        }
      />
      <div className="mb-4 rounded-2xl border border-line bg-panel">
        <ToggleRow title="Show people who left" checked={showInactive} onChange={setShowInactive} />
      </div>
      <ErrorNote>{staff.error}</ErrorNote>
      {!staff.value ? (
        <LoadState loading={staff.loading} error={null} />
      ) : staff.value.length === 0 ? (
        <EmptyState icon={UsersIcon} title="No staff yet" />
      ) : (
        <ListCard>
          {staff.value.map((member) => (
            <ListRow
              key={member.id}
              href={`/staff/view/?id=${member.id}`}
              title={
                <>
                  {member.name} {member.role !== "STAFF" && <Pill tone="accent">{roleLabel(member.role)}</Pill>}{" "}
                  {!member.active && <Pill tone="bad">Left</Pill>}
                </>
              }
              subtitle={[member.designation, PAY_TYPES.find((p) => p.value === member.payType)?.label]
                .filter(Boolean)
                .join(" · ")}
              trailing={
                member.payType === "PIECE"
                  ? "By the piece"
                  : `${rupees(member.salaryRupees)}${member.payType === "DAILY" ? " / day" : " / month"}`
              }
            />
          ))}
        </ListCard>
      )}
    </section>
  );
}
