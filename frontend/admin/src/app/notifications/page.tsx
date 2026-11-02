"use client";

import { BellIcon } from "@/components/icons";
import { EmptyState, PageHeader } from "@/components/ui";

/** Shop alerts. The feed fills in once orders, deliveries and stock send updates (later phases). */
export default function NotificationsPage() {
  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader title="Notifications" backHref="/" />
      <EmptyState icon={BellIcon} title="No notifications yet">
        Order updates, due dates and low-stock alerts will show up here.
      </EmptyState>
    </section>
  );
}
