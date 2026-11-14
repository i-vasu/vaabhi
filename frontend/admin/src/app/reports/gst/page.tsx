"use client";

import Link from "next/link";
import { useState } from "react";
import { DownloadIcon } from "@/components/icons";
import { ErrorNote } from "@/components/tailoring";
import { LoadState, PageHeader, TextField, buttonSecondary } from "@/components/ui";
import type { Gstr1DocRange, Gstr1HsnRow, Gstr1Row } from "@vaabhi/api-client";
import { api } from "@/lib/api";
import { rupees } from "@/lib/settings";
import { useLoad } from "@/lib/tailoring";

/** The current month ("2026-11") in India, whatever the browser's own time zone. */
function thisMonth(): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Kolkata", year: "numeric", month: "2-digit" })
    .format(new Date())
    .slice(0, 7);
}

const sum = (values: number[]) => values.reduce((a, b) => a + b, 0);
const cell = (value: unknown) => (value === null || value === undefined || value === "" ? "—" : String(value));

const th = "whitespace-nowrap px-4 py-2 font-medium";
const num = "px-4 py-2 text-right tabular-nums";

function Section({
  title,
  empty,
  isEmpty,
  onDownload,
  children,
}: {
  title: string;
  empty: string;
  isEmpty: boolean;
  onDownload?: () => void;
  children: React.ReactNode;
}) {
  return (
    <div className="mt-6">
      <div className="mb-2 flex items-center justify-between gap-3">
        <h2 className="text-base font-semibold">{title}</h2>
        {onDownload && (
          <button type="button" className={buttonSecondary} onClick={onDownload}>
            <DownloadIcon size={18} /> Download CSV
          </button>
        )}
      </div>
      {isEmpty ? (
        <p className="text-ink-soft">{empty}</p>
      ) : (
        <div className="overflow-x-auto rounded-2xl border border-line bg-panel">
          <table className="w-full text-left text-sm">{children}</table>
        </div>
      )}
    </div>
  );
}

function RowsTable({ title, rows, onDownload }: { title: string; rows: Gstr1Row[]; onDownload: () => void }) {
  const keys = rows.length > 0 ? Object.keys(rows[0]!.columns) : [];
  return (
    <Section title={title} empty="No invoices in this section" isEmpty={rows.length === 0} onDownload={onDownload}>
      <thead className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
        <tr>
          {keys.map((k) => (
            <th key={k} className={th}>
              {k}
            </th>
          ))}
          <th className={`${th} text-right`}>Taxable</th>
          <th className={`${th} text-right`}>IGST</th>
          <th className={`${th} text-right`}>CGST</th>
          <th className={`${th} text-right`}>SGST</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-line">
        {rows.map((row, i) => (
          <tr key={i}>
            {keys.map((k) => (
              <td key={k} className="whitespace-nowrap px-4 py-2">
                {cell(row.columns[k])}
              </td>
            ))}
            <td className={num}>{rupees(row.taxable)}</td>
            <td className={num}>{rupees(row.igst)}</td>
            <td className={num}>{rupees(row.cgst)}</td>
            <td className={num}>{rupees(row.sgst)}</td>
          </tr>
        ))}
      </tbody>
      <tfoot className="border-t border-line-strong font-semibold">
        <tr>
          <td className="px-4 py-2" colSpan={Math.max(keys.length, 1)}>
            Total
          </td>
          <td className={num}>{rupees(sum(rows.map((r) => r.taxable)))}</td>
          <td className={num}>{rupees(sum(rows.map((r) => r.igst)))}</td>
          <td className={num}>{rupees(sum(rows.map((r) => r.cgst)))}</td>
          <td className={num}>{rupees(sum(rows.map((r) => r.sgst)))}</td>
        </tr>
      </tfoot>
    </Section>
  );
}

