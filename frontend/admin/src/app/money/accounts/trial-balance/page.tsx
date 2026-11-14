"use client";

import Link from "next/link";
import { useState } from "react";
import { ErrorNote } from "@/components/tailoring";
import { LoadState, PageHeader, TextField } from "@/components/ui";
import { ACCOUNT_TYPES } from "@/lib/accounts";
import { api } from "@/lib/api";
import { rupees } from "@/lib/settings";
import { today, useLoad } from "@/lib/tailoring";

/** Debits and credits of every account as at a date; they should be equal. */
export default function TrialBalancePage() {
  const [asOf, setAsOf] = useState(() => today());
  const trial = useLoad(() => api.admin.accounts.trialBalance(asOf), [asOf]);
  const v = trial.value;
  const difference = v ? Math.abs(v.totalDebitRupees - v.totalCreditRupees) : 0;

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader title="Trial balance" backHref="/money/accounts/" />
      <TextField
        className="mb-4"
        label="As at"
        type="date"
        value={asOf}
        onChange={(e) => e.target.value && setAsOf(e.target.value)}
      />
      <ErrorNote>{trial.error}</ErrorNote>
      {!v ? (
        <LoadState loading={trial.loading} error={null} />
      ) : (
        <>
          <p className={`mb-4 font-medium ${v.balanced ? "text-ok" : "text-bad"}`}>
            {v.balanced ? "Balanced ✓" : `Out by ${rupees(difference)}`}
          </p>
          {v.lines.length === 0 ? (
            <p className="text-ink-soft">No balances as at this date.</p>
          ) : (
            <div className="overflow-x-auto rounded-2xl border border-line bg-panel">
              <table className="w-full text-left text-sm">
                <thead className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
                  <tr>
                    <th className="px-4 py-2 font-medium">Account</th>
                    <th className="px-4 py-2 text-right font-medium">Debit</th>
                    <th className="px-4 py-2 text-right font-medium">Credit</th>
                  </tr>
                </thead>
                {ACCOUNT_TYPES.map((group) => {
                  const rows = v.lines.filter((line) => line.type === group.value);
                  if (rows.length === 0) return null;
                  return (
                    <tbody key={group.value} className="divide-y divide-line border-t border-line first:border-t-0">
                      {rows.map((line) => (
                        <tr key={line.accountId}>
                          <td className="px-4 py-3">
                            <Link
                              href={`/money/accounts/ledger/?id=${line.accountId}`}
                              className="text-accent-ink underline"
                            >
                              {line.code} · {line.name}
                            </Link>
                          </td>
                          <td className="whitespace-nowrap px-4 py-3 text-right tabular-nums">
                            {line.debitRupees > 0 ? rupees(line.debitRupees) : ""}
                          </td>
                          <td className="whitespace-nowrap px-4 py-3 text-right tabular-nums">
                            {line.creditRupees > 0 ? rupees(line.creditRupees) : ""}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  );
                })}
                <tfoot className="border-t border-line font-medium">
                  <tr>
                    <td className="px-4 py-3">Total</td>
                    <td className="px-4 py-3 text-right tabular-nums">{rupees(v.totalDebitRupees)}</td>
                    <td className="px-4 py-3 text-right tabular-nums">{rupees(v.totalCreditRupees)}</td>
                  </tr>
                </tfoot>
              </table>
            </div>
          )}
        </>
      )}
    </section>
  );
}
