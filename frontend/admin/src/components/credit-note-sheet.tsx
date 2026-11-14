"use client";

import type { CreditNote, CreditNoteForm, Invoice } from "@vaabhi/api-client";
import { useState } from "react";
import { ErrorNote, Pill } from "@/components/tailoring";
import { ChipGroup, LoadState, Sheet, TextAreaField, buttonPrimary, buttonSecondary } from "@/components/ui";
import { api } from "@/lib/api";
import { rupees } from "@/lib/settings";
import { formatDate, useAction, useLoad } from "@/lib/tailoring";

type Reason = "RETURN" | "CORRECTION";
type Scope = "WHOLE" | "LINES";

/** The credit notes already issued under an invoice, with the quantity left to credit on each line. */
function useCredited(invoice: Invoice) {
  const issued = useLoad(
    () => Promise.all(invoice.creditNotes.map((n) => api.admin.money.creditNotes.get(n.id))),
    [invoice.id, invoice.creditNotes.length],
  );
  const credited = new Map<number, number>();
  for (const n of issued.value ?? []) {
    for (const line of n.lines) {
      credited.set(line.invoiceLineId, (credited.get(line.invoiceLineId) ?? 0) + line.quantity);
    }
  }
  const remaining = (lineId: number, quantity: number) => Math.max(0, quantity - (credited.get(lineId) ?? 0));
  const fullyCredited =
    issued.value !== undefined && invoice.lines.every((line) => remaining(line.id, line.quantity) === 0);
  return { issued, remaining, fullyCredited };
}

/** The "Credit note" button and its sheet; hidden once everything on the invoice has been credited. */
export function CreditNoteAction({ invoice, onIssued }: { invoice: Invoice; onIssued: (note: CreditNote) => void }) {
  const [open, setOpen] = useState(false);
  const credited = useCredited(invoice);
  if (credited.fullyCredited) return null;
  return (
    <>
      <button type="button" className={buttonSecondary} onClick={() => setOpen(true)}>
        Credit note
      </button>
      {open && (
        <CreditNoteSheet
          invoice={invoice}
          credited={credited}
          onClose={() => setOpen(false)}
          onIssued={(created) => {
            setOpen(false);
            onIssued(created);
          }}
        />
      )}
    </>
  );
}

/** Issue a credit note against an invoice: the whole invoice, or chosen lines up to what is still uncredited. */
function CreditNoteSheet({
  invoice,
  credited: { issued, remaining, fullyCredited },
  onClose,
  onIssued,
}: {
  invoice: Invoice;
  credited: ReturnType<typeof useCredited>;
  onClose: () => void;
  onIssued: (note: CreditNote) => void;
}) {
  const action = useAction();
  const [scope, setScope] = useState<Scope>("WHOLE");
  const [reason, setReason] = useState<Reason>("RETURN");
  const [note, setNote] = useState("");
  const [quantities, setQuantities] = useState<Record<number, number>>({});

  const chosen = invoice.lines
    .map((line) => ({ invoiceLineId: line.id, quantity: quantities[line.id] ?? 0 }))
    .filter((line) => line.quantity > 0);
  const canIssue = !fullyCredited && (scope === "WHOLE" || chosen.length > 0);

  const issue = async () => {
    const form: CreditNoteForm = {
      reason,
      note: note.trim() || null,
      lines: scope === "WHOLE" ? [] : chosen,
    };
    const created = await action.run(() => api.admin.money.creditNotes.issue(invoice.id, form));
    if (created) onIssued(created);
  };

  return (
    <Sheet open title={`Credit note for ${invoice.number}`} onClose={onClose}>
      {!issued.value ? (
        <LoadState loading={issued.loading} error={issued.error} />
      ) : (
        <div className="flex flex-col gap-4">
          {issued.value.length > 0 && (
            <div>
              <p className="text-xs font-semibold uppercase tracking-[0.12em] text-ink-faint">Already issued</p>
              <ul className="mt-1 text-sm text-ink-soft">
                {issued.value.map((n) => (
                  <li key={n.id}>
                    {n.number} · {formatDate(n.issuedOn)} · {rupees(n.totalRupees)}
                  </li>
                ))}
              </ul>
            </div>
          )}
          <ChipGroup
            label="What to credit"
            value={scope}
            onChange={setScope}
            options={[
              {
                value: "WHOLE",
                label: issued.value.length > 0 ? "Whole invoice (what is left)" : "Whole invoice",
              },
              { value: "LINES", label: "Choose lines" },
            ]}
          />
          {scope === "LINES" && (
            <ul className="divide-y divide-line rounded-2xl border border-line">
              {invoice.lines.map((line) => {
                const left = remaining(line.id, line.quantity);
                const value = quantities[line.id] ?? 0;
                const set = (next: number) => setQuantities((q) => ({ ...q, [line.id]: next }));
                return (
                  <li key={line.id} className="flex items-center justify-between gap-3 px-4 py-3">
                    <div className="min-w-0">
                      <p className="truncate">{line.description}</p>
                      <p className="text-xs text-ink-faint">{left} left to credit</p>
                    </div>
                    {left === 0 ? (
                      <Pill>Fully credited</Pill>
                    ) : (
                      <div className="flex items-center gap-2">
                        <button
                          type="button"
                          aria-label={`Fewer ${line.description}`}
                          className={buttonSecondary}
                          disabled={value <= 0}
                          onClick={() => set(value - 1)}
                        >
                          −
                        </button>
                        <span className="w-6 text-center tabular-nums">{value}</span>
                        <button
                          type="button"
                          aria-label={`More ${line.description}`}
                          className={buttonSecondary}
                          disabled={value >= left}
                          onClick={() => set(value + 1)}
                        >
                          +
                        </button>
                      </div>
                    )}
                  </li>
                );
              })}
            </ul>
          )}
          <ChipGroup
            label="Reason"
            value={reason}
            onChange={setReason}
            options={[
              { value: "RETURN", label: "Return" },
              { value: "CORRECTION", label: "Correction" },
            ]}
          />
          <TextAreaField label="Note (optional)" value={note} onChange={(e) => setNote(e.target.value)} rows={2} />
          <ErrorNote>{action.error}</ErrorNote>
          <div className="flex gap-2">
            <button type="button" className={buttonPrimary} disabled={action.busy || !canIssue} onClick={issue}>
              Issue credit note
            </button>
            <button type="button" className={buttonSecondary} onClick={onClose}>
              Close
            </button>
          </div>
        </div>
      )}
    </Sheet>
  );
}
