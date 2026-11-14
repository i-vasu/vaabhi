"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import type { ClosingStockLine, YearEnd } from "@vaabhi/api-client";
import { DownloadIcon, TrashIcon } from "@/components/icons";
import { ErrorNote } from "@/components/tailoring";
import {
  ListCard,
  LoadState,
  PageHeader,
  SectionLabel,
  SelectField,
  Sheet,
  StatTile,
  TextAreaField,
  TextField,
  buttonPrimary,
  buttonSecondary,
} from "@/components/ui";
import { fyLabel, fyRange, toPaise, useIsOwner } from "@/lib/accounts";
import { api } from "@/lib/api";
import { describeError, orNull, rupees } from "@/lib/settings";
import { formatDate, today, useAction, useLoad } from "@/lib/tailoring";

/** "2026-27" for the year that starts in April 2026. */
function labelFor(startYear: number): string {
  return `${startYear}-${String((startYear + 1) % 100).padStart(2, "0")}`;
}

/** The latest year that ended on 31 March before today, or the year today is in. */
function defaultFy(): string {
  let start = Number(fyRange(today()).from.slice(0, 4));
  while (`${start + 1}-03-31` >= today()) start -= 1;
  return labelFor(start);
}

/** The current financial year and the four before it. */
function fyOptions(): { value: string; label: string }[] {
  const current = Number(fyRange(today()).from.slice(0, 4));
  return [0, 1, 2, 3, 4].map((back) => {
    const start = current - back;
    return { value: labelFor(start), label: fyLabel(`${start}-04-01`) };
  });
}

/** Up to two decimals, not negative; null when it is not a quantity. */
function parseQuantity(text: string): number | null {
  const cleaned = text.trim();
  return /^\d+(\.\d{1,2})?$/.test(cleaned) ? Number(cleaned) : null;
}

type StockRow = { stockItemId: number; name: string; unit: string; quantity: string; rate: string };

const stockRow = (line: ClosingStockLine): StockRow => ({
  stockItemId: line.stockItemId,
  name: line.name,
  unit: line.unit,
  quantity: String(line.quantity),
  rate: String(line.rateRupees),
});

const cellInput = (locked: boolean) =>
  `w-28 rounded-lg border px-2 py-1.5 text-right tabular-nums text-ink outline-none transition-colors ${
    locked ? "border-transparent bg-transparent" : "border-line-strong bg-field focus:border-accent"
  }`;

