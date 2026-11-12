"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { AmountLine } from "@/components/money";
import { ErrorNote } from "@/components/tailoring";
import { LoadState, PageHeader, TextField, buttonSecondary } from "@/components/ui";
import { downloadText, ledgerCsv } from "@/lib/accounts";
import { api } from "@/lib/api";
import { monthOf } from "@/lib/money";
import { rupees } from "@/lib/settings";
import { formatDate, idParam, today, useLoad } from "@/lib/tailoring";

function AccountLedger({ id }: { id: number }) {
  const [range, setRange] = useState(() => monthOf(today()));
  const ledger = useLoad(() => api.admin.accounts.ledger(id, range), [id, range.from, range.to]);
  const l = ledger.value;

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader
        title={l ? `${l.accountCode} · ${l.accountName}` : "Ledger"}
        backHref="/money/accounts/chart/"
        actions={
          l && (
            <button
              type="button"
              className={buttonSecondary}
              onClick={() => downloadText(`ledger-${l.accountCode}-${l.from}-to-${l.to}.csv`, ledgerCsv(l))}
            >
              Download CSV
            </button>
          )
        }
      />
      <div className="mb-4 grid grid-cols-2 gap-3">
        <TextField
          label="From"
          type="date"
          value={range.from}
          onChange={(e) => e.target.value && setRange((r) => ({ ...r, from: e.target.value }))}
        />
        <TextField
          label="To"
          type="date"
          value={range.to}
          onChange={(e) => e.target.value && setRange((r) => ({ ...r, to: e.target.value }))}
        />
      </div>
      <ErrorNote>{ledger.error}</ErrorNote>
      {!l ? (
        <LoadState loading={ledger.loading} error={null} />
      ) : (
        <>
          <dl aria-label="Ledger totals" className="mb-4 rounded-2xl border border-line bg-panel p-4 text-sm">
            <AmountLine label="Opening balance" value={rupees(l.openingRupees)} />
            <AmountLine label="Debits" value={rupees(l.totalDebitRupees)} />
            <AmountLine label="Credits" value={rupees(l.totalCreditRupees)} />
            <AmountLine label="Closing balance" value={rupees(l.closingRupees)} strong />
          </dl>
          {l.lines.length === 0 ? (
            <p className="text-ink-soft">Nothing in this period.</p>
          ) : (
            <div className="overflow-x-auto rounded-2xl border border-line bg-panel">
              <table className="w-full text-left text-sm">
                <thead className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
                  <tr>
                    <th className="px-4 py-2 font-medium">Date</th>
                    <th className="px-4 py-2 font-medium">Entry</th>
                    <th className="px-4 py-2 text-right font-medium">Debit</th>
                    <th className="px-4 py-2 text-right font-medium">Credit</th>
                    <th className="px-4 py-2 text-right font-medium">Balance</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {l.lines.map((line, index) => (
                    <tr key={`${line.entryId}-${index}`}>
                      <td className="whitespace-nowrap px-4 py-3 text-ink-soft">{formatDate(line.date)}</td>
                      <td className="px-4 py-3">
                        <Link
                          href={`/money/accounts/entry/?id=${line.entryId}`}
                          className="font-medium text-accent-ink underline"
                        >
                          {line.number}
                        </Link>
                        <span className="block text-ink-soft">
                          {line.narration}
                          {line.partyName && ` · ${line.partyName}`}
                        </span>
                      </td>
                      <td className="whitespace-nowrap px-4 py-3 text-right tabular-nums">
                        {line.debitRupees > 0 ? rupees(line.debitRupees) : ""}
                      </td>
                      <td className="whitespace-nowrap px-4 py-3 text-right tabular-nums">
                        {line.creditRupees > 0 ? rupees(line.creditRupees) : ""}
                      </td>
                      <td className="whitespace-nowrap px-4 py-3 text-right tabular-nums">
                        {rupees(line.balanceRupees)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </section>
  );
}

function LedgerRoute() {
  const id = idParam(useSearchParams().get("id"));
  return id === null ? (
    <section className="mx-auto max-w-3xl">
      <PageHeader title="Ledger" backHref="/money/accounts/chart/" />
      <LoadState loading={false} error="That account link is not valid." />
    </section>
  ) : (
    <AccountLedger id={id} />
  );
}

/** One account's statement for a date range, with running balance and a CSV download. */
export default function AccountLedgerPage() {
  return (
    <Suspense fallback={<p className="text-ink-soft">Loading…</p>}>
      <LedgerRoute />
    </Suspense>
  );
}
