"use client";

import Link from "next/link";
import { useState } from "react";
import { MonthPicker } from "@/components/money";
import { ErrorNote } from "@/components/tailoring";
import { LoadState, PageHeader, SectionLabel, SelectField } from "@/components/ui";
import { api } from "@/lib/api";
import { monthOf } from "@/lib/money";
import { rupees } from "@/lib/settings";
import { today, useLoad } from "@/lib/tailoring";

const when = new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short" });

/** Work stages each person finished in a month, at the price list's piece rates. */
export default function ProductionPage() {
  const [month, setMonth] = useState(() => monthOf(today()));
  const [staffId, setStaffId] = useState("");
  const staff = useLoad(() => api.admin.staff.list(), []);
  const ledger = useLoad(
    () =>
      api.admin.payroll.production({ from: month.from, to: month.to, staffId: staffId ? Number(staffId) : undefined }),
    [month.from, staffId],
  );
  const l = ledger.value;

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader title="Production ledger" backHref="/payroll/" />
      <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
        <MonthPicker value={month} onChange={setMonth} />
        <SelectField
          className="min-w-48"
          label="Person"
          options={[
            { value: "", label: "Everyone" },
            ...(staff.value ?? []).map((s) => ({ value: String(s.id), label: s.name })),
          ]}
          value={staffId}
          onChange={setStaffId}
        />
      </div>
      <p className="mb-4 text-sm text-ink-soft">
        Counted when someone given an item on the work board moves it to the next stage. Rates come from the{" "}
        <Link href="/price-list/" className="text-accent-ink underline">
          price list
        </Link>
        ; items not from the price list have no rate.
      </p>
      <ErrorNote>{ledger.error}</ErrorNote>
      {!l ? (
        <LoadState loading={ledger.loading} error={null} />
      ) : (
        <>
          {l.byStaff.length > 0 && (
            <ul className="mb-4 grid gap-3 sm:grid-cols-2">
              {l.byStaff.map((p) => (
                <li key={p.staffId} className="rounded-2xl border border-line bg-panel p-4">
                  <span className="block font-medium">{p.name}</span>
                  <span className="block text-sm text-ink-soft">
                    {p.pieces} pieces · {rupees(p.amountRupees)}
                  </span>
                </li>
              ))}
            </ul>
          )}
          <SectionLabel>{l.entries.length} stages finished</SectionLabel>
          {l.entries.length === 0 ? (
            <p className="text-ink-soft">Nothing finished this month.</p>
          ) : (
            <ul className="divide-y divide-line rounded-2xl border border-line bg-panel text-sm">
              {l.entries.map((e) => (
                <li key={`${e.itemId}-${e.stageId}`} className="flex flex-wrap items-center gap-x-3 gap-y-1 px-4 py-3">
                  <span className="text-ink-soft">{when.format(new Date(e.at))}</span>
                  <span className="font-medium">{e.staffName}</span>
                  <span>
                    {e.stageName} · {e.quantity > 1 ? `${e.quantity} × ` : ""}
                    {e.description}
                  </span>
                  <Link href={`/orders/view/?id=${e.orderId}`} className="text-accent-ink underline">
                    {e.orderNumber}
                  </Link>
                  <span className="ml-auto tabular-nums">
                    {e.rateRupees > 0 ? rupees(e.amountRupees) : <span className="text-ink-faint">no rate</span>}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </section>
  );
}
