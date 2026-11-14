"use client";

import { useState } from "react";
import type { BackupStatus } from "@vaabhi/api-client";
import { DownloadIcon } from "@/components/icons";
import { ErrorNote } from "@/components/tailoring";
import {
  EmptyState,
  LoadState,
  PageHeader,
  SectionLabel,
  Sheet,
  StatTile,
  TextAreaField,
  TextField,
  buttonPrimary,
  buttonSecondary,
} from "@/components/ui";
import { relativeTime, useIsOwner } from "@/lib/accounts";
import { api } from "@/lib/api";
import { orNull } from "@/lib/settings";
import { formatDate, today, useAction, useLoad } from "@/lib/tailoring";

const KIND_LABELS: Record<string, string> = { DB: "Backup", CHECK: "Check", DRILL: "Drill" };

const dateTime = new Intl.DateTimeFormat("en-IN", {
  day: "numeric",
  month: "short",
  hour: "2-digit",
  minute: "2-digit",
});

/** "14 Nov, 01:04 am" in the browser's time zone. */
function localDateTime(iso: string): string {
  return dateTime.format(new Date(iso));
}

/** The browser-local calendar day of a date-time, as yyyy-mm-dd. */
function localDay(iso: string): string {
  const date = new Date(iso);
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 10);
}

/** Bytes as megabytes to one decimal place, or a dash when the run has no size. */
function megabytes(bytes: number | null): string {
  return bytes === null ? "—" : (bytes / 1024 / 1024).toFixed(1);
}

function statusTone(status: string): string {
  if (status === "OK") return "text-ok";
  if (status === "FAILED") return "text-bad";
  return "text-ink-faint";
}

/** Records a restore drill: the day it was done and an optional note. */
function DrillSheet({ onClose, onSaved }: { onClose: () => void; onSaved: (next: BackupStatus) => void }) {
  const [drilledOn, setDrilledOn] = useState(today());
  const [note, setNote] = useState("");
  const action = useAction();

  async function save() {
    const saved = await action.run(() => api.admin.accounts.backups.recordDrill({ drilledOn, note: orNull(note) }));
    if (saved) onSaved(saved);
  }

  return (
    <Sheet open title="Mark restore drill done" onClose={onClose}>
      <p className="mb-4 text-sm text-ink-soft">
        Record the day you restored a backup and checked it, as in docs/RESTORE.md.
      </p>
      <div className="space-y-3">
        <TextField label="Date" type="date" value={drilledOn} onChange={(e) => setDrilledOn(e.target.value)} />
        <TextAreaField
          label="Note"
          maxLength={500}
          value={note}
          onChange={(e) => setNote(e.target.value)}
          hint="What you restored and what you checked."
        />
      </div>
      <ErrorNote>{action.error}</ErrorNote>
      <div className="mt-4 flex gap-3">
        <button type="button" className={buttonPrimary} disabled={action.busy || !drilledOn} onClick={save}>
          {action.busy ? "Saving…" : "Save"}
        </button>
        <button type="button" className={buttonSecondary} onClick={onClose}>
          Cancel
        </button>
      </div>
    </Sheet>
  );
}

