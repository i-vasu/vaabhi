"use client";

import { useState } from "react";
import type { RequestForm, RequestKind, StaffMember, StaffRequest } from "@vaabhi/api-client";
import { parseRupees, rupees } from "@/lib/settings";
import { REQUEST_KINDS, REQUEST_TONES, requestLabel, spansDays } from "@/lib/staff";
import { formatDate, today, useAction } from "@/lib/tailoring";
import { ErrorNote, Pill } from "./tailoring";
import { SelectField, Sheet, TextField, buttonPrimary } from "./ui";

/** The API form, or what still needs filling in. */
export function requestFromDraft(draft: {
  staffId: number | null;
  kind: RequestKind;
  fromDate: string;
  toDate: string;
  hours: string;
  amount: string;
  reason: string;
}): { form: RequestForm; error: null } | { form: null; error: string } {
  if (!draft.fromDate) return { form: null, error: "Pick the day." };
  const span = spansDays(draft.kind);
  if (span && draft.toDate && draft.toDate < draft.fromDate)
    return { form: null, error: "The last day is before the first." };
  const hours = draft.kind === "PERMISSION" ? Number(draft.hours) : null;
  if (hours !== null && !(hours >= 0.5 && hours <= 8)) return { form: null, error: "Permission is 0.5 to 8 hours." };
  const amountRupees = draft.kind === "ADVANCE" ? parseRupees(draft.amount) : null;
  if (draft.kind === "ADVANCE" && !amountRupees) return { form: null, error: "Enter the advance amount." };
  return {
    error: null,
    form: {
      staffId: draft.staffId,
      kind: draft.kind,
      fromDate: draft.fromDate,
      toDate: span ? draft.toDate || draft.fromDate : null,
      hours,
      amountRupees,
      reason: draft.reason.trim() || null,
    },
  };
}

/** Raise a request, for yourself or (with `staff`) for someone else. */
export function RequestSheet({
  staff,
  onClose,
  onSubmit,
}: {
  staff?: StaffMember[];
  onClose: () => void;
  onSubmit: (form: RequestForm) => Promise<StaffRequest>;
}) {
  const [staffId, setStaffId] = useState<string>(staff?.[0] ? String(staff[0].id) : "");
  const [kind, setKind] = useState<RequestKind>("LEAVE");
  const [fromDate, setFromDate] = useState(today());
  const [toDate, setToDate] = useState("");
  const [hours, setHours] = useState("2");
  const [amount, setAmount] = useState("");
  const [reason, setReason] = useState("");
  const action = useAction();

  return (
    <Sheet open title="New request" onClose={onClose}>
      <div className="flex flex-col gap-4">
        {staff && (
          <SelectField
            label="For"
            options={staff.map((s) => ({ value: String(s.id), label: s.name }))}
            value={staffId}
            onChange={setStaffId}
          />
        )}
        <SelectField label="Kind" options={REQUEST_KINDS} value={kind} onChange={setKind} />
        <div className="grid grid-cols-2 gap-3">
          <TextField
            label={spansDays(kind) ? "From" : "Day"}
            type="date"
            value={fromDate}
            onChange={(e) => setFromDate(e.target.value)}
          />
          {spansDays(kind) && (
            <TextField
              label="To"
              type="date"
              min={fromDate}
              value={toDate}
              onChange={(e) => setToDate(e.target.value)}
            />
          )}
          {kind === "PERMISSION" && (
            <TextField label="Hours" inputMode="decimal" value={hours} onChange={(e) => setHours(e.target.value)} />
          )}
          {kind === "ADVANCE" && (
            <TextField
              label="Amount (₹)"
              inputMode="decimal"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
            />
          )}
        </div>
        <TextField label="Reason" maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} />
        <ErrorNote>{action.error}</ErrorNote>
        <button
          type="button"
          className={buttonPrimary}
          disabled={action.busy}
          onClick={async () => {
            const result = requestFromDraft({
              staffId: staff ? Number(staffId) : null,
              kind,
              fromDate,
              toDate,
              hours,
              amount,
              reason,
            });
            if (result.error !== null) return action.setError(result.error);
            const saved = await action.run(() => onSubmit(result.form));
            if (saved) onClose();
          }}
        >
          {action.busy ? "Sending…" : "Send request"}
        </button>
      </div>
    </Sheet>
  );
}

/** "Leave · 6 Aug – 8 Aug" etc. */
export function RequestSummary({ request: r, showName = false }: { request: StaffRequest; showName?: boolean }) {
  return (
    <>
      <span className="flex flex-wrap items-center gap-2">
        {showName && <span className="font-medium">{r.staffName}</span>}
        <span className={showName ? "text-ink-soft" : "font-medium"}>{requestLabel(r.kind)}</span>
        <Pill tone={REQUEST_TONES[r.status]}>{r.status.toLowerCase()}</Pill>
      </span>
      <span className="block text-sm text-ink-soft">
        {formatDate(r.fromDate)}
        {r.toDate !== r.fromDate && ` – ${formatDate(r.toDate)}`}
        {r.hours && ` · ${r.hours} h`}
        {r.amountRupees && ` · ${rupees(r.amountRupees)}`}
        {r.reason && ` · ${r.reason}`}
        {r.decisionNote && ` · “${r.decisionNote}”`}
      </span>
    </>
  );
}
