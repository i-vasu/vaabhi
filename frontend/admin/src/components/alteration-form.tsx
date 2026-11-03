"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import type { AlterationForm, AlterationKind, ClientSummary } from "@vaabhi/api-client";
import { api } from "@/lib/api";
import { newKey } from "@/lib/product-editor";
import { describeError, parseRupees, rupees } from "@/lib/settings";
import { ALTERATION_KINDS, addDays, today, useLoad } from "@/lib/tailoring";
import { PlusIcon, TrashIcon } from "./icons";
import { ClientPicker, PhotoInput } from "./tailoring";
import { SaveBar, SectionLabel, SelectField, TextAreaField, TextField, ToggleRow, buttonSecondary } from "./ui";

type Row = {
  key: string;
  kind: AlterationKind;
  garment: string;
  instructions: string;
  photoKey: string | null;
  cost: string;
};

type Draft = { readyBy: string; paid: boolean; items: Row[]; notes: string; accepted: boolean };

const blankRow = (): Row => ({
  key: newKey(),
  kind: "FIT_ADJUSTMENT",
  garment: "",
  instructions: "",
  photoKey: null,
  cost: "",
});

/** Turns the draft into the API form, or explains what is still missing. */
export function alterationFromDraft(
  clientId: number | null,
  orderId: number | null,
  draft: Draft,
): { form: AlterationForm; error: null } | { form: null; error: string } {
  if (clientId === null) return { form: null, error: "Choose the customer." };
  const items = [];
  for (const [index, row] of draft.items.entries()) {
    if (!row.garment.trim()) return { form: null, error: `Garment ${index + 1}: say what it is.` };
    const costRupees = !draft.paid || row.cost.trim() === "" ? 0 : parseRupees(row.cost);
    if (costRupees === null) return { form: null, error: `Garment ${index + 1}: enter the charge as a number.` };
    items.push({
      kind: row.kind,
      garment: row.garment.trim(),
      instructions: row.instructions.trim() || null,
      photoKey: row.photoKey,
      costRupees,
    });
  }
  if (!draft.accepted) return { form: null, error: "The customer must accept the alteration terms." };
  return {
    error: null,
    form: {
      clientId,
      orderId,
      readyBy: draft.readyBy,
      paid: draft.paid,
      items,
      notes: draft.notes.trim() || null,
      undertakingAccepted: true,
    },
  };
}

export function AlterationEditor({
  initialClient,
  orderId,
}: {
  initialClient: ClientSummary | null;
  /** The order the garments were stitched on, when booking from an order. */
  orderId: number | null;
}) {
  const router = useRouter();
  const [client, setClient] = useState(initialClient);
  const [draft, setDraft] = useState<Draft>(() => ({
    readyBy: addDays(today(), 2),
    // Fixes on the shop's own work are usually free.
    paid: orderId === null,
    items: [blankRow()],
    notes: "",
    accepted: false,
  }));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const documents = useLoad(() => api.admin.settings.get("documents"), []);

  const update = (patch: Partial<Draft>) => setDraft((d) => ({ ...d, ...patch }));
  const updateRow = (key: string, patch: Partial<Row>) =>
    setDraft((d) => ({ ...d, items: d.items.map((row) => (row.key === key ? { ...row, ...patch } : row)) }));
  const total = draft.paid ? draft.items.reduce((sum, row) => sum + (parseRupees(row.cost) ?? 0), 0) : 0;

  async function save() {
    const result = alterationFromDraft(client?.id ?? null, orderId, draft);
    if (result.error !== null) {
      setError(result.error);
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const saved = await api.admin.alterations.create(result.form);
      router.replace(`/alterations/view/?id=${saved.id}`);
    } catch (e) {
      setError(describeError(e).message);
      setSaving(false);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <SectionLabel>Customer</SectionLabel>
      {orderId !== null && client ? (
        <p className="rounded-xl border border-line bg-panel-2 px-4 py-3">
          <span className="font-medium">{client.name}</span>
          <span className="text-ink-soft"> · garments from their order</span>
        </p>
      ) : (
        <ClientPicker value={client} onChange={setClient} />
      )}

      <SectionLabel
        action={
          <button
            type="button"
            className={buttonSecondary}
            onClick={() => update({ items: [...draft.items, blankRow()] })}
          >
            <PlusIcon size={18} /> Add garment
          </button>
        }
      >
        Garments
      </SectionLabel>
      <div className="rounded-2xl border border-line bg-panel">
        <ToggleRow
          title="Charge for this alteration"
          subtitle={draft.paid ? `Total ${rupees(total)}` : "Free — no charge to the customer"}
          checked={draft.paid}
          onChange={(paid) => update({ paid })}
        />
      </div>
      {draft.items.map((row, index) => (
        <fieldset key={row.key} className="rounded-2xl border border-line bg-panel p-4">
          <legend className="sr-only">Garment {index + 1}</legend>
          <div className="mb-3 flex items-center">
            <span className="font-medium">Garment {index + 1}</span>
            {draft.items.length > 1 && (
              <button
                type="button"
                aria-label={`Remove garment ${index + 1}`}
                className="ml-auto rounded-lg p-2 text-bad hover:bg-bad-soft"
                onClick={() => update({ items: draft.items.filter((other) => other.key !== row.key) })}
              >
                <TrashIcon size={18} />
              </button>
            )}
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <TextField
              label="Garment"
              maxLength={200}
              placeholder="Blue silk blouse"
              value={row.garment}
              onChange={(event) => updateRow(row.key, { garment: event.target.value })}
            />
            <SelectField
              label="Work"
              options={ALTERATION_KINDS}
              value={row.kind}
              onChange={(kind) => updateRow(row.key, { kind })}
            />
          </div>
          <TextField
            className="mt-3"
            label="Instructions"
            maxLength={500}
            placeholder="Take in 1 inch at the waist"
            value={row.instructions}
            onChange={(event) => updateRow(row.key, { instructions: event.target.value })}
          />
          <div className="mt-3 flex flex-wrap items-end gap-4">
            <PhotoInput
              label="Garment photo"
              purpose="ALTERATION"
              value={row.photoKey}
              onChange={(photoKey) => updateRow(row.key, { photoKey })}
            />
            {draft.paid && (
              <TextField
                className="ml-auto w-32"
                label="Charge (₹)"
                inputMode="decimal"
                value={row.cost}
                onChange={(event) => updateRow(row.key, { cost: event.target.value })}
              />
            )}
          </div>
        </fieldset>
      ))}

      <SectionLabel>Details</SectionLabel>
      <TextField
        label="Ready by"
        type="date"
        min={today()}
        value={draft.readyBy}
        onChange={(event) => update({ readyBy: event.target.value })}
      />
      <TextAreaField
        label="Notes"
        maxLength={1000}
        value={draft.notes}
        onChange={(event) => update({ notes: event.target.value })}
      />

      <div className="rounded-2xl border border-line bg-panel p-4">
        <p className="text-sm text-ink-soft">{documents.value?.alterationUndertaking || UNDERTAKING}</p>
        <label className="mt-3 flex items-center gap-3 font-medium">
          <input
            type="checkbox"
            className="size-5 accent-accent"
            checked={draft.accepted}
            onChange={(event) => update({ accepted: event.target.checked })}
          />
          The customer accepts these terms
        </label>
      </div>
      <SaveBar saving={saving} saved={false} error={error} onSave={() => void save()} label="Book alteration" />
    </div>
  );
}

/** Matches the API's default wording, shown until the shop writes its own in Settings → Billing → Documents. */
const UNDERTAKING = "I understand that alterations are best-effort and the final fit is not guaranteed.";
