"use client";

import Link from "next/link";
import { useEffect, useRef } from "react";
import { BellIcon } from "@/components/icons";
import { EmptyState, ListCard, ListRow, LoadState, PageHeader, SectionLabel } from "@/components/ui";
import { api } from "@/lib/api";
import { activityHref, activityTime } from "@/lib/activity";
import { useLoad } from "@/lib/tailoring";

/** What needs attention now, and recent order, payment and alteration updates. Opening it marks them seen. */
export default function NotificationsPage() {
  const feed = useLoad(() => api.admin.notifications(), []);
  const marked = useRef(false);
  useEffect(() => {
    if (feed.value && !marked.current) {
      marked.current = true;
      void api.admin.markNotificationsSeen().catch(() => undefined);
    }
  }, [feed.value]);
  const f = feed.value;

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader title="Notifications" backHref="/" badge={f && f.unread > 0 ? `${f.unread} new` : undefined} />
      {!f ? (
        <LoadState loading={feed.loading} error={feed.error} />
      ) : f.alerts.length === 0 && f.recent.length === 0 ? (
        <EmptyState icon={BellIcon} title="All quiet">
          Order updates, due dates and low-stock alerts will show up here.
        </EmptyState>
      ) : (
        <>
          {f.alerts.length > 0 && (
            <>
              <SectionLabel>Needs attention</SectionLabel>
              <ListCard>
                {f.alerts.map((a) => (
                  <ListRow key={a.kind} icon={BellIcon} title={a.title} href={a.href} />
                ))}
              </ListCard>
            </>
          )}
          <SectionLabel>Recent</SectionLabel>
          <ul className="divide-y divide-line rounded-2xl border border-line bg-panel text-sm">
            {f.recent.map((e, i) => {
              const href = activityHref(e);
              return (
                <li key={e.id} className="flex flex-wrap gap-x-3 px-4 py-3">
                  {i < f.unread && <span aria-label="new" className="mt-1.5 size-2 rounded-full bg-accent" />}
                  {href ? (
                    <Link href={href} className="flex-1 hover:underline">
                      {e.title}
                    </Link>
                  ) : (
                    <span className="flex-1">{e.title}</span>
                  )}
                  <span className="text-ink-faint">{activityTime(e.at)}</span>
                </li>
              );
            })}
          </ul>
        </>
      )}
    </section>
  );
}
