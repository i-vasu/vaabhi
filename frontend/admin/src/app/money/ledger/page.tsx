"use client";

import Link from "next/link";
import { useState } from "react";
import type { LedgerEntry, LedgerKind } from "@vaabhi/api-client";
import { AmountLine, MonthPicker } from "@/components/money";
import { ErrorNote } from "@/components/tailoring";
import { ChipGroup, LoadState, PageHeader } from "@/components/ui";
import { api } from "@/lib/api";
import { LEDGER_KINDS, kindLabel, monthOf } from "@/lib/money";
import { rupees } from "@/lib/settings";
import { paymentModeLabel, today, useLoad } from "@/lib/tailoring";

const when = new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", hour: "numeric", minute: "2-digit" });

/** Where an entry came from, so it can be opened. */
export function sourceHref(entry: LedgerEntry): string | null {
  switch (entry.kind) {
    case "ORDER_PAYMENT":
    case "REFUND":
      return `/orders/view/?id=${entry.sourceId}`;
    case "ALTERATION_PAYMENT":
      return `/alterations/view/?id=${entry.sourceId}`;
    default:
      return null;
  }
}

/** Every payment, refund and expense in a month with a running balance. */
export default function LedgerPage() {
  const [month, setMonth] = useState(() => monthOf(today()));
  const [kind, setKind] = useState<LedgerKind | "ALL">("ALL");
  const ledger = useLoad(
    () => api.admin.money.ledger({ from: month.from, to: month.to, kind: kind === "ALL" ? undefined : kind }),
    [month.from, kind],
  );
  const l = ledger.value;

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader title="Ledger" backHref="/money/" actions={<MonthPicker value={month} onChange={setMonth} />} />
      <div className="mb-4">
        <ChipGroup
          label="Show entries"
          options={[{ value: "ALL" as const, label: "All" }, ...LEDGER_KINDS]}
          value={kind}
          onChange={setKind}
        />
      </div>
      <ErrorNote>{ledger.error}</ErrorNote>
      {!l ? (
        <LoadState loading={ledger.loading} error={null} />
      ) : (
        <>
          <dl aria-label="Ledger totals" className="mb-4 rounded-2xl border border-line bg-panel p-4 text-sm">
            <AmountLine label="Opening balance" value={rupees(l.openingRupees)} />
            <AmountLine label="Money in" value={`+ ${rupees(l.inRupees)}`} />
            <AmountLine label="Money out" value={`− ${rupees(l.outRupees)}`} />
            <AmountLine label="Closing balance" value={rupees(l.closingRupees)} strong />
          </dl>
          {l.entries.length === 0 ? (
            <p className="text-ink-soft">Nothing this month.</p>
          ) : (
            <div className="overflow-x-auto rounded-2xl border border-line bg-panel">
              <table className="w-full text-left text-sm">
                <thead className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
                  <tr>
                    <th className="px-4 py-2 font-medium">When</th>
                    <th className="px-4 py-2 font-medium">What</th>
                    <th className="px-4 py-2 font-medium">Mode</th>
                    <th className="px-4 py-2 text-right font-medium">Amount</th>
                    <th className="px-4 py-2 text-right font-medium">Balance</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {l.entries.map((entry, index) => {
                    const href = sourceHref(entry);
                    return (
                      <tr key={`${entry.kind}-${entry.sourceId}-${index}`}>
                        <td className="whitespace-nowrap px-4 py-3 text-ink-soft">{when.format(new Date(entry.at))}</td>
                        <td className="px-4 py-3">
                          {href ? (
                            <Link href={href} className="font-medium text-accent-ink underline">
                              {entry.reference}
                            </Link>
                          ) : (
                            <span className="font-medium">{entry.reference}</span>
                          )}
                          <span className="block text-ink-soft">
                            {kindLabel(entry.kind)}
                            {entry.party && ` · ${entry.party}`}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-ink-soft">{paymentModeLabel(entry.mode)}</td>
                        <td
                          className={`whitespace-nowrap px-4 py-3 text-right tabular-nums ${entry.amountRupees < 0 ? "text-warn" : "text-ok"}`}
                        >
                          {entry.amountRupees < 0 ? "− " : "+ "}
                          {rupees(Math.abs(entry.amountRupees))}
                        </td>
                        <td className="whitespace-nowrap px-4 py-3 text-right tabular-nums">
                          {rupees(entry.balanceRupees)}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </section>
  );
}
