"use client";

import { useState } from "react";
import { MonthPicker } from "@/components/money";
import { BankIcon, DocumentIcon, ReceiptIcon, ActivityIcon } from "@/components/icons";
import { ErrorNote } from "@/components/tailoring";
import { ListCard, ListRow, LoadState, PageHeader, SectionLabel, StatTile } from "@/components/ui";
import { api } from "@/lib/api";
import { monthOf } from "@/lib/money";
import { paymentModeLabel, today, useLoad } from "@/lib/tailoring";
import { rupees } from "@/lib/settings";

/** Money at a glance: what came in and went out this month, and what customers still owe. */
export default function MoneyPage() {
  const [month, setMonth] = useState(() => monthOf(today()));
  const ledger = useLoad(() => api.admin.money.ledger({ from: month.from, to: month.to }), [month.from]);
  const dues = useLoad(() => api.admin.money.outstanding(), []);
  const l = ledger.value;

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader title="Money" actions={<MonthPicker value={month} onChange={setMonth} />} />
      <ErrorNote>{ledger.error ?? dues.error}</ErrorNote>
      {!l ? (
        <LoadState loading={ledger.loading} error={null} />
      ) : (
        <>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            <StatTile label="Money in" value={rupees(l.inRupees)} tone="ok" />
            <StatTile label="Money out" value={rupees(l.outRupees)} tone={l.outRupees > 0 ? "warn" : "default"} />
            <StatTile
              label="Net"
              value={rupees(l.inRupees - l.outRupees)}
              tone={l.inRupees - l.outRupees < 0 ? "bad" : "default"}
            />
            <StatTile
              label="Customers owe"
              value={dues.value ? rupees(dues.value.totalRupees) : "…"}
              hint={
                dues.value && dues.value.handedOverRupees > 0
                  ? `${rupees(dues.value.handedOverRupees)} already handed over`
                  : undefined
              }
              tone={dues.value && dues.value.handedOverRupees > 0 ? "warn" : "default"}
            />
          </div>
          {l.byMode.length > 0 && (
            <>
              <SectionLabel>By payment mode</SectionLabel>
              <dl className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                {l.byMode.map((m) => (
                  <div key={m.mode} className="rounded-2xl border border-line bg-panel p-3">
                    <dt className="text-xs font-semibold uppercase tracking-[0.12em] text-ink-faint">
                      {paymentModeLabel(m.mode)}
                    </dt>
                    <dd className="mt-1 tabular-nums">
                      <span className="text-ok">+{rupees(m.inRupees)}</span>
                      {m.outRupees > 0 && <span className="block text-warn">−{rupees(m.outRupees)}</span>}
                    </dd>
                  </div>
                ))}
              </dl>
            </>
          )}
        </>
      )}
      <SectionLabel>Books</SectionLabel>
      <ListCard>
        <ListRow href="/money/dues/" icon={BankIcon} title="Dues" subtitle="Who owes what, oldest first" />
        <ListRow href="/money/invoices/" icon={DocumentIcon} title="Invoices" subtitle="Tax invoices and GST" />
        <ListRow
          href="/money/credit-notes/"
          icon={DocumentIcon}
          title="Credit notes"
          subtitle="Returns and corrections against invoices"
        />
        <ListRow
          href="/money/expenses/"
          icon={ReceiptIcon}
          title="Expenses"
          subtitle="Rent, salaries, material, bills"
        />
        <ListRow href="/money/ledger/" icon={ActivityIcon} title="Ledger" subtitle="Every rupee in and out" />
      </ListCard>
    </section>
  );
}
