"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import type { BankImportSummary, CodImportSummary, ColumnMapping, FilePreview } from "@vaabhi/api-client";
import { ColumnMapper, initialMapping, type MapperKind } from "@/components/column-mapper";
import { ErrorNote } from "@/components/tailoring";
import { PageHeader, SectionLabel, TextField, buttonPrimary, buttonSecondary } from "@/components/ui";
import { api } from "@/lib/api";
import { bankMappingProblem, codMappingProblem } from "@/lib/bank";
import { parseRupees } from "@/lib/settings";
import { useAction } from "@/lib/tailoring";

type Result = { kind: "bank"; summary: BankImportSummary } | { kind: "cod"; summary: CodImportSummary };

function UploadView({ kind }: { kind: MapperKind }) {
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<FilePreview | null>(null);
  const [mapping, setMapping] = useState<ColumnMapping | null>(null);
  const [opening, setOpening] = useState("");
  const [closing, setClosing] = useState("");
  const [result, setResult] = useState<Result | null>(null);
  const previewing = useAction();
  const importing = useAction();
  const sweep = useAction();

  const openingRupees = opening.trim() === "" ? null : parseRupees(opening);
  const closingRupees = closing.trim() === "" ? null : parseRupees(closing);
  const badBalance =
    (opening.trim() !== "" && openingRupees === null) || (closing.trim() !== "" && closingRupees === null);
  const problem = mapping ? (kind === "bank" ? bankMappingProblem(mapping) : codMappingProblem(mapping)) : null;

  async function choose(selected: File) {
    setFile(selected);
    setPreview(null);
    setMapping(null);
    setResult(null);
    const loaded = await previewing.run(() =>
      kind === "cod"
        ? api.admin.accounts.bank.previewRemittance(selected)
        : api.admin.accounts.bank.previewStatement(selected),
    );
    if (loaded) {
      setPreview(loaded);
      setMapping(initialMapping(kind, loaded));
    }
  }

  async function importNow() {
    if (!file || !mapping || problem || badBalance) return;
    setResult(null);
    if (kind === "bank") {
      const summary = await importing.run(() =>
        api.admin.accounts.bank.importStatement(file, { mapping, openingRupees, closingRupees }),
      );
      if (summary) setResult({ kind: "bank", summary });
    } else {
      const summary = await importing.run(() => api.admin.accounts.bank.importRemittance(file, mapping));
      if (summary) {
        setResult({ kind: "cod", summary });
        await sweep.run(() => api.admin.accounts.sweep());
      }
    }
  }

  const title = kind === "cod" ? "Upload remittance report" : "Upload statement";

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader title={title} backHref="/money/accounts/bank/" />
      <ErrorNote>{previewing.error ?? importing.error}</ErrorNote>

      {result ? (
        <div className="space-y-4">
          {result.kind === "bank" ? (
            <>
              <p className="font-medium">
                {result.summary.added} new lines, {result.summary.duplicates} already imported,{" "}
                {result.summary.autoMatched} matched automatically.
              </p>
              {result.summary.skipped.length > 0 && (
                <div>
                  <SectionLabel>Skipped rows</SectionLabel>
                  <ul className="space-y-1 text-sm text-warn">
                    {result.summary.skipped.map((row) => (
                      <li key={row.rowNumber}>
                        Row {row.rowNumber}: {row.reason}
                      </li>
                    ))}
                  </ul>
                </div>
              )}
              <Link
                href={`/money/accounts/bank/statement/?id=${result.summary.statementId}`}
                className={buttonSecondary}
              >
                Open statement
              </Link>
            </>
          ) : (
            <>
              <p className="font-medium">
                {result.summary.added} added, {result.summary.duplicates} already imported.
              </p>
              {sweep.busy && <p className="text-sm text-ink-soft">Posting remittances…</p>}
              <ErrorNote>{sweep.error}</ErrorNote>
              {result.summary.unknownAwbs.length > 0 && (
                <div className="rounded-xl bg-warn-soft px-4 py-3 text-warn">
                  <p className="font-medium">Unknown AWBs</p>
                  <ul className="mt-1 space-y-1 text-sm">
                    {result.summary.unknownAwbs.map((awb) => (
                      <li key={awb}>{awb}</li>
                    ))}
                  </ul>
                </div>
              )}
              <Link href="/money/accounts/bank/" className={buttonSecondary}>
                Back to bank reconciliation
              </Link>
            </>
          )}
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
              aria-label="Statement file"
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
              <ColumnMapper kind={kind} preview={preview} value={mapping} onChange={setMapping} />

              {kind === "bank" && (
                <div className="mt-4 grid gap-3 sm:grid-cols-2">
                  <TextField
                    label="Opening balance"
                    hint="Blank = from the file"
                    inputMode="decimal"
                    value={opening}
                    error={opening.trim() !== "" && openingRupees === null ? "Not a rupee amount" : undefined}
                    onChange={(e) => setOpening(e.target.value)}
                  />
                  <TextField
                    label="Closing balance"
                    hint="Blank = from the file"
                    inputMode="decimal"
                    value={closing}
                    error={closing.trim() !== "" && closingRupees === null ? "Not a rupee amount" : undefined}
                    onChange={(e) => setClosing(e.target.value)}
                  />
                </div>
              )}

              {problem && <p className="mt-4 text-sm text-bad">{problem}</p>}
              <div className="mt-4">
                <button
                  type="button"
                  className={buttonPrimary}
                  disabled={importing.busy || !!problem || badBalance}
                  onClick={importNow}
                >
                  {importing.busy ? "Importing…" : "Import"}
                </button>
              </div>
            </>
          )}
        </>
      )}
    </section>
  );
}

function Route() {
  const kind: MapperKind = useSearchParams().get("kind") === "cod" ? "cod" : "bank";
  return <UploadView key={kind} kind={kind} />;
}

/** Picks a bank statement or COD remittance file, maps its columns, imports it and shows what happened. */
export default function UploadPage() {
  return (
    <Suspense fallback={<p className="text-ink-soft">Loading…</p>}>
      <Route />
    </Suspense>
  );
}
