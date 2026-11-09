"use client";

import { useState } from "react";
import { MonthPicker } from "@/components/money";
import { ErrorNote } from "@/components/tailoring";
import { LoadState } from "@/components/ui";
import { api } from "@/lib/api";
import { monthOf } from "@/lib/money";
import { ATTENDANCE, minutes } from "@/lib/staff";
import { today, useLoad } from "@/lib/tailoring";

/** Everyone's month: one letter per day, with totals. Shown on the Attendance page's Month tab. */
export function AttendanceMonth() {
  const [month, setMonth] = useState(() => monthOf(today()));
  const key = month.from.slice(0, 7);
  const data = useLoad(() => api.admin.staff.month(key), [key]);
  const m = data.value;

  return (
    <div>
      <div className="mb-3 flex justify-end">
        <MonthPicker value={month} onChange={setMonth} />
      </div>
      <p className="mb-3 text-sm text-ink-soft">
        {ATTENDANCE.map((s) => `${s.short} ${s.label.toLowerCase()}`).join(" · ")} · blank not marked
      </p>
      <ErrorNote>{data.error}</ErrorNote>
      {!m ? (
        <LoadState loading={data.loading} error={null} />
      ) : (
        <div className="overflow-x-auto rounded-2xl border border-line bg-panel">
          <table className="w-full text-center text-xs">
            <thead className="border-b border-line text-ink-faint">
              <tr>
                <th className="sticky left-0 bg-panel px-3 py-2 text-left font-medium">Name</th>
                {(m.rows[0]?.days ?? []).map((_, i) => (
                  <th key={i} className="px-1 py-2 font-medium tabular-nums">
                    {i + 1}
                  </th>
                ))}
                <th className="px-2 py-2 font-medium">P</th>
                <th className="px-2 py-2 font-medium">½</th>
                <th className="px-2 py-2 font-medium">A</th>
                <th className="px-2 py-2 font-medium">L</th>
                <th className="px-2 py-2 font-medium">Late</th>
                <th className="px-2 py-2 font-medium">OT</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {m.rows.map((row) => (
                <tr key={row.staffId}>
                  <th scope="row" className="sticky left-0 bg-panel px-3 py-2 text-left text-sm font-medium">
                    {row.name}
                  </th>
                  {row.days.map((status, i) => {
                    const s = ATTENDANCE.find((a) => a.value === status);
                    return (
                      <td
                        key={i}
                        title={s?.label ?? "Not marked"}
                        className={`px-1 py-2 ${s?.tone === "bad" ? "text-bad" : s?.tone === "warn" ? "text-warn" : s?.tone === "ok" ? "text-ok" : "text-ink-soft"}`}
                      >
                        {s?.short ?? ""}
                      </td>
                    );
                  })}
                  <td className="px-2 py-2 tabular-nums">{row.present}</td>
                  <td className="px-2 py-2 tabular-nums">{row.halfDays}</td>
                  <td className="px-2 py-2 tabular-nums">{row.absent}</td>
                  <td className="px-2 py-2 tabular-nums">{row.leave}</td>
                  <td className="whitespace-nowrap px-2 py-2">{minutes(row.lateMinutes)}</td>
                  <td className="whitespace-nowrap px-2 py-2">{minutes(row.overtimeMinutes)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
