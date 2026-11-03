"use client";

import { useState } from "react";
import { rupees } from "@/lib/settings";

/*
 * Small charts for the reports hub. Colours are categorical slots 1–3 of the reference palette stepped for a dark
 * surface (validated against the panel colour #111a2e); text always uses ink tokens, never series colours. Every
 * chart has a legend, a hover tooltip and the same numbers as a table for screen readers.
 */

export const SERIES = [
  { key: "bookedRupees", label: "Booked", color: "#3987e5" },
  { key: "collectedRupees", label: "Collected", color: "#d95926" },
  { key: "expensesRupees", label: "Spent", color: "#199e70" },
] as const;

type MonthRow = { month: string; bookedRupees: number; collectedRupees: number; expensesRupees: number };

const monthName = new Intl.DateTimeFormat("en-IN", { month: "short", timeZone: "UTC" });
export const shortMonth = (month: string) => monthName.format(new Date(`${month}-01T00:00:00Z`));

function Legend() {
  return (
    <ul className="mb-3 flex flex-wrap gap-4 text-sm text-ink-soft">
      {SERIES.map((s) => (
        <li key={s.key} className="flex items-center gap-2">
          <span aria-hidden className="size-3 rounded-sm" style={{ background: s.color }} />
          {s.label}
        </li>
      ))}
    </ul>
  );
}

/** Booked, collected and spent per month as grouped bars on one axis. */
export function MonthBars({ months }: { months: MonthRow[] }) {
  const [hover, setHover] = useState<number | null>(null);
  const most = Math.max(1, ...months.flatMap((m) => SERIES.map((s) => m[s.key])));
  const height = 160;

  return (
    <figure className="rounded-2xl border border-line bg-panel p-4">
      <figcaption className="mb-2 font-medium">Six months: booked, collected and spent</figcaption>
      <Legend />
      <div className="relative">
        <div className="flex items-end gap-3 border-b border-line" style={{ height }} aria-hidden>
          {months.map((m, i) => (
            <div
              key={m.month}
              className="relative flex h-full flex-1 items-end justify-center gap-[2px]"
              onMouseEnter={() => setHover(i)}
              onMouseLeave={() => setHover(null)}
            >
              {SERIES.map((s) => (
                <span
                  key={s.key}
                  className="w-full max-w-4 rounded-t"
                  style={{
                    height: `${Math.max(m[s.key] > 0 ? 2 : 0, (m[s.key] / most) * (height - 8))}px`,
                    background: s.color,
                    opacity: hover === null || hover === i ? 1 : 0.45,
                  }}
                />
              ))}
              {hover === i && (
                <div className="absolute bottom-full z-10 mb-2 w-40 rounded-xl border border-line-strong bg-panel-2 p-2 text-xs shadow-lg">
                  <p className="mb-1 font-medium text-ink">{shortMonth(m.month)}</p>
                  {SERIES.map((s) => (
                    <p key={s.key} className="flex items-center justify-between gap-2 text-ink-soft">
                      <span className="flex items-center gap-1">
                        <span className="size-2 rounded-sm" style={{ background: s.color }} />
                        {s.label}
                      </span>
                      <span className="tabular-nums text-ink">{rupees(m[s.key])}</span>
                    </p>
                  ))}
                </div>
              )}
            </div>
          ))}
        </div>
        <div className="mt-1 flex gap-3 text-center text-xs text-ink-faint" aria-hidden>
          {months.map((m) => (
            <span key={m.month} className="flex-1">
              {shortMonth(m.month)}
            </span>
          ))}
        </div>
      </div>
      <table className="sr-only">
        <caption>Booked, collected and spent per month</caption>
        <thead>
          <tr>
            <th>Month</th>
            {SERIES.map((s) => (
              <th key={s.key}>{s.label}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {months.map((m) => (
            <tr key={m.month}>
              <th>{shortMonth(m.month)}</th>
              {SERIES.map((s) => (
                <td key={s.key}>{rupees(m[s.key])}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </figure>
  );
}

/** Money owed by how late it is: one bar per bucket, label and amount in text beside it. */
export function AgeingBars({ buckets }: { buckets: { label: string; count: number; rupees: number }[] }) {
  const most = Math.max(1, ...buckets.map((b) => b.rupees));
  return (
    <figure className="rounded-2xl border border-line bg-panel p-4">
      <figcaption className="mb-3 font-medium">What customers owe, by how late</figcaption>
      <ul className="flex flex-col gap-3">
        {buckets.map((b) => (
          <li key={b.label} title={`${b.label}: ${rupees(b.rupees)} on ${b.count}`}>
            <div className="mb-1 flex justify-between text-sm">
              <span className="text-ink-soft">{b.label}</span>
              <span className="tabular-nums text-ink">
                {rupees(b.rupees)} <span className="text-ink-faint">· {b.count}</span>
              </span>
            </div>
            <div className="h-2 rounded-full bg-panel-2" aria-hidden>
              <div
                className="h-2 rounded-full"
                style={{ width: `${(b.rupees / most) * 100}%`, background: SERIES[0].color }}
              />
            </div>
          </li>
        ))}
      </ul>
    </figure>
  );
}
