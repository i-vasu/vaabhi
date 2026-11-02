"use client";

import type { ProductionStage } from "@vaabhi/api-client";
import { ArrowDownIcon, ArrowUpIcon, PlusIcon, TrashIcon } from "@/components/icons";
import { LoadState, PageHeader, SaveBar, buttonSecondary, inputClasses } from "@/components/ui";
import { api } from "@/lib/api";
import { useSaveable } from "@/lib/settings";

const kindNote: Record<ProductionStage["kind"], string> = {
  WORK: "Work stage",
  READY: "Order is ready for pickup",
  DELIVERED: "Order handed over",
};

/**
 * Work stages can be added, renamed, reordered and removed; Ready and Delivered always close the flow and can only
 * be renamed.
 */
export default function StagesPage() {
  const list = useSaveable(api.admin.settings.stages, api.admin.settings.saveStages);
  const stages = list.value;

  if (!stages) {
    return (
      <section className="mx-auto max-w-3xl">
        <PageHeader title="Production stages" backHref="/settings/orders/" />
        <LoadState loading={list.loading} error={list.loadError} />
      </section>
    );
  }

  const workCount = stages.filter((stage) => stage.kind === "WORK").length;
  const replace = (index: number, stage: ProductionStage) =>
    list.setValue(stages.map((s, i) => (i === index ? stage : s)));
  const move = (index: number, by: -1 | 1) => {
    const next = [...stages];
    [next[index], next[index + by]] = [next[index + by]!, next[index]!];
    list.setValue(next);
  };

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader title="Production stages" backHref="/settings/orders/" />
      <p className="mb-4 text-ink-soft">
        Every order item moves through these stages in order. Stages with orders in them keep their history when
        removed.
      </p>
      <ol className="flex flex-col gap-2">
        {stages.map((stage, index) => {
          const isWork = stage.kind === "WORK";
          return (
            <li
              key={stage.id ?? `new-${index}`}
              className="flex items-center gap-3 rounded-2xl border border-line bg-panel p-3"
            >
              <span className="grid size-8 shrink-0 place-items-center rounded-lg bg-accent-soft text-sm font-semibold text-accent-ink">
                {index + 1}
              </span>
              <div className="min-w-0 flex-1">
                <input
                  aria-label={`Stage ${index + 1} name`}
                  value={stage.name}
                  maxLength={60}
                  onChange={(event) => replace(index, { ...stage, name: event.target.value })}
                  className={`${inputClasses} py-2`}
                />
                <p className="mt-1 text-xs text-ink-faint">{kindNote[stage.kind]}</p>
              </div>
              {isWork && (
                <div className="flex shrink-0 gap-1">
                  <button
                    type="button"
                    aria-label={`Move ${stage.name} up`}
                    disabled={index === 0}
                    onClick={() => move(index, -1)}
                    className="rounded-lg p-2 text-ink-soft hover:bg-panel-2 disabled:opacity-30"
                  >
                    <ArrowUpIcon size={18} />
                  </button>
                  <button
                    type="button"
                    aria-label={`Move ${stage.name} down`}
                    disabled={index === workCount - 1}
                    onClick={() => move(index, 1)}
                    className="rounded-lg p-2 text-ink-soft hover:bg-panel-2 disabled:opacity-30"
                  >
                    <ArrowDownIcon size={18} />
                  </button>
                  <button
                    type="button"
                    aria-label={`Remove ${stage.name}`}
                    disabled={workCount === 1}
                    onClick={() => list.setValue(stages.filter((_, i) => i !== index))}
                    className="rounded-lg p-2 text-bad hover:bg-bad-soft disabled:opacity-30"
                  >
                    <TrashIcon size={18} />
                  </button>
                </div>
              )}
            </li>
          );
        })}
      </ol>
      <button
        type="button"
        className={`${buttonSecondary} mt-4`}
        onClick={() =>
          list.setValue([
            ...stages.slice(0, workCount),
            { id: null, name: "New stage", kind: "WORK" },
            ...stages.slice(workCount),
          ])
        }
      >
        <PlusIcon size={18} /> Add work stage
      </button>
      <SaveBar saving={list.saving} saved={list.saved} error={list.error} onSave={() => void list.save()} />
    </section>
  );
}