function StockSection({
  fy,
  data,
  owner,
  onSaved,
}: {
  fy: string;
  data: YearEnd;
  owner: boolean;
  onSaved: (next: YearEnd) => void;
}) {
  const locked = data.closed || !owner;
  const [rows, setRows] = useState<StockRow[]>(() => data.stock.map(stockRow));
  const [finishedGoods, setFinishedGoods] = useState(() => String(data.finishedGoodsRupees));
  const [note, setNote] = useState(data.stockNote ?? "");
  const action = useAction();
  const finishedPaise = toPaise(finishedGoods);
  const valid =
    finishedPaise !== null && rows.every((row) => parseQuantity(row.quantity) !== null && toPaise(row.rate) !== null);

  const update = (index: number, patch: Partial<StockRow>) =>
    setRows((current) => current.map((row, i) => (i === index ? { ...row, ...patch } : row)));

  async function save() {
    if (!valid || finishedPaise === null) return;
    const lines = rows.map((row) => ({
      stockItemId: row.stockItemId,
      quantity: parseQuantity(row.quantity)!,
      rateRupees: toPaise(row.rate)! / 100,
    }));
    const saved = await action.run(() =>
      api.admin.accounts.yearEnd.saveStock(fy, {
        lines,
        finishedGoodsRupees: finishedPaise / 100,
        note: orNull(note),
      }),
    );
    if (saved) onSaved(saved);
  }

  return (
    <div>
      <SectionLabel>Closing stock</SectionLabel>
      {!data.stockSaved && (
        <p className="mb-3 text-sm text-ink-faint">
          Pre-filled from stock on hand at 31 March. Check the counts and save.
        </p>
      )}
      {rows.length === 0 ? (
        <p className="text-ink-soft">No stock on hand at 31 March</p>
      ) : (
        <div className="overflow-x-auto rounded-2xl border border-line bg-panel">
          <table className="w-full min-w-[44rem] text-left text-sm">
            <thead className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
              <tr>
                <th className="px-4 py-2 font-medium">Item</th>
                <th className="px-4 py-2 font-medium">Unit</th>
                <th className="px-4 py-2 text-right font-medium">Quantity</th>
                <th className="px-4 py-2 text-right font-medium">Rate ₹</th>
                <th className="px-4 py-2 text-right font-medium">Value</th>
                <th className="px-4 py-2" />
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {rows.map((row, index) => {
                const quantity = parseQuantity(row.quantity);
                const ratePaise = toPaise(row.rate);
                return (
                  <tr key={row.stockItemId}>
                    <td className="px-4 py-3">{row.name}</td>
                    <td className="px-4 py-3 text-ink-soft">{row.unit}</td>
                    <td className="px-4 py-3 text-right">
                      <input
                        aria-label={`Quantity for ${row.name}`}
                        inputMode="decimal"
                        value={row.quantity}
                        readOnly={locked}
                        onChange={(event) => update(index, { quantity: event.target.value })}
                        className={cellInput(locked)}
                      />
                      {quantity === null && <p className="mt-1 text-xs text-bad">Use up to 2 decimals.</p>}
                    </td>
                    <td className="px-4 py-3 text-right">
                      <input
                        aria-label={`Rate for ${row.name}`}
                        inputMode="decimal"
                        value={row.rate}
                        readOnly={locked}
                        onChange={(event) => update(index, { rate: event.target.value })}
                        className={cellInput(locked)}
                      />
                      {ratePaise === null && <p className="mt-1 text-xs text-bad">Enter an amount.</p>}
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-right tabular-nums">
                      {quantity !== null && ratePaise !== null ? rupees(Math.round(quantity * ratePaise) / 100) : "—"}
                    </td>
                    <td className="px-4 py-3 text-right">
                      {!locked && (
                        <button
                          type="button"
                          aria-label={`Remove ${row.name}`}
                          onClick={() => setRows((current) => current.filter((_, i) => i !== index))}
                          className="rounded-lg p-2 text-bad hover:bg-bad-soft"
                        >
                          <TrashIcon size={18} />
                        </button>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
      <div className="mt-4 max-w-xs">
        <TextField
          label="Finished goods (₹)"
          inputMode="decimal"
          value={finishedGoods}
          readOnly={locked}
          error={finishedPaise === null ? "Enter an amount." : undefined}
          onChange={(event) => setFinishedGoods(event.target.value)}
        />
      </div>
      <TextAreaField
        className="mt-3"
        label="Note"
        maxLength={300}
        value={note}
        readOnly={locked}
        onChange={(event) => setNote(event.target.value)}
      />
      {owner && (
        <div className="mt-4">
          <button
            type="button"
            className={buttonPrimary}
            disabled={action.busy || data.closed || !valid}
            onClick={save}
          >
            {action.busy ? "Saving…" : "Save stock list"}
          </button>
        </div>
      )}
      <ErrorNote>{action.error}</ErrorNote>
    </div>
  );
}

function YearEndView({ fy, onChangeFy }: { fy: string; onChangeFy: (fy: string) => void }) {
  const owner = useIsOwner();
  const yearEnd = useLoad(() => api.admin.accounts.yearEnd.get(fy), [fy]);
  const closeAction = useAction();
  const reopenAction = useAction();
  const [closing, setClosing] = useState(false);
  const [reopening, setReopening] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const [downloadError, setDownloadError] = useState<string | null>(null);
  // Bumped whenever the server sends a new year-end: the stock list starts again from what it holds.
  const [version, setVersion] = useState(0);
  const v = yearEnd.value;

  function apply(next: YearEnd) {
    yearEnd.setValue(next);
    setVersion((current) => current + 1);
  }

  const options = fyOptions();
  if (!options.some((option) => option.value === fy)) {
    options.push({ value: fy, label: fyLabel(`${fy.slice(0, 4)}-04-01`) });
  }

  async function closeYear() {
    const closed = await closeAction.run(() => api.admin.accounts.yearEnd.close(fy));
    if (closed) {
      apply(closed);
      setClosing(false);
    }
  }

  async function reopenYear() {
    const reopened = await reopenAction.run(() => api.admin.accounts.yearEnd.reopen(fy));
    if (reopened) {
      apply(reopened);
      setReopening(false);
    }
  }

  async function downloadPack() {
    setDownloadError(null);
    setDownloading(true);
    try {
      const blob = await api.admin.accounts.yearEnd.auditorPack(fy);
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `vaabhi-books-${fy}.xlsx`;
      link.click();
      setTimeout(() => URL.revokeObjectURL(url), 0);
    } catch (e) {
      setDownloadError(describeError(e).message);
    } finally {
      setDownloading(false);
    }
  }

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader
        title="Year end"
        backHref="/money/accounts/"
        actions={
          v &&
          (owner && !v.closed ? (
            <button type="button" className={buttonPrimary} onClick={() => setClosing(true)}>
              Close year
            </button>
          ) : owner && v.canReopen ? (
            <button type="button" className={buttonSecondary} onClick={() => setReopening(true)}>
              Reopen
            </button>
          ) : undefined)
        }
      />
      <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
        <div>
          <SelectField
            className="w-full max-w-56"
            label="Financial year"
            options={options}
            value={fy}
            onChange={onChangeFy}
          />
          {v && <p className={`mt-2 font-medium ${v.closed ? "text-ok" : ""}`}>{v.closed ? "Closed" : "Open"}</p>}
        </div>
        <div className="flex flex-wrap items-center gap-3 pb-1">
          {v && !v.closed && <span className="text-sm text-ink-faint">Draft until the year is closed</span>}
          <button type="button" className={buttonSecondary} disabled={downloading} onClick={downloadPack}>
            <DownloadIcon size={18} /> {downloading ? "Downloading…" : "Download auditor pack"}
          </button>
        </div>
      </div>
      <ErrorNote>{yearEnd.error ?? downloadError}</ErrorNote>
      {!v ? (
        <LoadState loading={yearEnd.loading} error={null} />
      ) : (
        <>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <StatTile label="Closing stock" value={rupees(v.closingStockRupees)} />
            <StatTile label="Stock in books" value={rupees(v.stockBalanceRupees)} />
            <StatTile label="Adjustment" value={rupees(v.adjustmentRupees)} />
            <StatTile label="Profit for the year" value={rupees(v.profitRupees)} />
          </div>

          <SectionLabel>Checks</SectionLabel>
          {v.checks.length === 0 ? (
            <p className="text-ink-soft">No checks.</p>
          ) : (
            <ListCard>
              {v.checks.map((check) => (
                <div key={check.key} className="px-4 py-3 text-sm">
                  <p className="flex items-start gap-2">
                    <span aria-hidden className={check.ok ? "text-ok" : "text-bad"}>
                      {check.ok ? "✓" : "✗"}
                    </span>
                    <span className="font-medium">{check.label}</span>
                  </p>
                  {check.message && <p className="mt-1 pl-6 text-ink-soft">{check.message}</p>}
                </div>
              ))}
            </ListCard>
          )}

          <StockSection key={version} fy={fy} data={v} owner={owner} onSaved={apply} />

          <SectionLabel>Closing runs</SectionLabel>
          {v.runs.length === 0 ? (
            <p className="text-ink-soft">Not closed yet</p>
          ) : (
            <ListCard>
              {v.runs.map((run) => (
                <div key={run.id} className="px-4 py-3 text-sm">
                  <p>
                    Closed {formatDate(run.closedAt)}
                    {run.closedBy ? ` by ${run.closedBy}` : ""}
                  </p>
                  {run.reopenedAt && (
                    <p className="mt-1 text-ink-soft">
                      Reopened {formatDate(run.reopenedAt)}
                      {run.reopenedBy ? ` by ${run.reopenedBy}` : ""}
                    </p>
                  )}
                  {(run.stockEntryId !== null || run.closingEntryId !== null) && (
                    <p className="mt-1 flex flex-wrap gap-3">
                      {run.stockEntryId !== null && (
                        <Link
                          href={`/money/accounts/entry/?id=${run.stockEntryId}`}
                          className="font-medium text-accent-ink underline"
                        >
                          Stock entry
                        </Link>
                      )}
                      {run.closingEntryId !== null && (
                        <Link
                          href={`/money/accounts/entry/?id=${run.closingEntryId}`}
                          className="font-medium text-accent-ink underline"
                        >
                          Closing entry
                        </Link>
                      )}
                    </p>
                  )}
                </div>
              ))}
            </ListCard>
          )}
        </>
      )}

      <Sheet open={closing} title={`Close ${fy}?`} onClose={() => setClosing(false)}>
        <p className="mb-4 text-ink-soft">
          This posts the closing stock adjustment and the closing entry dated 31 March, and locks April to March. You
          can reopen it while no later year is closed.
        </p>
        <ErrorNote>{closeAction.error}</ErrorNote>
        <div className="mt-4 flex gap-3">
          <button type="button" className={buttonPrimary} disabled={closeAction.busy} onClick={closeYear}>
            {closeAction.busy ? "Closing…" : `Close ${fy}`}
          </button>
          <button type="button" className={buttonSecondary} onClick={() => setClosing(false)}>
            Cancel
          </button>
        </div>
      </Sheet>

      <Sheet open={reopening} title={`Reopen ${fy}?`} onClose={() => setReopening(false)}>
        <p className="mb-4 text-ink-soft">
          This reverses the closing entries and unlocks March. Other months stay locked.
        </p>
        <ErrorNote>{reopenAction.error}</ErrorNote>
        <div className="mt-4 flex gap-3">
          <button type="button" className={buttonPrimary} disabled={reopenAction.busy} onClick={reopenYear}>
            {reopenAction.busy ? "Reopening…" : `Reopen ${fy}`}
          </button>
          <button type="button" className={buttonSecondary} onClick={() => setReopening(false)}>
            Cancel
          </button>
        </div>
      </Sheet>
    </section>
  );
}

function Route() {
  const router = useRouter();
  const requested = useSearchParams().get("fy");
  const fy = requested && /^\d{4}-\d{2}$/.test(requested) ? requested : defaultFy();
  return (
    <YearEndView
      key={fy}
      fy={fy}
      onChangeFy={(next) => router.replace(`/money/accounts/year-end/?fy=${encodeURIComponent(next)}`)}
    />
  );
}

/** Close a financial year: check the books, count the stock at 31 March, and post the closing entries. */
export default function YearEndPage() {
  return (
    <Suspense fallback={<p className="text-ink-soft">Loading…</p>}>
      <Route />
    </Suspense>
  );
}
