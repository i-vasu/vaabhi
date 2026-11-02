"use client";

import { useState } from "react";
import type { ProductionStage, WorkshopStaff } from "@vaabhi/api-client";
import { ErrorNote } from "@/components/tailoring";
import { EmptyState, LoadState, PageHeader, buttonSecondary } from "@/components/ui";
import { UsersIcon } from "@/components/icons";
import { cn } from "@vaabhi/ui";
import { api } from "@/lib/api";
import { useAction, useLoad } from "@/lib/tailoring";

/** Who may be given which work stage. Nobody ticked means the person can take any stage. */
export default function WorkshopStaffPage() {
  const setup = useLoad(() => Promise.all([api.admin.workshop.staff(), api.admin.settings.stages()]), []);

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader title="Who works which stage" backHref="/workshop/" />
      <p className="mb-4 text-ink-soft">
        Tick the stages each person does. Someone with nothing ticked can be given any stage. Adding staff comes with
        the Staff pages.
      </p>
      {!setup.value ? (
        <LoadState loading={setup.loading} error={setup.error} />
      ) : setup.value[0].length === 0 ? (
        <EmptyState icon={UsersIcon} title="No staff yet" />
      ) : (
        <ul className="flex flex-col gap-3">
          {setup.value[0].map((person) => (
            <StaffStagesRow
              key={person.id}
              person={person}
              stages={setup.value![1].filter((stage) => stage.kind === "WORK")}
            />
          ))}
        </ul>
      )}
    </section>
  );
}

function StaffStagesRow({ person, stages }: { person: WorkshopStaff; stages: ProductionStage[] }) {
  const [chosen, setChosen] = useState(new Set(person.stageIds));
  const [saved, setSaved] = useState(new Set(person.stageIds));
  const action = useAction();
  const dirty = chosen.size !== saved.size || [...chosen].some((id) => !saved.has(id));

  const toggle = (id: number) => {
    const next = new Set(chosen);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    setChosen(next);
  };

  return (
    <li className="rounded-2xl border border-line bg-panel p-4">
      <div className="mb-3 flex items-center justify-between gap-3">
        <span>
          <span className="block font-medium">{person.name}</span>
          <span className="block text-sm text-ink-soft">
            {chosen.size === 0 ? "Any stage" : `${chosen.size} of ${stages.length} stages`} · {person.workingOn} items
            now
          </span>
        </span>
        {dirty && (
          <button
            type="button"
            className={buttonSecondary}
            disabled={action.busy}
            onClick={async () => {
              const updated = await action.run(() => api.admin.workshop.saveStages(person.id, [...chosen]));
              if (updated) setSaved(new Set(updated.stageIds));
            }}
          >
            {action.busy ? "Saving…" : "Save"}
          </button>
        )}
      </div>
      <div role="group" aria-label={`Stages for ${person.name}`} className="flex flex-wrap gap-2">
        {stages.map((stage) => {
          const on = chosen.has(stage.id!);
          return (
            <button
              key={stage.id}
              type="button"
              aria-pressed={on}
              onClick={() => toggle(stage.id!)}
              className={cn(
                "rounded-full border px-4 py-1.5 text-sm",
                on ? "border-accent/60 bg-accent-soft text-accent-ink" : "border-line-strong text-ink-soft",
              )}
            >
              {stage.name}
            </button>
          );
        })}
      </div>
      <ErrorNote>{action.error}</ErrorNote>
    </li>
  );
}
