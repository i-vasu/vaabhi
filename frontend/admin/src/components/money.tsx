"use client";

import { monthLabel, shiftMonth, type Month } from "@/lib/money";
import { ChevronLeftIcon, ChevronRightIcon } from "./icons";

/** ‹ November 2026 › — steps through months. */
export function MonthPicker({ value, onChange }: { value: Month; onChange: (month: Month) => void }) {
  return (
    <div className="flex items-center gap-2">
      <button
        type="button"
        aria-label="Previous month"
        className="grid size-10 place-items-center rounded-lg border border-line bg-panel text-ink-soft hover:text-ink"
        onClick={() => onChange(shiftMonth(value, -1))}
      >
        <ChevronLeftIcon />
      </button>
      <span className="min-w-36 text-center font-medium" aria-live="polite">
        {monthLabel(value)}
      </span>
      <button
        type="button"
        aria-label="Next month"
        className="grid size-10 place-items-center rounded-lg border border-line bg-panel text-ink-soft hover:text-ink"
        onClick={() => onChange(shiftMonth(value, 1))}
      >
        <ChevronRightIcon />
      </button>
    </div>
  );
}

/** A label and an amount on one line, for totals blocks. */
export function AmountLine({ label, value, strong = false }: { label: string; value: string; strong?: boolean }) {
  return (
    <div
      className={strong ? "mt-1 flex justify-between text-base font-semibold" : "flex justify-between text-ink-soft"}
    >
      <dt>{label}</dt>
      <dd className="tabular-nums">{value}</dd>
    </div>
  );
}
