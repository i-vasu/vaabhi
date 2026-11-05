"use client";

import { useEffect, useState } from "react";
import type { GarmentField, GarmentTemplate } from "@vaabhi/api-client";
import { PlusIcon, RulerIcon, TrashIcon } from "@/components/icons";
import {
  EmptyState,
  ListCard,
  ListRow,
  LoadState,
  PageHeader,
  SelectField,
  Sheet,
  TextField,
  buttonPrimary,
  buttonSecondary,
} from "@/components/ui";
import { api } from "@/lib/api";
import { describeError } from "@/lib/settings";

const audiences: { value: GarmentTemplate["businessType"]; label: string }[] = [
  { value: "ALL", label: "Everyone" },
  { value: "LADIES", label: "Ladies" },
  { value: "GENTS", label: "Gents" },
  { value: "KIDS", label: "Kids" },
];

/** "Sleeve length" → "sleeve_length": the stable key measurements are stored under. */
export function fieldKey(label: string): string {
  const key = label
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "");
  return /^[a-z]/.test(key) ? key : `m_${key}`;
}

const blank: GarmentTemplate = { id: null, name: "", businessType: "ALL", fields: [] };

export default function GarmentTemplatesPage() {
  const [templates, setTemplates] = useState<GarmentTemplate[]>();
  const [loadError, setLoadError] = useState<string | null>(null);
  const [editing, setEditing] = useState<GarmentTemplate | null>(null);

  const reload = () =>
    api.admin.settings
      .garmentTemplates()
      .then(setTemplates)
      .catch((e: unknown) => setLoadError(describeError(e).message));

  useEffect(() => {
    void reload();
  }, []);

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader
        title="Garment templates"
        backHref="/settings/orders/"
        actions={
          <button type="button" className={buttonSecondary} onClick={() => setEditing(blank)}>
            <PlusIcon size={18} /> Add garment
          </button>
        }
      />
      <p className="mb-4 text-ink-soft">
        The measurement fields of each garment appear in New order and Add measurement.
      </p>
      {!templates ? (
        <LoadState loading={!loadError} error={loadError} />
      ) : templates.length === 0 ? (
        <EmptyState icon={RulerIcon} title="No garments yet">
          Add a garment with the measurements your tailors take for it.
        </EmptyState>
      ) : (
        <ListCard>
          {templates.map((template) => (
            <button
              key={template.id}
              type="button"
              className="block w-full text-left hover:bg-panel-2"
              onClick={() => setEditing(template)}
            >
              <ListRow
                icon={RulerIcon}
                title={template.name}
                subtitle={template.fields.map((field) => field.label).join(", ")}
                trailing={audiences.find((a) => a.value === template.businessType)?.label}
              />
            </button>
          ))}
        </ListCard>
      )}
      {editing && (
        <TemplateSheet
          template={editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            void reload();
          }}
        />
      )}
    </section>
  );
}

function TemplateSheet({
  template,
  onClose,
  onSaved,
}: {
  template: GarmentTemplate;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [draft, setDraft] = useState(template);
  const [newField, setNewField] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const setField = (index: number, field: GarmentField) =>
    setDraft({ ...draft, fields: draft.fields.map((f, i) => (i === index ? field : f)) });

  function addField() {
    const label = newField.trim();
    if (!label) return;
    setDraft({ ...draft, fields: [...draft.fields, { key: fieldKey(label), label, unit: "in" }] });
    setNewField("");
  }

  async function run(action: () => Promise<unknown>) {
    setBusy(true);
    setError(null);
    try {
      await action();
      onSaved();
    } catch (e) {
      setError(describeError(e).message);
      setBusy(false);
    }
  }

  return (
    <Sheet open title={template.id ? "Edit garment" : "New garment"} onClose={onClose}>
      <div className="flex flex-col gap-4">
        <TextField
          label="Garment name"
          value={draft.name}
          maxLength={60}
          placeholder="Blouse"
          onChange={(event) => setDraft({ ...draft, name: event.target.value })}
        />
        <SelectField
          label="For"
          options={audiences}
          value={draft.businessType}
          onChange={(businessType) => setDraft({ ...draft, businessType })}
        />
        <div>
          <p className="mb-2 text-sm font-medium text-ink-soft">Measurements</p>
          <ul className="flex flex-col gap-2">
            {draft.fields.map((field, index) => (
              <li key={field.key} className="flex items-center gap-2">
                <input
                  aria-label={`Measurement ${index + 1}`}
                  value={field.label}
                  onChange={(event) => setField(index, { ...field, label: event.target.value })}
                  className="min-w-0 flex-1 rounded-xl border border-line-strong bg-field px-3 py-2"
                />
                <select
                  aria-label={`Unit for ${field.label}`}
                  value={field.unit}
                  onChange={(event) => setField(index, { ...field, unit: event.target.value })}
                  className="rounded-xl border border-line-strong bg-field px-2 py-2"
                >
                  <option value="in">in</option>
                  <option value="cm">cm</option>
                  <option value="">text</option>
                </select>
                <button
                  type="button"
                  aria-label={`Remove ${field.label}`}
                  onClick={() => setDraft({ ...draft, fields: draft.fields.filter((_, i) => i !== index) })}
                  className="rounded-lg p-2 text-bad hover:bg-bad-soft"
                >
                  <TrashIcon size={18} />
                </button>
              </li>
            ))}
          </ul>
          <div className="mt-2 flex gap-2">
            <input
              aria-label="New measurement"
              placeholder="e.g. Sleeve length"
              value={newField}
              onChange={(event) => setNewField(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter") {
                  event.preventDefault();
                  addField();
                }
              }}
              className="min-w-0 flex-1 rounded-xl border border-line-strong bg-field px-3 py-2"
            />
            <button type="button" className={buttonSecondary} onClick={addField}>
              Add
            </button>
          </div>
        </div>
        {error && (
          <p role="alert" className="text-bad">
            {error}
          </p>
        )}
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            className={buttonPrimary}
            disabled={busy}
            onClick={() =>
              void run(() =>
                draft.id
                  ? api.admin.settings.updateGarmentTemplate(draft.id, draft)
                  : api.admin.settings.createGarmentTemplate(draft),
              )
            }
          >
            {busy ? "Saving…" : "Save garment"}
          </button>
          {draft.id && (
            <button
              type="button"
              className={`${buttonSecondary} text-bad`}
              disabled={busy}
              onClick={() => void run(() => api.admin.settings.deleteGarmentTemplate(draft.id!))}
            >
              <TrashIcon size={18} /> Remove
            </button>
          )}
        </div>
      </div>
    </Sheet>
  );
}
