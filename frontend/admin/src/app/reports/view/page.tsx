"use client";

import { useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { DownloadIcon } from "@/components/icons";
import { ErrorNote } from "@/components/tailoring";
import { ChipGroup, LoadState, PageHeader, TextField, buttonSecondary } from "@/components/ui";
import { api } from "@/lib/api";
import { monthOf, shiftMonth } from "@/lib/money";
import { download, formatCell, toCsv } from "@/lib/reports";
import { addDays, today, useLoad } from "@/lib/tailoring";

type Preset = "month" | "last" | "quarter" | "year" | "custom";

/** The date range a preset stands for, ending today (or at the end of last month). */
function presetRange(preset: Exclude<Preset, "custom">, now = today()): { from: string; to: string } {
  const month = monthOf(now);
  switch (preset) {
    case "month":
      return { from: month.from, to: now };
    case "last":
      return shiftMonth(month, -1);
    case "quarter":
      return { from: shiftMonth(month, -2).from, to: now };
    case "year":
      return { from: addDays(now, -364), to: now };
  }
}

function ReportView({ name }: { name: string }) {
  const [preset, setPreset] = useState<Preset>("month");
  const [range, setRange] = useState(() => presetRange("month"));
  const report = useLoad(() => api.admin.reports.get(name, range), [name, range.from, range.to]);
  const r = report.value;
  const totals = (r?.totals ?? {}) as Record<string, unknown>;

  return (
    <section className="mx-auto max-w-6xl">
      <PageHeader
        title={r?.title ?? "Report"}
        backHref="/reports/"
        actions={
          r && (
            <button
              type="button"
              className={buttonSecondary}
              onClick={() => download(`${r.name}-${r.from}-to-${r.to}.csv`, toCsv(r))}
            >
              <DownloadIcon size={18} /> CSV
            </button>
          )
        }
      />
      <div className="mb-4 flex flex-col gap-3">
        <ChipGroup
          label="Range"
          options={[
            { value: "month", label: "This month" },
            { value: "last", label: "Last month" },
            { value: "quarter", label: "3 months" },
            { value: "year", label: "12 months" },
            { value: "custom", label: "Dates…" },
          ]}
          value={preset}
          onChange={(next) => {
            setPreset(next);
            if (next !== "custom") setRange(presetRange(next));
          }}
        />
        {preset === "custom" && (
          <div className="flex flex-wrap gap-3">
            <TextField
              label="From"
              type="date"
              value={range.from}
              onChange={(e) => e.target.value && setRange({ ...range, from: e.target.value })}
            />
            <TextField
              label="To"
              type="date"
              value={range.to}
              onChange={(e) => e.target.value && setRange({ ...range, to: e.target.value })}
            />
          </div>
        )}
      </div>
      <ErrorNote>{report.error}</ErrorNote>
      {!r ? (
        <LoadState loading={report.loading} error={null} />
      ) : (
        <>
          {r.note && <p className="mb-3 text-sm text-ink-soft">{r.note}</p>}
          {r.rows.length === 0 ? (
            <p className="text-ink-soft">Nothing in this range.</p>
          ) : (
            <div className="overflow-x-auto rounded-2xl border border-line bg-panel">
              <table className="w-full text-left text-sm">
                <thead className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
                  <tr>
                    {r.columns.map((c) => (
                      <th
                        key={c.key}
                        className={`whitespace-nowrap px-4 py-2 font-medium ${c.type === "TEXT" || c.type === "DATE" ? "" : "text-right"}`}
                      >
                        {c.label}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {r.rows.map((row, i) => (
                    <tr key={i}>
                      {r.columns.map((c) => (
                        <td
                          key={c.key}
                          className={`px-4 py-2 ${c.type === "TEXT" || c.type === "DATE" ? "" : "text-right tabular-nums"}`}
                        >
                          {formatCell(c, (row as Record<string, unknown>)[c.key])}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
                {Object.keys(totals).length > 0 && (
                  <tfoot className="border-t border-line-strong font-semibold">
                    <tr>
                      {r.columns.map((c, i) => (
                        <td
                          key={c.key}
                          className={`px-4 py-2 ${c.type === "TEXT" || c.type === "DATE" ? "" : "text-right tabular-nums"}`}
                        >
                          {i === 0 ? "Total" : c.key in totals ? formatCell(c, totals[c.key]) : ""}
                        </td>
                      ))}
                    </tr>
                  </tfoot>
                )}
              </table>
            </div>
          )}
        </>
      )}
    </section>
  );
}

function Page() {
  const name = useSearchParams().get("name");
  return name ? <ReportView key={name} name={name} /> : <ErrorNote>No report selected.</ErrorNote>;
}

export default function ReportViewPage() {
  return (
    <Suspense fallback={<p className="text-ink-soft">Loading…</p>}>
      <Page />
    </Suspense>
  );
}
