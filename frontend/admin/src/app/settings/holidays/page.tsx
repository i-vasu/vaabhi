"use client";

import { useState } from "react";
import type { CalendarSettings } from "@vaabhi/api-client";
import { cn } from "@vaabhi/ui";
import { PlusIcon, TrashIcon } from "@/components/icons";
import { LoadState, PageHeader, SaveBar, SectionLabel, Sheet, TextField, buttonPrimary } from "@/components/ui";
import { useSection } from "@/lib/settings";

type Day = CalendarSettings["weeklyOff"][number];

const days: { value: Day; label: string }[] = [
  { value: "MONDAY", label: "Mon" },
  { value: "TUESDAY", label: "Tue" },
  { value: "WEDNESDAY", label: "Wed" },
  { value: "THURSDAY", label: "Thu" },
  { value: "FRIDAY", label: "Fri" },
  { value: "SATURDAY", label: "Sat" },
  { value: "SUNDAY", label: "Sun" },
];

const longDate = (iso: string) =>
  new Date(`${iso}T00:00:00`).toLocaleDateString("en-IN", {
    weekday: "short",
    day: "numeric",
    month: "short",
    year: "numeric",
  });

export default function HolidaysPage() {
  const section = useSection("calendar");
  const c = section.value;
  const [adding, setAdding] = useState(false);
  const [date, setDate] = useState("");
  const [name, setName] = useState("");

  if (!c) {
    return (
      <section className="mx-auto max-w-3xl">
        <PageHeader title="Holidays & weekly off" backHref="/settings/" />
        <LoadState loading={section.loading} error={section.loadError} />
      </section>
    );
  }

  const toggleDay = (day: Day) =>
    section.update({
      weeklyOff: c.weeklyOff.includes(day) ? c.weeklyOff.filter((d) => d !== day) : [...c.weeklyOff, day],
    });

  const holidays = [...c.holidays].sort((a, b) => a.date.localeCompare(b.date));

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader title="Holidays & weekly off" backHref="/settings/" />

      <SectionLabel>Weekly off</SectionLabel>
      <p className="mb-3 text-ink-soft">Days the shop is closed every week.</p>
      <div className="flex flex-wrap gap-2">
        {days.map((day) => {
          const off = c.weeklyOff.includes(day.value);
          return (
            <button
              key={day.value}
              type="button"
              aria-pressed={off}
              onClick={() => toggleDay(day.value)}
              className={cn(
                "rounded-xl border px-5 py-2.5",
                off ? "border-accent bg-accent-soft text-accent-ink" : "border-line-strong text-ink-soft",
              )}
            >
              {day.label}
            </button>
          );
        })}
      </div>

      <SectionLabel
        action={
          <button
            type="button"
            className="inline-flex items-center gap-1 text-accent-ink"
            onClick={() => setAdding(true)}
          >
            <PlusIcon size={18} /> Add date
          </button>
        }
      >
        Holidays
      </SectionLabel>
      {holidays.length === 0 ? (
        <p className="text-ink-soft">No holidays added yet.</p>
      ) : (
        <ul className="divide-y divide-line rounded-2xl border border-line bg-panel">
          {holidays.map((holiday) => (
            <li key={holiday.date} className="flex items-center gap-3 px-4 py-3">
              <span className="flex-1">
                <span className="block font-medium">{holiday.name}</span>
                <span className="text-sm text-ink-soft">{longDate(holiday.date)}</span>
              </span>
              <button
                type="button"
                aria-label={`Remove ${holiday.name}`}
                onClick={() => section.update({ holidays: c.holidays.filter((h) => h.date !== holiday.date) })}
                className="rounded-lg p-2 text-bad hover:bg-bad-soft"
              >
                <TrashIcon size={18} />
              </button>
            </li>
          ))}
        </ul>
      )}

      <SaveBar saving={section.saving} saved={section.saved} error={section.error} onSave={() => void section.save()} />

      <Sheet open={adding} title="Add holiday" onClose={() => setAdding(false)}>
        <form
          className="flex flex-col gap-4"
          onSubmit={(event) => {
            event.preventDefault();
            if (!date || !name.trim()) return;
            section.update({
              holidays: [...c.holidays.filter((h) => h.date !== date), { date, name: name.trim() }],
            });
            setDate("");
            setName("");
            setAdding(false);
          }}
        >
          <TextField label="Date" type="date" required value={date} onChange={(event) => setDate(event.target.value)} />
          <TextField
            label="Holiday name"
            required
            maxLength={80}
            placeholder="e.g. Diwali"
            value={name}
            onChange={(event) => setName(event.target.value)}
          />
          <button type="submit" className={buttonPrimary}>
            Add holiday
          </button>
        </form>
      </Sheet>
    </section>
  );
}
