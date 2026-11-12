"use client";

import Link from "next/link";
import { useState } from "react";
import type { Account, OpeningBalances } from "@vaabhi/api-client";
import { ErrorNote } from "@/components/tailoring";
import { LoadState, PageHeader, TextField, buttonPrimary } from "@/components/ui";
import { paiseLabel, toPaise, useIsOwner } from "@/lib/accounts";
import { api } from "@/lib/api";
import { formatDate, useAction, useLoad } from "@/lib/tailoring";

type Amounts = Record<number, { debit: string; credit: string }>;

const amountText = (rupees: number | undefined) => (rupees ? String(rupees) : "");

function OpeningForm({
  accounts,
  opening,
  owner,
  onSaved,
}: {
  accounts: Account[];
  opening: OpeningBalances;
  owner: boolean;
  onSaved: () => void;
}) {
  const [amounts, setAmounts] = useState<Amounts>(() =>
    Object.fromEntries(
      accounts.map((a) => {
        const line = opening.lines.find((l) => l.accountId === a.id);
        return [a.id, { debit: amountText(line?.debitRupees), credit: amountText(line?.creditRupees) }];
      }),
    ),
  );
  const [saved, setSaved] = useState(false);
  const action = useAction();

  const parsed = accounts.map((a) => ({
    account: a,
    debit: toPaise(amounts[a.id]?.debit ?? ""),
    credit: toPaise(amounts[a.id]?.credit ?? ""),
  }));
  const invalid = parsed.some((p) => p.debit === null || p.credit === null);
  const debitTotal = parsed.reduce((sum, p) => sum + (p.debit ?? 0), 0);
  const creditTotal = parsed.reduce((sum, p) => sum + (p.credit ?? 0), 0);
  const remainder = Math.abs(debitTotal - creditTotal);

  function change(id: number, patch: Partial<{ debit: string; credit: string }>) {
    setSaved(false);
    setAmounts((current) => ({
      ...current,
      [id]: {
        debit: patch.debit ?? current[id]?.debit ?? "",
        credit: patch.credit ?? current[id]?.credit ?? "",
      },
    }));
  }

  async function save() {
    setSaved(false);
    const result = await action.run(() =>
      api.admin.accounts.saveOpening({
        lines: parsed
          .filter((p) => (p.debit ?? 0) > 0 || (p.credit ?? 0) > 0)
          .map((p) => ({
            accountId: p.account.id,
            debitRupees: (p.debit ?? 0) / 100,
            creditRupees: (p.credit ?? 0) / 100,
          })),
      }),
    );
    if (result) {
      setSaved(true);
      onSaved();
    }
  }

  return (
    <>
      <div className="space-y-3">
        {accounts.map((a) => (
          <div key={a.id} className="rounded-2xl border border-line bg-panel p-4">
            <p className="mb-2 font-medium">
              {a.code} · {a.name}
            </p>
            <div className="grid grid-cols-2 gap-3">
              <TextField
                label={`Debit ${a.code}`}
                inputMode="decimal"
                value={amounts[a.id]?.debit ?? ""}
                readOnly={!owner}
                onChange={(e) => change(a.id, { debit: e.target.value })}
              />
              <TextField
                label={`Credit ${a.code}`}
                inputMode="decimal"
                value={amounts[a.id]?.credit ?? ""}
                readOnly={!owner}
                onChange={(e) => change(a.id, { credit: e.target.value })}
              />
            </div>
          </div>
        ))}
      </div>
      <p className="mt-4 font-medium" role="status">
        {remainder === 0 ? "No difference" : `${paiseLabel(remainder)} will go to Opening balance equity`}
      </p>
      {owner && (
        <div className="mt-4 flex flex-wrap items-center gap-3">
          <button type="button" className={buttonPrimary} disabled={action.busy || invalid} onClick={save}>
            {action.busy ? "Saving…" : "Save"}
          </button>
          <span role="status" className="text-sm text-ok">
            {saved ? "Saved" : ""}
          </span>
        </div>
      )}
      <ErrorNote>{action.error}</ErrorNote>
    </>
  );
}

/** What each balance-sheet account held on the day the books start. */
export default function OpeningBalancesPage() {
  const owner = useIsOwner();
  const chart = useLoad(() => api.admin.accounts.chart(), []);
  const opening = useLoad(() => api.admin.accounts.opening(), []);
  const o = opening.value;
  // A turned-off account stays in the list while the saved entry holds an amount on it.
  const held = new Set((o?.lines ?? []).filter((l) => l.debitRupees || l.creditRupees).map((l) => l.accountId));
  const accounts = (chart.value ?? []).filter(
    (a) =>
      (a.active || held.has(a.id)) &&
      a.systemKey !== "OPENING_BALANCE" &&
      (a.type === "ASSET" || a.type === "LIABILITY" || a.type === "EQUITY"),
  );

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader title="Opening balances" backHref="/money/accounts/" />
      <ErrorNote>{chart.error ?? opening.error}</ErrorNote>
      {!o || !chart.value ? (
        <LoadState loading={chart.loading || opening.loading} error={null} />
      ) : !o.startDate ? (
        <p className="rounded-2xl border border-line bg-panel p-4">
          Set the books start date first{" "}
          <Link href="/money/accounts/" className="font-medium text-accent-ink underline">
            Go to Accounts
          </Link>
        </p>
      ) : (
        <>
          <p className="mb-4 text-sm text-ink-soft">
            As at {formatDate(o.startDate)}
            {o.entryNumber ? ` · entry ${o.entryNumber}` : ""}
          </p>
          {o.preStartOrders.length > 0 && (
            <div className="mb-4 rounded-2xl border border-line bg-panel p-4">
              <p className="font-medium">Orders with money received before the books start</p>
              <p className="mt-1 text-sm text-ink-soft">
                Include what these owe, and the advances they hold, in the overall Debtors and advances amounts below.
              </p>
              <ul className="mt-2 list-inside list-disc text-sm">
                {o.preStartOrders.map((number) => (
                  <li key={number}>{number}</li>
                ))}
              </ul>
            </div>
          )}
          <OpeningForm
            key={`${accounts.length}-${o.entryId ?? "new"}`}
            accounts={accounts}
            opening={o}
            owner={owner}
            onSaved={opening.reload}
          />
        </>
      )}
    </section>
  );
}
