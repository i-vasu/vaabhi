"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Fragment, Suspense, useState } from "react";
import type { StatementLine } from "@vaabhi/api-client";
import { DownloadIcon } from "@/components/icons";
import { ErrorNote } from "@/components/tailoring";
import { LoadState, PageHeader, SegmentedControl, SelectField, TextField, buttonSecondary } from "@/components/ui";
import { fyLabel, fyRange } from "@/lib/accounts";
import { api } from "@/lib/api";
import { rupees } from "@/lib/settings";
import { formatDate, today, useAction, useLoad } from "@/lib/tailoring";

type Tab = "pl" | "bs";

const TABS: { value: Tab; label: string }[] = [
  { value: "pl", label: "Profit and loss" },
  { value: "bs", label: "Balance sheet" },
];

/** The current FY and the four before it, plus a way to pick any dates. */
function periodOptions(): { value: string; label: string }[] {
  const startYear = Number(fyRange(today()).from.slice(0, 4));
  return [
    ...Array.from({ length: 5 }, (_, index) => {
      const start = `${startYear - index}-04-01`;
      return { value: start, label: fyLabel(start) };
    }),
    { value: "custom", label: "Custom" },
  ];
}

/** The last day of the FY that starts on `start` ("2026-04-01" → "2027-03-31"). */
function fyEndOf(start: string): string {
  return `${Number(start.slice(0, 4)) + 1}-03-31`;
}

/** The FY label the API uses ("2026-27") for the FY a date falls in. */
function fyKey(isoDate: string): string {
  const startYear = Number(fyRange(isoDate).from.slice(0, 4));
  return `${startYear}-${String((startYear + 1) % 100).padStart(2, "0")}`;
}

type TotalRow = { label: string; amountRupees: number; previousRupees: number };

function StatementTable({
  sections,
  headings,
  totals,
}: {
  sections: { title: string; lines: StatementLine[] }[];
  headings: { current: string; previous: string };
  totals: TotalRow[];
}) {
  const [open, setOpen] = useState<Record<string, boolean>>({});
  const cell = "px-4 py-2 text-right tabular-nums";

  return (
    <div className="overflow-x-auto rounded-2xl border border-line bg-panel">
      <table className="w-full text-left text-sm">
        <thead className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
          <tr>
            <th className="px-4 py-2 font-medium">Line / account</th>
            <th className="px-4 py-2 text-right font-medium">{headings.current}</th>
            <th className="px-4 py-2 text-right font-medium">{headings.previous}</th>
          </tr>
        </thead>
        {sections.map((section) => (
          <tbody key={section.title} className="divide-y divide-line border-t border-line first:border-t-0">
            <tr>
              <td
                colSpan={3}
                className="bg-panel-2 px-4 py-2 text-xs font-semibold uppercase tracking-wide text-ink-faint"
              >
                {section.title}
              </td>
            </tr>
            {section.lines.map((line) => {
              const expanded = !!open[line.line];
              return (
                <Fragment key={line.line}>
                  <tr>
                    <td className="px-4 py-3 font-semibold">
                      <button
                        type="button"
                        aria-expanded={expanded}
                        onClick={() => setOpen((current) => ({ ...current, [line.line]: !expanded }))}
                        className="flex items-center gap-2 text-left"
                      >
                        <span aria-hidden className="text-ink-faint">
                          {expanded ? "▾" : "▸"}
                        </span>
                        {line.label}
                      </button>
                    </td>
                    <td className={`whitespace-nowrap font-semibold ${cell}`}>{rupees(line.amountRupees)}</td>
                    <td className={`whitespace-nowrap font-semibold ${cell}`}>{rupees(line.previousRupees)}</td>
                  </tr>
                  {expanded &&
                    line.accounts.map((account) => (
                      <tr key={account.accountId} className="text-ink-soft">
                        <td className="px-4 py-2 pl-10">
                          {account.accountId === 0 ? (
                            `${account.code} · ${account.name}`
                          ) : (
                            <Link
                              href={`/money/accounts/ledger/?id=${account.accountId}`}
                              className="text-accent-ink underline"
                            >
                              {account.code} · {account.name}
                            </Link>
                          )}
                        </td>
                        <td className={`whitespace-nowrap ${cell}`}>{rupees(account.amountRupees)}</td>
                        <td className={`whitespace-nowrap ${cell}`}>{rupees(account.previousRupees)}</td>
                      </tr>
                    ))}
                </Fragment>
              );
            })}
          </tbody>
        ))}
        <tfoot className="border-t border-line font-semibold">
          {totals.map((total) => (
            <tr key={total.label}>
              <td className="px-4 py-2">{total.label}</td>
              <td className={`whitespace-nowrap ${cell}`}>{rupees(total.amountRupees)}</td>
              <td className={`whitespace-nowrap ${cell}`}>{rupees(total.previousRupees)}</td>
            </tr>
          ))}
        </tfoot>
      </table>
    </div>
  );
}

