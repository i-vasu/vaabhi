"use client";

import type { NotificationSettings } from "@vaabhi/api-client";
import { ListCard, LoadState, PageHeader, SectionLabel, ToggleRow } from "@/components/ui";
import { useSection } from "@/lib/settings";

const alerts: { key: keyof NotificationSettings; title: string; subtitle?: string }[] = [
  { key: "newOrder", title: "New order placed" },
  { key: "orderReady", title: "Order ready alerts" },
  { key: "orderDelivered", title: "Order delivered" },
  { key: "paymentReceived", title: "Payment received" },
  { key: "dataExported", title: "Data exported", subtitle: "Know when staff export shop data" },
  { key: "morningSchedule", title: "Morning schedule", subtitle: "Today's dues and deliveries, at opening time" },
  { key: "dailySummary", title: "Daily summary", subtitle: "Today's orders, dues and collections" },
];

/** Each switch saves at once. */
export default function NotificationSettingsPage() {
  const section = useSection("notifications");
  const n = section.value;

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader title="Notifications" backHref="/settings/" />
      <SectionLabel>Notifications</SectionLabel>
      <p className="mb-3 text-ink-soft">
        Applies to the whole shop — turning one off stops that alert on every device, including the owner&apos;s.
      </p>
      {!n ? (
        <LoadState loading={section.loading} error={section.loadError} />
      ) : (
        <ListCard>
          {alerts.map((alert) => (
            <ToggleRow
              key={alert.key}
              title={alert.title}
              subtitle={alert.subtitle}
              checked={n[alert.key]}
              disabled={section.saving}
              onChange={(checked) => void section.save({ ...n, [alert.key]: checked })}
            />
          ))}
        </ListCard>
      )}
      {section.error && (
        <p role="alert" className="mt-4 text-bad">
          {section.error}
        </p>
      )}
    </section>
  );
}
