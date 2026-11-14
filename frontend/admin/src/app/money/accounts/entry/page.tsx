"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { ErrorNote, Pill } from "@/components/tailoring";
import { LoadState, PageHeader, Sheet, buttonPrimary, buttonSecondary } from "@/components/ui";
import { sourceHref, sourceLabel, useIsOwner } from "@/lib/accounts";
import { api } from "@/lib/api";
import { rupees } from "@/lib/settings";
import { formatDate, idParam, useAction, useLoad } from "@/lib/tailoring";

function EntryView({ id }: { id: number }) {
  const owner = useIsOwner();
  const entry = useLoad(() => api.admin.accounts.entry(id), [id]);
  const action = useAction();
  const [confirming, setConfirming] = useState(false);
  const v = entry.value;

  if (!v) {
    return (
      <section className="mx-auto max-w-3xl">
        <PageHeader title="Journal entry" backHref="/money/accounts/journal/" />
        <LoadState loading={entry.loading} error={entry.error} />
      </section>
    );
  }

  const canReverse =
    owner && (v.sourceType === "MANUAL" || v.sourceType === "OPENING") && !v.reversedById && !v.reversesId;
  const href = v.sourceLink ? sourceHref(v.sourceLink) : null;
  const totalDebit = v.lines.reduce((sum, line) => sum + line.debitRupees, 0);
  const totalCredit = v.lines.reduce((sum, line) => sum + line.creditRupees, 0);

  async function reverse() {
    const reversed = await action.run(() => api.admin.accounts.reverse(id));
    if (reversed) {
      setConfirming(false);
      entry.reload();
    }
  }

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader
        title={v.number}
        backHref="/money/accounts/journal/"
        badge={formatDate(v.date)}
        actions={
          canReverse && (
            <button type="button" className={buttonSecondary} onClick={() => setConfirming(true)}>
              Reverse
            </button>
          )
        }
      />
      <div className="mb-4 rounded-2xl border border-line bg-panel p-4 text-sm">
        <p className="font-medium">{v.narration}</p>
        <p className="mt-1 text-ink-soft">
          {sourceLabel(v.sourceType)}
          {v.sourceLink &&
            (href ? (
              <>
                {" · "}
                <Link href={href} className="font-medium text-accent-ink underline">
                  {v.sourceLink.label}
                </Link>
              </>
            ) : (
              ` · ${v.sourceLink.label}`
            ))}
          {v.createdBy && ` · by ${v.createdBy}`}
        </p>
        {v.reversedById && (
          <p className="mt-2">
            <Pill tone="warn">Reversed</Pill>{" "}
            <Link href={`/money/accounts/entry/?id=${v.reversedById}`} className="text-accent-ink underline">
              See the reversal
            </Link>
          </p>
        )}
        {v.reversesId && (
          <p className="mt-2">
            <Link href={`/money/accounts/entry/?id=${v.reversesId}`} className="text-accent-ink underline">
              Reverses an earlier entry
            </Link>
          </p>
        )}
      </div>
      <div className="overflow-x-auto rounded-2xl border border-line bg-panel">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
            <tr>
              <th className="px-4 py-2 font-medium">Account</th>
              <th className="px-4 py-2 text-right font-medium">Debit</th>
              <th className="px-4 py-2 text-right font-medium">Credit</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-line">
            {v.lines.map((line, index) => (
              <tr key={index}>
                <td className="px-4 py-3">
                  <Link
                    href={`/money/accounts/ledger/?id=${line.accountId}`}
                    className="font-medium text-accent-ink underline"
                  >
                    {line.accountCode} · {line.accountName}
                  </Link>
                  {(line.partyName || line.memo) && (
                    <span className="block text-ink-soft">
                      {[line.partyName, line.memo].filter(Boolean).join(" · ")}
                    </span>
                  )}
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
          <tfoot className="border-t border-line font-medium">
            <tr>
              <td className="px-4 py-3">Total</td>
              <td className="px-4 py-3 text-right tabular-nums">{rupees(totalDebit)}</td>
              <td className="px-4 py-3 text-right tabular-nums">{rupees(totalCredit)}</td>
            </tr>
          </tfoot>
        </table>
      </div>
      <Sheet open={confirming} title="Reverse this entry?" onClose={() => setConfirming(false)}>
        <p className="mb-4 text-ink-soft">
          A new entry is posted with the debits and credits swapped. {v.number} stays in the journal.
        </p>
        <ErrorNote>{action.error}</ErrorNote>
        <div className="mt-4 flex gap-3">
          <button type="button" className={buttonPrimary} disabled={action.busy} onClick={reverse}>
            {action.busy ? "Reversing…" : "Reverse entry"}
          </button>
          <button type="button" className={buttonSecondary} onClick={() => setConfirming(false)}>
            Cancel
          </button>
        </div>
      </Sheet>
    </section>
  );
}

function Route() {
  const id = idParam(useSearchParams().get("id"));
  return id ? <EntryView key={id} id={id} /> : <ErrorNote>No entry selected.</ErrorNote>;
}

/** One journal entry: its lines, where it came from, and (for the owner) Reverse on manual entries. */
export default function EntryPage() {
  return (
    <Suspense fallback={<p className="text-ink-soft">Loading…</p>}>
      <Route />
    </Suspense>
  );
}