function StatementsView({ tab, onTab }: { tab: Tab; onTab: (tab: Tab) => void }) {
  const [options] = useState(periodOptions);
  const [period, setPeriod] = useState(() => fyRange(today()).from);
  const [customFrom, setCustomFrom] = useState(() => fyRange(today()).from);
  const [customTo, setCustomTo] = useState(() => today());
  const [asOf, setAsOf] = useState(() => today());
  const download = useAction();

  const range = period === "custom" ? { from: customFrom, to: customTo } : { from: period, to: fyEndOf(period) };
  const profit = useLoad(
    () => api.admin.accounts.statements.profitAndLoss(range.from, range.to),
    [range.from, range.to],
  );
  const balance = useLoad(() => api.admin.accounts.statements.balanceSheet(asOf), [asOf]);

  function choosePeriod(value: string) {
    setPeriod(value);
    if (value !== "custom") {
      const end = fyEndOf(value);
      setAsOf(today() > end ? end : today());
    }
  }

  async function downloadExcel() {
    const label = fyKey(tab === "pl" ? range.to : asOf);
    const blob = await download.run(() => api.admin.accounts.yearEnd.auditorPack(label));
    if (!blob) return;
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `vaabhi-books-${label}.xlsx`;
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 0);
  }

  const p = profit.value;
  const b = balance.value;
  const previousIncome = p?.income.reduce((sum, line) => sum + line.previousRupees, 0) ?? 0;
  const previousExpenses = p?.expenses.reduce((sum, line) => sum + line.previousRupees, 0) ?? 0;
  const difference = b ? Math.abs(b.totalEquityAndLiabilitiesRupees - b.totalAssetsRupees) : 0;

  return (
    <section className="mx-auto max-w-5xl">
      <PageHeader
        title="Statements"
        backHref="/money/accounts/"
        actions={
          <button type="button" className={buttonSecondary} disabled={download.busy} onClick={downloadExcel}>
            <DownloadIcon size={18} /> {download.busy ? "Preparing…" : "Download Excel"}
          </button>
        }
      />
      <div className="mb-6">
        <SegmentedControl label="Statement" options={TABS} value={tab} onChange={onTab} />
      </div>
      <ErrorNote>{download.error}</ErrorNote>

      {tab === "pl" && (
        <>
          <div className="mb-4 flex flex-wrap items-end gap-3">
            <SelectField className="w-44" label="Period" options={options} value={period} onChange={choosePeriod} />
            {period === "custom" && (
              <>
                <TextField
                  className="w-44"
                  label="From"
                  type="date"
                  value={customFrom}
                  onChange={(e) => e.target.value && setCustomFrom(e.target.value)}
                />
                <TextField
                  className="w-44"
                  label="To"
                  type="date"
                  value={customTo}
                  onChange={(e) => e.target.value && setCustomTo(e.target.value)}
                />
              </>
            )}
          </div>
          <ErrorNote>{profit.error}</ErrorNote>
          {!p ? (
            <LoadState loading={profit.loading} error={null} />
          ) : p.income.length === 0 && p.expenses.length === 0 ? (
            <p className="text-ink-soft">No entries in this period</p>
          ) : (
            <StatementTable
              sections={[
                { title: "Income", lines: p.income },
                { title: "Expenses", lines: p.expenses },
              ]}
              headings={{
                current: "This period",
                previous: `${formatDate(p.previousFrom)} – ${formatDate(p.previousTo)}`,
              }}
              totals={[
                {
                  label: "Total income",
                  amountRupees: p.totalIncomeRupees,
                  previousRupees: previousIncome,
                },
                {
                  label: "Total expenses",
                  amountRupees: p.totalExpensesRupees,
                  previousRupees: previousExpenses,
                },
                {
                  label: p.profitRupees >= 0 ? "Profit" : "Loss",
                  amountRupees: Math.abs(p.profitRupees),
                  previousRupees: Math.abs(p.previousProfitRupees),
                },
              ]}
            />
          )}
        </>
      )}

      {tab === "bs" && (
        <>
          <TextField
            className="mb-4 max-w-48"
            label="As at"
            type="date"
            value={asOf}
            onChange={(e) => e.target.value && setAsOf(e.target.value)}
          />
          <ErrorNote>{balance.error}</ErrorNote>
          {!b ? (
            <LoadState loading={balance.loading} error={null} />
          ) : (
            <>
              {!b.balanced && (
                <p role="alert" className="mb-4 rounded-xl bg-bad-soft px-4 py-3 text-bad">
                  This balance sheet doesn&apos;t balance — out by {rupees(difference)}.{" "}
                  <Link href="/money/accounts/trial-balance/" className="underline">
                    Check the trial balance.
                  </Link>
                </p>
              )}
              {b.equityAndLiabilities.length === 0 && b.assets.length === 0 ? (
                <p className="text-ink-soft">No balances as at this date</p>
              ) : (
                <StatementTable
                  sections={[
                    { title: "Equity and liabilities", lines: b.equityAndLiabilities },
                    { title: "Assets", lines: b.assets },
                  ]}
                  headings={{
                    current: `As at ${formatDate(b.asOf)}`,
                    previous: `As at ${formatDate(b.previousAsOf)}`,
                  }}
                  totals={[
                    {
                      label: "Total equity and liabilities",
                      amountRupees: b.totalEquityAndLiabilitiesRupees,
                      previousRupees: b.previousTotalEquityAndLiabilitiesRupees,
                    },
                    {
                      label: "Total assets",
                      amountRupees: b.totalAssetsRupees,
                      previousRupees: b.previousTotalAssetsRupees,
                    },
                  ]}
                />
              )}
            </>
          )}
        </>
      )}
    </section>
  );
}

function Route() {
  const router = useRouter();
  const tab = useSearchParams().get("tab") === "bs" ? "bs" : "pl";
  return <StatementsView tab={tab} onTab={(next) => router.replace(`/money/accounts/statements/?tab=${next}`)} />;
}

/** Profit and loss and balance sheet by Schedule III line, with the previous period alongside. */
export default function StatementsPage() {
  return (
    <Suspense fallback={<p className="text-ink-soft">Loading…</p>}>
      <Route />
    </Suspense>
  );
}