/** Backup health and the recent runs; the owner can run one now and record a restore drill. */
export default function BackupsPage() {
  const owner = useIsOwner();
  const backups = useLoad(() => api.admin.accounts.backups.status(), []);
  const runAction = useAction();
  const [drilling, setDrilling] = useState(false);
  const s = backups.value;

  async function backupNow() {
    const next = await runAction.run(() => api.admin.accounts.backups.runNow());
    if (next) backups.setValue(next);
  }

  const failed = s?.lastBackup?.status === "FAILED";
  const lastSize = s?.lastBackup?.sizeBytes ?? null;
  const message = s
    ? !s.configured
      ? "Backups aren't set up. See the setup guide in infra/vps-setup.md."
      : failed
        ? `Last backup failed: ${s.lastBackup?.message ?? "no reason given"}`
        : s.missedLastNight
          ? "Last night's backup didn't run"
          : null
    : null;

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader
        title="Backups"
        backHref="/money/accounts/"
        actions={
          owner && (
            <>
              <button type="button" className={buttonSecondary} onClick={() => setDrilling(true)}>
                Mark restore drill done
              </button>
              <button type="button" className={buttonPrimary} disabled={runAction.busy} onClick={backupNow}>
                {runAction.busy ? "Backing up…" : "Back up now"}
              </button>
            </>
          )
        }
      />
      <ErrorNote>{backups.error ?? runAction.error}</ErrorNote>
      {!s ? (
        <LoadState loading={backups.loading} error={null} />
      ) : (
        <>
          {message && (
            <p
              className={`mb-4 rounded-xl px-4 py-3 ${
                s.configured ? "bg-bad-soft text-bad" : "bg-warn-soft text-warn"
              }`}
            >
              {message}
            </p>
          )}

          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <StatTile
              label="Last backup"
              value={s.lastBackup ? relativeTime(s.lastBackup.finishedAt ?? s.lastBackup.startedAt) : "Never"}
              hint={lastSize === null ? undefined : `${megabytes(lastSize)} MB`}
              tone={failed || s.missedLastNight ? "bad" : s.lastBackup ? "ok" : "warn"}
            />
            <StatTile
              label="Last check"
              value={s.lastCheck ? formatDate(localDay(s.lastCheck.startedAt)) : "Never"}
              tone={s.lastCheck?.status === "FAILED" ? "bad" : undefined}
            />
            <StatTile
              label="Restore drill"
              value={s.lastDrill?.drilledOn ? formatDate(s.lastDrill.drilledOn) : "Not done"}
              tone={s.lastDrill ? undefined : "warn"}
            />
            <StatTile
              label="Missed nights"
              value={s.missedDaysLast7}
              hint="In the last 7 days"
              tone={s.missedDaysLast7 > 0 ? "warn" : "ok"}
            />
          </div>
          <p className="mt-3 text-sm text-ink-soft">
            {s.locationLabel}
            {s.bucket ? ` · Bucket ${s.bucket}` : ""}
          </p>

          <SectionLabel>Recent runs</SectionLabel>
          {s.recent.length === 0 ? (
            <EmptyState icon={DownloadIcon} title="No backups yet">
              The first backup runs at 01:00 once the storage is set up.
            </EmptyState>
          ) : (
            <div className="overflow-x-auto rounded-2xl border border-line bg-panel">
              <table className="w-full min-w-[44rem] text-left text-sm">
                <thead className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
                  <tr>
                    <th className="px-4 py-2 font-medium">Kind</th>
                    <th className="px-4 py-2 font-medium">Status</th>
                    <th className="px-4 py-2 font-medium">Started</th>
                    <th className="px-4 py-2 text-right font-medium">Size (MB)</th>
                    <th className="px-4 py-2 font-medium">Details</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {s.recent.map((run) => (
                    <tr key={run.id}>
                      <td className="whitespace-nowrap px-4 py-3">{KIND_LABELS[run.kind] ?? run.kind}</td>
                      <td className={`whitespace-nowrap px-4 py-3 font-medium ${statusTone(run.status)}`}>
                        {run.status}
                      </td>
                      <td className="whitespace-nowrap px-4 py-3 tabular-nums">{localDateTime(run.startedAt)}</td>
                      <td className="whitespace-nowrap px-4 py-3 text-right tabular-nums">
                        {megabytes(run.sizeBytes)}
                      </td>
                      <td className="px-4 py-3">
                        {!run.message && !run.notes && run.attachmentsCopied === 0 && !run.drilledOn ? (
                          <span className="text-ink-faint">—</span>
                        ) : (
                          <div className="space-y-1">
                            {run.message && <p>{run.message}</p>}
                            {run.notes && <p className="text-ink-soft">{run.notes}</p>}
                            {run.attachmentsCopied > 0 && (
                              <p className="text-ink-faint">Attachments copied: {run.attachmentsCopied}</p>
                            )}
                            {run.drilledOn && <p className="text-ink-soft">Drilled {formatDate(run.drilledOn)}</p>}
                          </div>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <p className="mt-3 text-sm text-ink-faint">
            Backups run every night at 01:00. See docs/RESTORE.md for the restore drill.
          </p>
        </>
      )}

      {drilling && (
        <DrillSheet
          onClose={() => setDrilling(false)}
          onSaved={(next) => {
            backups.setValue(next);
            setDrilling(false);
          }}
        />
      )}
    </section>
  );
}
