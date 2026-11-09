"use client";

import { useState } from "react";
import { cn } from "@vaabhi/ui";
import { useStaff } from "@/components/app-shell";
import { CalendarIcon } from "@/components/icons";
import { EmptyState, LoadState, PageHeader, TextField, buttonPrimary, buttonSecondary } from "@/components/ui";
import { hasPermission } from "@/lib/nav";
import { useSection } from "@/lib/settings";

type Mode = "WEEK" | "MONTH" | "AGENDA";

const MODES: { value: Mode; label: string }[] = [
  { value: "WEEK", label: "Week" },
  { value: "MONTH", label: "Month" },
  { value: "AGENDA", label: "List" },
];

/** Google only lets its read-only "embed" view sit inside another app; adding an event opens Google itself. */
const NEW_EVENT = "https://calendar.google.com/calendar/r/eventedit";

function embedUrl(calendarId: string, mode: Mode): string {
  const params = new URLSearchParams({
    src: calendarId,
    ctz: Intl.DateTimeFormat().resolvedOptions().timeZone || "Asia/Kolkata",
    mode,
    showPrint: "0",
    showTitle: "0",
  });
  return `https://calendar.google.com/calendar/embed?${params}`;
}

/**
 * The shop's Google Calendar inside the app. The calendar to show is a setting (usually the shop's Gmail
 * address); a private calendar shows when this browser is signed in to that Google account.
 */
export default function CalendarPage() {
  const staff = useStaff();
  const section = useSection("calendar");
  const c = section.value;
  const [mode, setMode] = useState<Mode>("WEEK");
  const [editing, setEditing] = useState(false);
  const canEdit = hasPermission(staff, "SETTINGS");
  const calendarId = c?.googleCalendarId?.trim() || "";

  return (
    <section className="mx-auto max-w-6xl">
      <PageHeader
        title="Calendar"
        actions={
          <a href={NEW_EVENT} target="_blank" rel="noopener noreferrer" className={buttonPrimary}>
            Add event
          </a>
        }
      />
      {!c ? (
        <LoadState loading={section.loading} error={section.loadError} />
      ) : !calendarId || editing ? (
        canEdit ? (
          <form
            className="flex max-w-xl flex-col gap-3 rounded-2xl border border-line bg-panel p-5"
            onSubmit={async (event) => {
              event.preventDefault();
              if (await section.save()) setEditing(false);
            }}
          >
            <p className="text-ink-soft">
              Which Google Calendar should show here? Use the shop&apos;s Gmail address, or a calendar ID from Google
              Calendar → Settings → Integrate calendar.
            </p>
            <TextField
              label="Google Calendar ID"
              placeholder="shop@gmail.com"
              value={c.googleCalendarId ?? ""}
              error={section.fieldError("googleCalendarId")}
              onChange={(e) => {
                setEditing(true);
                section.update({ googleCalendarId: e.target.value || null });
              }}
            />
            {section.error && <p className="text-sm text-bad">{section.error}</p>}
            <div className="flex gap-2">
              <button type="submit" className={buttonPrimary} disabled={section.saving}>
                {section.saving ? "Saving…" : "Save"}
              </button>
              {calendarId && (
                <button type="button" className={buttonSecondary} onClick={() => setEditing(false)}>
                  Cancel
                </button>
              )}
            </div>
          </form>
        ) : (
          <EmptyState icon={CalendarIcon} title="No calendar set up yet" />
        )
      ) : (
        <>
          <div className="mb-3 flex flex-wrap items-center gap-2">
            <div
              role="tablist"
              aria-label="Calendar view"
              className="inline-flex rounded-xl border border-line bg-panel p-1"
            >
              {MODES.map((m) => (
                <button
                  key={m.value}
                  type="button"
                  role="tab"
                  aria-selected={mode === m.value}
                  onClick={() => setMode(m.value)}
                  className={cn(
                    "rounded-lg px-4 py-2 text-sm font-medium",
                    mode === m.value ? "bg-accent text-white" : "text-ink-soft hover:text-ink",
                  )}
                >
                  {m.label}
                </button>
              ))}
            </div>
            {canEdit && (
              <button
                type="button"
                className="ml-auto text-sm text-ink-soft underline"
                onClick={() => setEditing(true)}
              >
                Change calendar
              </button>
            )}
          </div>
          <iframe
            key={`${calendarId}-${mode}`}
            title="Google Calendar"
            src={embedUrl(calendarId, mode)}
            className="h-[70vh] w-full rounded-2xl border border-line bg-white"
          />
          <p className="mt-2 text-xs text-ink-faint">
            Events not showing? Sign in to this Google account in this browser, or make the calendar visible to
            &quot;anyone with the link&quot; in Google Calendar&apos;s sharing settings.
          </p>
        </>
      )}
    </section>
  );
}
