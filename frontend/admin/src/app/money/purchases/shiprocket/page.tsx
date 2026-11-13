"use client";

import Link from "next/link";
import { useState } from "react";
import type { ColumnMapping, FilePreview, ShiprocketInvoiceSummary } from "@vaabhi/api-client";
import { ColumnMapper, initialMapping } from "@/components/column-mapper";
import { ErrorNote } from "@/components/tailoring";
import { PageHeader, SectionLabel, buttonPrimary, buttonSecondary } from "@/components/ui";
import { api } from "@/lib/api";
import { shiprocketMappingProblem } from "@/lib/purchases";
import { rupees } from "@/lib/settings";
import { useAction } from "@/lib/tailoring";

/** Imports Shiprocket's freight invoices as bills: pick the file, map its columns, then see what came in. */
export default function ShiprocketPage() {
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<FilePreview | null>(null);
  const [mapping, setMapping] = useState<ColumnMapping | null>(null);
  const [result, setResult] = useState<ShiprocketInvoiceSummary | null>(null);
  const previewing = useAction();
  const importing = useAction();

  const problem = mapping ? shiprocketMappingProblem(mapping) : null;

  async function choose(selected: File) {
    setFile(selected);
    setPreview(null);
    setMapping(null);
    setResult(null);
    const loaded = await previewing.run(() => api.admin.purchases.previewShiprocket(selected));
    if (loaded) {
      setPreview(loaded);
      setMapping(initialMapping("shiprocket", loaded));
    }
  }

  async function importNow() {
    if (!file || !mapping || problem) return;
    setResult(null);
    const summary = await importing.run(() => api.admin.purchases.importShiprocket(file, mapping));
    if (summary) setResult(summary);
  }

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader title="Shiprocket invoices" backHref="/money/purchases/" />
      <ErrorNote>{previewing.error ?? importing.error}</ErrorNote>

      {result ? (
        <div className="space-y-4">
          <p className="font-medium">
            {result.invoicesCreated} {result.invoicesCreated === 1 ? "invoice" : "invoices"} created,{" "}
            {result.invoicesSkipped} already imported, {rupees(result.totalRupees)} billed.
          </p>
          {result.skippedInvoiceNos.length > 0 && (
            <div>
              <SectionLabel>Already recorded</SectionLabel>
              <ul className="space-y-1 text-sm text-ink-soft">
                {result.skippedInvoiceNos.map((invoiceNo) => (
                  <li key={invoiceNo}>{invoiceNo}</li>
                ))}
              </ul>
            </div>
          )}
          {result.badRows.length > 0 && (
            <div>
              <SectionLabel>Skipped rows</SectionLabel>
              <ul className="space-y-1 text-sm text-warn">
                {result.badRows.map((row) => (
                  <li key={`${row.rowNumber}-${row.reason}`}>
                    Row {row.rowNumber}: {row.reason}
                  </li>
                ))}
              </ul>
            </div>
          )}
          <Link href="/money/purchases/bills/" className={buttonSecondary}>
            Open the bills
          </Link>
        </div>
      ) : (
        <>
          <SectionLabel>1. Choose the file</SectionLabel>
          <label className={buttonSecondary}>
            {preview ? "Choose another file" : "Choose file"}
            <input
              type="file"
              accept=".csv,.xlsx"
              className="sr-only"
              aria-label="Shiprocket invoice file"
              disabled={previewing.busy || importing.busy}
              onChange={(event) => {
                const selected = event.target.files?.[0];
                event.target.value = "";
                if (selected) void choose(selected);
              }}
            />
          </label>
          {file && <p className="mt-2 text-sm text-ink-soft">{file.name}</p>}
          {previewing.busy && <p className="mt-2 text-sm text-ink-soft">Reading the file…</p>}

          {preview && mapping && (
            <>
              <SectionLabel>2. Match the columns</SectionLabel>
              <ColumnMapper kind="shiprocket" preview={preview} value={mapping} onChange={setMapping} />
              {problem && <p className="mt-4 text-sm text-bad">{problem}</p>}
              <div className="mt-4">
                <button
                  type="button"
                  className={buttonPrimary}
                  disabled={importing.busy || Boolean(problem)}
                  onClick={() => void importNow()}
                >
                  {importing.busy ? "Importing…" : "Import invoices"}
                </button>
              </div>
            </>
          )}
        </>
      )}
    </section>
  );
}