function HsnTable({ rows, onDownload }: { rows: Gstr1HsnRow[]; onDownload: () => void }) {
  return (
    <Section
      title="HSN summary"
      empty="No invoices in this section"
      isEmpty={rows.length === 0}
      onDownload={onDownload}
    >
      <thead className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
        <tr>
          <th className={th}>HSN</th>
          <th className={th}>Description</th>
          <th className={th}>UQC</th>
          <th className={`${th} text-right`}>Quantity</th>
          <th className={`${th} text-right`}>Rate %</th>
          <th className={`${th} text-right`}>Taxable</th>
          <th className={`${th} text-right`}>IGST</th>
          <th className={`${th} text-right`}>CGST</th>
          <th className={`${th} text-right`}>SGST</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-line">
        {rows.map((row, i) => (
          <tr key={i}>
            <td className="whitespace-nowrap px-4 py-2">{row.hsn}</td>
            <td className="px-4 py-2">{row.description}</td>
            <td className="px-4 py-2">{row.uqc}</td>
            <td className={num}>{row.quantity}</td>
            <td className={num}>{row.rate}</td>
            <td className={num}>{rupees(row.taxable)}</td>
            <td className={num}>{rupees(row.igst)}</td>
            <td className={num}>{rupees(row.cgst)}</td>
            <td className={num}>{rupees(row.sgst)}</td>
          </tr>
        ))}
      </tbody>
      <tfoot className="border-t border-line-strong font-semibold">
        <tr>
          <td className="px-4 py-2" colSpan={3}>
            Total
          </td>
          <td className={num}>{sum(rows.map((r) => r.quantity))}</td>
          <td className="px-4 py-2" />
          <td className={num}>{rupees(sum(rows.map((r) => r.taxable)))}</td>
          <td className={num}>{rupees(sum(rows.map((r) => r.igst)))}</td>
          <td className={num}>{rupees(sum(rows.map((r) => r.cgst)))}</td>
          <td className={num}>{rupees(sum(rows.map((r) => r.sgst)))}</td>
        </tr>
      </tfoot>
    </Section>
  );
}

function DocsTable({ rows, onDownload }: { rows: Gstr1DocRange[]; onDownload: () => void }) {
  return (
    <Section
      title="Documents issued"
      empty="No documents this month"
      isEmpty={rows.length === 0}
      onDownload={onDownload}
    >
      <thead className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
        <tr>
          <th className={th}>Nature of document</th>
          <th className={th}>From</th>
          <th className={th}>To</th>
          <th className={`${th} text-right`}>Total</th>
          <th className={`${th} text-right`}>Cancelled</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-line">
        {rows.map((row, i) => (
          <tr key={i}>
            <td className="px-4 py-2">{row.nature}</td>
            <td className="whitespace-nowrap px-4 py-2">{row.from}</td>
            <td className="whitespace-nowrap px-4 py-2">{row.to}</td>
            <td className={num}>{row.total}</td>
            <td className={num}>{row.cancelled}</td>
          </tr>
        ))}
      </tbody>
    </Section>
  );
}

/** GSTR-1 for one month: each section of the offline tool's workbook, with its totals and a CSV. */
export default function GstReportPage() {
  const [month, setMonth] = useState(thisMonth);
  const [downloadError, setDownloadError] = useState<string | null>(null);
  const report = useLoad(() => api.admin.money.gstr1.month(month), [month]);
  const r = report.value;

  async function downloadCsv(section: string) {
    setDownloadError(null);
    try {
      const blob = await api.admin.money.gstr1.csv(month, section);
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `gstr1-${section}-${month}.csv`;
      link.click();
      setTimeout(() => URL.revokeObjectURL(url), 0);
    } catch (e) {
      setDownloadError(e instanceof Error ? e.message : "Could not download the CSV");
    }
  }

  return (
    <section className="mx-auto max-w-6xl">
      <PageHeader title="GSTR-1" backHref="/reports/" />
      <TextField
        label="Month"
        type="month"
        className="mb-4 max-w-48"
        value={month}
        onChange={(e) => e.target.value && setMonth(e.target.value)}
      />
      <p className="-mt-2 mb-4 text-sm">
        <Link
          href={`/money/accounts/gstr3b/?month=${encodeURIComponent(month)}`}
          className="font-medium text-accent-ink underline"
        >
          GSTR-3B for this month
        </Link>
      </p>
      <ErrorNote>{report.error ?? downloadError}</ErrorNote>
      {!r ? (
        <LoadState loading={report.loading} error={null} />
      ) : (
        <>
          {!r.gstOn && (
            <p className="rounded-2xl border border-line bg-panel px-4 py-3 text-sm text-ink-soft">
              GST is off: these figures are for practice
            </p>
          )}
          <RowsTable title="B2B invoices" rows={r.b2b} onDownload={() => downloadCsv("b2b")} />
          <RowsTable title="B2C large" rows={r.b2cl} onDownload={() => downloadCsv("b2cl")} />
          <RowsTable title="B2C small" rows={r.b2cs} onDownload={() => downloadCsv("b2cs")} />
          <RowsTable title="Credit notes (registered)" rows={r.cdnr} onDownload={() => downloadCsv("cdnr")} />
          <RowsTable title="Credit notes (unregistered)" rows={r.cdnur} onDownload={() => downloadCsv("cdnur")} />
          <HsnTable rows={r.hsn} onDownload={() => downloadCsv("hsn")} />
          <DocsTable rows={r.docs} onDownload={() => downloadCsv("docs")} />
        </>
      )}
    </section>
  );
}
