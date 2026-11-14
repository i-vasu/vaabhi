"use client";

import type { ColumnMapping, FilePreview } from "@vaabhi/api-client";
import { BANK_FIELDS, COD_FIELDS, guessColumns } from "@/lib/bank";
import { SHIPROCKET_FIELDS } from "@/lib/purchases";
import { SegmentedControl, TextField } from "./ui";

export type MapperKind = "bank" | "cod" | "shiprocket";

function fieldsFor(kind: MapperKind): { key: string; label: string }[] {
  if (kind === "bank") return BANK_FIELDS;
  return kind === "cod" ? COD_FIELDS : SHIPROCKET_FIELDS;
}

/** The mapping a preview starts with: the saved profile, or one guessed from the first row. */
export function initialMapping(kind: MapperKind, preview: FilePreview): ColumnMapping {
  if (preview.saved) return preview.saved;
  return {
    headerRow: 0,
    columns: guessColumns(preview.rows[0] ?? [], fieldsFor(kind)),
    dateFormat: "dd/MM/yyyy",
    amountStyle: "DEBIT_CREDIT",
  };
}

/**
 * Lets a person tell the importer which column holds what: the header row, the date format, the amount style
 * (bank only), and a field picker above each column of the preview.
 */
export function ColumnMapper({
  kind,
  preview,
  value,
  onChange,
}: {
  kind: MapperKind;
  preview: FilePreview;
  value: ColumnMapping;
  onChange: (value: ColumnMapping) => void;
}) {
  const allFields = fieldsFor(kind);
  const fields =
    kind === "bank"
      ? allFields.filter((field) => {
          if (value.amountStyle === "DEBIT_CREDIT") return field.key !== "amount";
          return field.key !== "debit" && field.key !== "credit";
        })
      : allFields;
  const columnCount = preview.rows.reduce((max, row) => Math.max(max, row.length), 0);

  function assign(column: number, key: string) {
    const columns: Record<string, number> = {};
    for (const [field, index] of Object.entries(value.columns)) {
      if (index !== column) columns[field] = index;
    }
    if (key) columns[key] = column;
    onChange({ ...value, columns });
  }

  function fieldAt(column: number): string {
    return Object.entries(value.columns).find(([, index]) => index === column)?.[0] ?? "";
  }

  return (
    <div>
      <div className="grid gap-3 sm:grid-cols-2">
        <TextField
          label="Header row"
          type="number"
          min={1}
          value={value.headerRow + 1}
          onChange={(e) => onChange({ ...value, headerRow: Math.max(1, Number(e.target.value) || 1) - 1 })}
        />
        <TextField
          label="Date format"
          hint="e.g. dd/MM/yyyy, dd-MMM-yyyy, yyyy-MM-dd"
          value={value.dateFormat}
          onChange={(e) => onChange({ ...value, dateFormat: e.target.value })}
        />
      </div>

      {kind === "bank" && (
        <div className="mt-3">
          <p className="mb-1.5 text-sm font-medium text-ink-soft">Amount style</p>
          <SegmentedControl
            label="Amount style"
            options={[
              { value: "DEBIT_CREDIT", label: "Debit and credit columns" },
              { value: "SIGNED", label: "One signed amount" },
            ]}
            value={value.amountStyle}
            onChange={(amountStyle) => onChange({ ...value, amountStyle })}
          />
        </div>
      )}

      <div className="mt-4 overflow-x-auto rounded-2xl border border-line bg-panel">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-line bg-panel-2">
            <tr>
              {Array.from({ length: columnCount }, (_, column) => (
                <th key={column} className="p-1.5 align-middle">
                  <select
                    aria-label={`Column ${column + 1} field`}
                    className="w-full rounded-md border border-line-strong bg-field px-1.5 py-1 text-xs text-ink outline-none focus:border-accent"
                    value={fieldAt(column)}
                    onChange={(e) => assign(column, e.target.value)}
                  >
                    <option value="">—</option>
                    {fields.map((field) => (
                      <option key={field.key} value={field.key}>
                        {field.label}
                      </option>
                    ))}
                  </select>
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-line">
            {preview.rows.map((row, index) => (
              <tr
                key={index}
                className={
                  index === value.headerRow
                    ? "bg-accent-soft font-medium text-accent-ink"
                    : index < value.headerRow
                      ? "text-ink-faint"
                      : undefined
                }
              >
                {Array.from({ length: columnCount }, (_, column) => (
                  <td key={column} className="whitespace-nowrap px-2 py-1.5">
                    {row[column] ?? ""}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
