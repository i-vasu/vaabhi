"use client";

import { useState } from "react";
import { ErrorNote } from "@/components/tailoring";
import { LoadState, PageHeader, SelectField, TextField, buttonPrimary, buttonSecondary } from "@/components/ui";
import { paiseLabel, toPaise, useIsOwner } from "@/lib/accounts";
import { api } from "@/lib/api";
import { orNull } from "@/lib/settings";
import { today, useAction, useLoad } from "@/lib/tailoring";

type Line = { accountId: string; debit: string; credit: string; memo: string };

const blankLine = (): Line => ({ accountId: "", debit: "", credit: "", memo: "" });

/** Post a journal entry by hand: every line is a debit or a credit, and the two sides must match. */
export default function ManualJournalPage() {
  const owner = useIsOwner();
  const chart = useLoad(() => api.admin.accounts.chart(), []);
  const [date, setDate] = useState(today());
  const [narration, setNarration] = useState("");
  const [lines, setLines] = useState<Line[]>([blankLine(), blankLine()]);
  const [posted, setPosted] = useState<string | null>(null);
  const action = useAction();

  if (!owner) {
    return (
      <section className="mx-auto max-w-3xl">
        <PageHeader title="Manual journal" backHref="/money/accounts/" />
        <p className="rounded-2xl border border-line bg-panel p-4">Only the owner can post journals</p>
      </section>
    );
  }

  const options = [
    { value: "", label: "Choose an account" },
    ...(chart.value ?? [])
      .filter((a) => a.active)
      .map((a) => ({ value: String(a.id), label: `${a.code} · ${a.name}` })),
  ];

  const amounts = lines.map((l) => ({ debit: toPaise(l.debit), credit: toPaise(l.credit) }));
  const invalidAmount = amounts.some((a) => a.debit === null || a.credit === null);
  const debitTotal = amounts.reduce((sum, a) => sum + (a.debit ?? 0), 0);
  const creditTotal = amounts.reduce((sum, a) => sum + (a.credit ?? 0), 0);
  const difference = Math.abs(debitTotal - creditTotal);
  const oneSideEach = lines.every((l, i) => {
    const debit = amounts[i]?.debit ?? 0;
    const credit = amounts[i]?.credit ?? 0;
    return !!l.accountId && debit > 0 !== credit > 0;
  });
  const canSave =
    !action.busy &&
    narration.trim() !== "" &&
    !!date &&
    lines.length >= 2 &&
    !invalidAmount &&
    oneSideEach &&
    difference === 0;

  function change(index: number, patch: Partial<Line>) {
    setPosted(null);
    setLines((current) => current.map((l, i) => (i === index ? { ...l, ...patch } : l)));
  }

  async function save() {
    const entry = await action.run(() =>
      api.admin.accounts.postManual({
        date,
        narration: narration.trim(),
        lines: lines.map((l, i) => ({
          accountId: Number(l.accountId),
          partyId: null,
          debitRupees: (amounts[i]?.debit ?? 0) / 100,
          creditRupees: (amounts[i]?.credit ?? 0) / 100,
          memo: orNull(l.memo),
        })),
      }),
    );
    if (entry) {
      setPosted(entry.number);
      setNarration("");
      setLines([blankLine(), blankLine()]);
    }
  }

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader title="Manual journal" backHref="/money/accounts/" />
      <ErrorNote>{chart.error}</ErrorNote>
      <LoadState loading={chart.loading && !chart.value} error={null} />
      <div className="grid gap-3 sm:grid-cols-2">
        <TextField label="Date" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
        <TextField label="Narration" value={narration} onChange={(e) => setNarration(e.target.value)} />
      </div>
      <div className="mt-4 space-y-3">
        {lines.map((line, index) => (
          <div key={index} className="rounded-2xl border border-line bg-panel p-4">
            <div className="grid gap-3 sm:grid-cols-2">
              <SelectField
                label={`Account ${index + 1}`}
                options={options}
                value={line.accountId}
                onChange={(value) => change(index, { accountId: value })}
              />
              <TextField
                label={`Memo ${index + 1}`}
                value={line.memo}
                onChange={(e) => change(index, { memo: e.target.value })}
              />
              <TextField
                label={`Debit ${index + 1}`}
                inputMode="decimal"
                value={line.debit}
                onChange={(e) => change(index, { debit: e.target.value })}
              />
              <TextField
                label={`Credit ${index + 1}`}
                inputMode="decimal"
                value={line.credit}
                onChange={(e) => change(index, { credit: e.target.value })}
              />
            </div>
            {lines.length > 2 && (
              <button
                type="button"
                className="mt-3 text-sm text-bad underline"
                onClick={() => setLines((current) => current.filter((_, i) => i !== index))}
              >
                Remove line {index + 1}
              </button>
            )}
          </div>
        ))}
      </div>
      <div className="mt-3">
        <button
          type="button"
          className={buttonSecondary}
          onClick={() => setLines((current) => [...current, blankLine()])}
        >
          Add line
        </button>
      </div>
      <p className={`mt-4 font-medium ${difference === 0 ? "text-ok" : "text-bad"}`} role="status">
        Difference {paiseLabel(difference)}
      </p>
      <div className="mt-4 flex flex-wrap items-center gap-3">
        <button type="button" className={buttonPrimary} disabled={!canSave} onClick={save}>
          {action.busy ? "Saving…" : "Save"}
        </button>
        {posted && <span className="text-sm text-ok">Posted {posted}</span>}
      </div>
      <ErrorNote>{action.error}</ErrorNote>
    </section>
  );
}
