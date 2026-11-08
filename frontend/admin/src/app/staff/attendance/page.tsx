"use client";

import Link from "next/link";
import { useState } from "react";
import type { AttendanceEntry, AttendanceStatus, DayAttendance } from "@vaabhi/api-client";
import { ErrorNote, Pill } from "@/components/tailoring";
import { EmptyState, LoadState, PageHeader, TextField, buttonPrimary, buttonSecondary } from "@/components/ui";
import { CalendarIcon } from "@/components/icons";
import { api } from "@/lib/api";
import { ATTENDANCE, attendanceLabel, hhmm, minutes, worked } from "@/lib/staff";
import { addDays, formatDate, today, useAction, useLoad } from "@/lib/tailoring";

/** Mark who came in on a day; late and overtime come from each person's shift. */
export default function AttendancePage() {
  const [day, setDay] = useState(today());
  const attendance = useLoad(() => api.admin.staff.day(day), [day]);
  const action = useAction();
  const a = attendance.value;
  const unmarked = a?.entries.filter((e) => e.status === null) ?? [];

  const apply = async (call: () => Promise<DayAttendance>) => {
    const next = await action.run(call);
    if (next) attendance.setValue(next);
  };

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader
        title="Attendance"
        backHref="/staff/"
        actions={
          <Link href="/staff/attendance/month/" className={buttonSecondary}>
            Month
          </Link>
        }
      />
      <div className="mb-4 flex flex-wrap items-end gap-2">
        <button type="button" className={buttonSecondary} onClick={() => setDay(addDays(day, -1))}>
          ‹ Day before
        </button>
        <TextField label="Day" type="date" value={day} onChange={(e) => e.target.value && setDay(e.target.value)} />
        <button type="button" className={buttonSecondary} onClick={() => setDay(addDays(day, 1))}>
          Day after ›
        </button>
      </div>
      {a?.holiday && (
        <p className="mb-4 rounded-xl bg-panel-2 px-4 py-3 text-ink-soft">
          The shop is closed on {formatDate(day)}: {a.holiday}.
        </p>
      )}
      <ErrorNote>{attendance.error ?? action.error}</ErrorNote>
      {!a ? (
        <LoadState loading={attendance.loading} error={null} />
      ) : a.entries.length === 0 ? (
        <EmptyState icon={CalendarIcon} title="No staff yet" />
      ) : (
        <>
          {unmarked.length > 0 && (
            <button
              type="button"
              className={`${buttonPrimary} mb-4`}
              disabled={action.busy}
              onClick={() =>
                void apply(() =>
                  api.admin.staff.markMany(
                    day,
                    "PRESENT",
                    unmarked.filter((e) => !e.suggested).map((e) => e.staffId),
                  ),
                )
              }
            >
              Mark the other {unmarked.filter((e) => !e.suggested).length} present
            </button>
          )}
          <ul className="flex flex-col gap-3">
            {a.entries.map((entry) => (
              <EntryCard
                key={`${a.day}-${entry.staffId}`}
                entry={entry}
                busy={action.busy}
                onMark={(status, checkIn, checkOut) =>
                  apply(() => api.admin.staff.mark(day, entry.staffId, { status, checkIn, checkOut, note: entry.note }))
                }
                onClear={() => apply(() => api.admin.staff.clear(day, entry.staffId))}
              />
            ))}
          </ul>
        </>
      )}
    </section>
  );
}

function EntryCard({
  entry,
  busy,
  onMark,
  onClear,
}: {
  entry: AttendanceEntry;
  busy: boolean;
  onMark: (status: AttendanceStatus, checkIn: string | null, checkOut: string | null) => Promise<void>;
  onClear: () => Promise<void>;
}) {
  const [checkIn, setCheckIn] = useState(hhmm(entry.checkIn));
  const [checkOut, setCheckOut] = useState(hhmm(entry.checkOut));
  const status = entry.status;
  const tone = ATTENDANCE.find((s) => s.value === status)?.tone ?? "default";

  return (
    <li className="rounded-2xl border border-line bg-panel p-4">
      <div className="mb-3 flex flex-wrap items-center gap-2">
        <span className="font-medium">{entry.name}</span>
        {entry.designation && <span className="text-sm text-ink-soft">{entry.designation}</span>}
        <span className="ml-auto text-sm text-ink-faint">
          {hhmm(entry.shiftStart)}–{hhmm(entry.shiftEnd)}
        </span>
        {status ? (
          <Pill tone={tone}>{attendanceLabel(status)}</Pill>
        ) : entry.suggested ? (
          <Pill>Probably {attendanceLabel(entry.suggested).toLowerCase()}</Pill>
        ) : (
          <Pill tone="warn">Not marked</Pill>
        )}
      </div>
      <div role="group" aria-label={`Mark ${entry.name}`} className="flex flex-wrap gap-2">
        {ATTENDANCE.map((s) => (
          <button
            key={s.value}
            type="button"
            aria-pressed={status === s.value}
            disabled={busy}
            onClick={() =>
              void onMark(s.value, worked(s.value) ? checkIn || null : null, worked(s.value) ? checkOut || null : null)
            }
            className={`rounded-full border px-3 py-1 text-sm ${
              status === s.value
                ? "border-accent/60 bg-accent-soft text-accent-ink"
                : "border-line-strong text-ink-soft"
            }`}
          >
            {s.label}
          </button>
        ))}
        {status && (
          <button
            type="button"
            className="text-sm text-ink-faint underline"
            disabled={busy}
            onClick={() => void onClear()}
          >
            Clear
          </button>
        )}
      </div>
      {worked(status) && (
        <div className="mt-3 flex flex-wrap items-end gap-3">
          <TextField
            label={`In (${entry.name})`}
            type="time"
            value={checkIn}
            onChange={(e) => setCheckIn(e.target.value)}
          />
          <TextField
            label={`Out (${entry.name})`}
            type="time"
            value={checkOut}
            onChange={(e) => setCheckOut(e.target.value)}
          />
          <button
            type="button"
            className={buttonSecondary}
            disabled={busy}
            onClick={() => void onMark(status!, checkIn || null, checkOut || null)}
          >
            Save times
          </button>
          <span className="text-sm text-ink-soft">
            {entry.lateMinutes > 0 && <span className="text-warn">Late {minutes(entry.lateMinutes)} </span>}
            {entry.overtimeMinutes > 0 && <span className="text-ok">Overtime {minutes(entry.overtimeMinutes)}</span>}
          </span>
        </div>
      )}
    </li>
  );
}
