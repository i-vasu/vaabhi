"use client";

import { useState } from "react";
import type { Permission, RolePermissions } from "@vaabhi/api-client";
import { ErrorNote } from "@/components/tailoring";
import { ListCard, LoadState, PageHeader, SectionLabel, ToggleRow, buttonPrimary } from "@/components/ui";
import { api } from "@/lib/api";
import { PERMISSIONS, roleLabel } from "@/lib/staff";
import { useAction, useLoad } from "@/lib/tailoring";

/** What managers and staff may do (the owner may always do everything). Applies from their next sign-in. */
export default function RolesPage() {
  const roles = useLoad(() => api.admin.staff.roles(), []);
  return (
    <section className="mx-auto max-w-2xl">
      <PageHeader title="Roles & permissions" backHref="/staff/" />
      <p className="mb-4 text-ink-soft">
        Changes apply the next time each person signs in. Everyone can read settings and raise their own requests.
      </p>
      {!roles.value ? (
        <LoadState loading={roles.loading} error={roles.error} />
      ) : (
        roles.value.filter((r) => r.editable).map((role) => <RoleCard key={role.role} role={role} />)
      )}
    </section>
  );
}

function RoleCard({ role }: { role: RolePermissions }) {
  const [chosen, setChosen] = useState(new Set<Permission>(role.permissions));
  const [saved, setSaved] = useState(new Set<Permission>(role.permissions));
  const action = useAction();
  const dirty = chosen.size !== saved.size || [...chosen].some((p) => !saved.has(p));

  return (
    <div className="mb-6">
      <SectionLabel
        action={
          dirty ? (
            <button
              type="button"
              className={buttonPrimary}
              disabled={action.busy}
              onClick={async () => {
                const next = await action.run(() => api.admin.staff.saveRole(role.role, [...chosen]));
                if (next) setSaved(new Set(next.permissions));
              }}
            >
              {action.busy ? "Saving…" : `Save ${roleLabel(role.role).toLowerCase()}`}
            </button>
          ) : undefined
        }
      >
        {roleLabel(role.role)}
      </SectionLabel>
      <ListCard>
        {PERMISSIONS.map((p) => (
          <ToggleRow
            key={p.value}
            title={`${roleLabel(role.role)}: ${p.label}`}
            subtitle={p.description}
            checked={chosen.has(p.value)}
            onChange={(on) => {
              const next = new Set(chosen);
              if (on) next.add(p.value);
              else next.delete(p.value);
              setChosen(next);
            }}
          />
        ))}
      </ListCard>
      <ErrorNote>{action.error}</ErrorNote>
    </div>
  );
}
