"use client";

import Link from "next/link";
import { BoardIcon, UsersIcon } from "@/components/icons";
import { ErrorNote } from "@/components/tailoring";
import {
  ListCard,
  ListRow,
  LoadState,
  PageHeader,
  SectionLabel,
  StatTile,
  buttonPrimary,
  buttonSecondary,
} from "@/components/ui";
import { api } from "@/lib/api";
import { useLoad } from "@/lib/tailoring";

/** The workshop at a glance: what is being made, what is late, and how the work is spread. */
export default function WorkshopPage() {
  const overview = useLoad(() => api.admin.workshop.overview(), []);
  const o = overview.value;

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader
        title="Workshop"
        actions={
          <Link href="/workshop/board/" className={buttonPrimary}>
            <BoardIcon size={18} /> Work board
          </Link>
        }
      />
      <ErrorNote>{overview.error}</ErrorNote>
      {!o ? (
        <LoadState loading={overview.loading} error={null} />
      ) : (
        <>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
            <Link href="/workshop/board/">
              <StatTile label="In production" value={o.inProduction} hint={`${o.unassigned} not given to anyone`} />
            </Link>
            <Link href="/orders/?filter=READY">
              <StatTile label="Ready for pickup" value={o.readyItems} tone={o.readyItems > 0 ? "ok" : "default"} />
            </Link>
            <Link href="/orders/?filter=DUE_TODAY">
              <StatTile label="Due today" value={o.dueToday} tone={o.dueToday > 0 ? "accent" : "default"} />
            </Link>
            <Link href="/orders/?filter=OVERDUE">
              <StatTile
                label="Overdue"
                value={o.overdue}
                tone={o.overdue > 0 ? "bad" : "default"}
                surface={o.overdue > 0}
              />
            </Link>
            <StatTile label="Trials today" value={o.trialsToday} />
          </div>

          <SectionLabel>Stage load</SectionLabel>
          <StageBars load={o.stageLoad} />

          <SectionLabel
            action={
              <Link href="/workshop/staff/" className="text-sm text-accent-ink underline">
                Who works which stage
              </Link>
            }
          >
            Staff
          </SectionLabel>
          {o.staffLoad.length === 0 ? (
            <p className="text-ink-soft">No staff yet.</p>
          ) : (
            <ListCard>
              {o.staffLoad.map((person) => (
                <ListRow
                  key={person.id}
                  icon={UsersIcon}
                  href={`/workshop/board/?staffId=${person.id}`}
                  title={person.name}
                  subtitle={person.stageIds.length === 0 ? "All stages" : `${person.stageIds.length} stages`}
                  trailing={`${person.workingOn} ${person.workingOn === 1 ? "item" : "items"}`}
                />
              ))}
            </ListCard>
          )}
          <Link href="/workshop/board/?unassigned=1" className={`${buttonSecondary} mt-4`}>
            Give out unassigned work
          </Link>
        </>
      )}
    </section>
  );
}

/** One bar per stage, scaled to the busiest; the overdue share is shown in the warning colour. */
function StageBars({
  load,
}: {
  load: { stage: { id: number | null; name: string }; items: number; overdue: number }[];
}) {
  const most = Math.max(1, ...load.map((l) => l.items));
  return (
    <ul className="flex flex-col gap-3 rounded-2xl border border-line bg-panel p-4">
      {load.map((l) => (
        <li key={l.stage.id}>
          <div className="mb-1 flex justify-between text-sm">
            <span>{l.stage.name}</span>
            <span className="tabular-nums text-ink-soft">
              {l.items}
              {l.overdue > 0 && <span className="text-warn"> · {l.overdue} late</span>}
            </span>
          </div>
          <div
            className="flex h-2 overflow-hidden rounded-full bg-panel-2"
            role="meter"
            aria-label={`${l.stage.name}: ${l.items} items`}
            aria-valuemin={0}
            aria-valuemax={most}
            aria-valuenow={l.items}
          >
            <span className="bg-warn" style={{ width: `${(l.overdue / most) * 100}%` }} />
            <span className="bg-accent" style={{ width: `${((l.items - l.overdue) / most) * 100}%` }} />
          </div>
        </li>
      ))}
    </ul>
  );
}
