"use client";

import { ActivityIcon } from "@/components/icons";
import { AgeingBars, MonthBars } from "@/components/charts";
import { ErrorNote } from "@/components/tailoring";
import { ListCard, ListRow, LoadState, PageHeader, SectionLabel, StatTile } from "@/components/ui";
import { api } from "@/lib/api";
import { rupees } from "@/lib/settings";
import { useLoad } from "@/lib/tailoring";

/** The reports hub: this month's figures, six months of money, dues ageing, and every report. */
export default function ReportsPage() {
  const overview = useLoad(() => api.admin.reports.overview(), []);
  const reports = useLoad(() => api.admin.reports.list(), []);
  const o = overview.value;

  return (
    <section className="mx-auto max-w-4xl">
      <PageHeader title="Reports" />
      <ErrorNote>{overview.error ?? reports.error}</ErrorNote>
      {!o ? (
        <LoadState loading={overview.loading} error={null} />
      ) : (
        <>
          <SectionLabel>This month</SectionLabel>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            <StatTile label="Booked" value={rupees(o.bookedPaise)} hint={`${o.ordersBooked} orders`} />
            <StatTile label="Collected" value={rupees(o.collectedPaise)} tone="ok" />
            <StatTile label="Spent" value={rupees(o.expensesPaise)} />
            <StatTile
              label="Net"
              value={rupees(o.collectedPaise - o.expensesPaise)}
              tone={o.collectedPaise - o.expensesPaise < 0 ? "bad" : "default"}
            />
            <StatTile label="Delivered" value={o.ordersDelivered} />
            <StatTile label="Average order" value={rupees(o.averageOrderPaise)} />
            <StatTile label="New customers" value={o.newCustomers} />
            <StatTile
              label="Owed to the shop"
              value={rupees(o.outstandingPaise)}
              tone={o.outstandingPaise > 0 ? "warn" : "default"}
            />
          </div>
          <div className="mt-6 grid gap-4 lg:grid-cols-2">
            <MonthBars months={o.months} />
            <AgeingBars buckets={o.ageing} />
          </div>
        </>
      )}
      <SectionLabel>All reports</SectionLabel>
      {reports.value && (
        <ListCard>
          {reports.value.map((r) => (
            <ListRow
              key={r.name}
              icon={ActivityIcon}
              href={`/reports/view/?name=${r.name}`}
              title={r.title}
              subtitle={r.description}
            />
          ))}
        </ListCard>
      )}
    </section>
  );
}
