"use client";

import { useState } from "react";
import { BankIcon } from "@/components/icons";
import { ErrorNote, Pill } from "@/components/tailoring";
import { EmptyState, ListCard, ListRow, LoadState, PageHeader, SegmentedControl, StatTile } from "@/components/ui";
import { api } from "@/lib/api";
import { rupees } from "@/lib/settings";
import { formatDate, formatPhone, useLoad } from "@/lib/tailoring";

type Show = "all" | "handedOver";

/** Everything customers owe, oldest first; garments already handed over are the money most at risk. */
export default function DuesPage() {
  const dues = useLoad(() => api.admin.money.outstanding(), []);
  const [show, setShow] = useState<Show>("all");
  const items = (dues.value?.items ?? []).filter((d) => show === "all" || d.handedOver);

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader title="Dues" backHref="/money/" />
      <ErrorNote>{dues.error}</ErrorNote>
      {!dues.value ? (
        <LoadState loading={dues.loading} error={null} />
      ) : (
        <>
          <div className="mb-4 grid grid-cols-2 gap-3">
            <StatTile label="Owed in all" value={rupees(dues.value.totalPaise)} />
            <StatTile
              label="Already handed over"
              value={rupees(dues.value.handedOverPaise)}
              tone={dues.value.handedOverPaise > 0 ? "warn" : "default"}
            />
          </div>
          <div className="mb-4">
            <SegmentedControl
              label="Show dues"
              options={[
                { value: "all", label: "All" },
                { value: "handedOver", label: "Handed over" },
              ]}
              value={show}
              onChange={setShow}
            />
          </div>
          {items.length === 0 ? (
            <EmptyState icon={BankIcon} title="Nothing owed">
              Every order and alteration is paid up.
            </EmptyState>
          ) : (
            <ListCard>
              {items.map((due) => (
                <ListRow
                  key={`${due.kind}-${due.id}`}
                  href={due.kind === "ORDER" ? `/orders/view/?id=${due.id}` : `/alterations/view/?id=${due.id}`}
                  title={
                    <>
                      {due.number} · {due.clientName}{" "}
                      {due.handedOver ? (
                        <Pill tone="warn">Handed over</Pill>
                      ) : due.daysLate > 0 ? (
                        <Pill tone="bad">{due.daysLate} days late</Pill>
                      ) : null}
                    </>
                  }
                  subtitle={`${formatPhone(due.clientPhone)} · ${due.kind === "ORDER" ? "due" : "ready by"} ${formatDate(due.date)}${due.invoiceNumber ? ` · ${due.invoiceNumber}` : ""}`}
                  trailing={
                    <span>
                      <span className="block font-medium text-warn">{rupees(due.duePaise)}</span>
                      <span className="block text-xs">of {rupees(due.totalPaise)}</span>
                    </span>
                  }
                />
              ))}
            </ListCard>
          )}
        </>
      )}
    </section>
  );
}
