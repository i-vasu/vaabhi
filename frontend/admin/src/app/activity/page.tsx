"use client";

import Link from "next/link";
import { useState } from "react";
import { ActivityIcon } from "@/components/icons";
import { Pager } from "@/components/tailoring";
import { ChipGroup, EmptyState, LoadState, PageHeader } from "@/components/ui";
import { api } from "@/lib/api";
import { activityHref, activityTime } from "@/lib/activity";
import { useLoad } from "@/lib/tailoring";

const PAGE_SIZE = 50;
const KINDS = [
  { value: "", label: "Everything" },
  { value: "ORDER_PLACED", label: "Orders booked" },
  { value: "PAYMENT_RECEIVED", label: "Payments" },
  { value: "ORDER_DELIVERED", label: "Deliveries" },
  { value: "ALTERATION_BOOKED", label: "Alterations" },
  { value: "SETTINGS_CHANGED", label: "Settings" },
];

/** What happened in the shop and who did it. */
export default function ActivityPage() {
  const [kind, setKind] = useState("");
  const [page, setPage] = useState(1);
  const log = useLoad(() => api.admin.activity({ kind: kind || undefined, page, pageSize: PAGE_SIZE }), [kind, page]);
  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader title="Activity log" backHref="/more/" />
      <div className="mb-4">
        <ChipGroup
          label="Show"
          options={KINDS}
          value={kind}
          onChange={(next) => {
            setKind(next);
            setPage(1);
          }}
        />
      </div>
      {!log.value ? (
        <LoadState loading={log.loading} error={log.error} />
      ) : log.value.items.length === 0 ? (
        <EmptyState icon={ActivityIcon} title="Nothing yet" />
      ) : (
        <>
          <ul className="divide-y divide-line rounded-2xl border border-line bg-panel text-sm">
            {log.value.items.map((e) => {
              const href = activityHref(e);
              return (
                <li key={e.id} className="flex flex-wrap gap-x-3 px-4 py-3">
                  <span className="text-ink-faint">{activityTime(e.at)}</span>
                  {href ? (
                    <Link href={href} className="flex-1 underline-offset-2 hover:underline">
                      {e.title}
                    </Link>
                  ) : (
                    <span className="flex-1">{e.title}</span>
                  )}
                  {e.actor && <span className="text-ink-soft">{e.actor}</span>}
                </li>
              );
            })}
          </ul>
          <Pager
            total={log.value.total}
            page={page}
            pageSize={PAGE_SIZE}
            noun={["entry", "entries"]}
            onPage={setPage}
          />
        </>
      )}
    </section>
  );
}
