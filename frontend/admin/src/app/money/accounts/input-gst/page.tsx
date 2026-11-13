"use client";

import { useState } from "react";
import { DownloadIcon } from "@/components/icons";
import { ErrorNote } from "@/components/tailoring";
import { EmptyState, LoadState, PageHeader, TextField, buttonSecondary } from "@/components/ui";
import { fyRange, monthTitle } from "@/lib/accounts";
import { api } from "@/lib/api";
import { rupees } from "@/lib/settings";
import { today, useLoad } from "@/lib/tailoring";

/** Input GST on supplier bills and gateway fees, month by month. */
export default function InputGstPage() {
  const [from, setFrom] = useState(() => fyRange(today()).from);
  const [to, setTo] = useState(() => fyRange(today()).to);
  const report = useLoad(() => api.admin.accounts.inputGst(from, to), [from, to]);
  const [downloadError, setDownloadError] = useState<string | null>(null);

  const rows = report.value;
  const totalTaxable = (rows ?? []).reduce((sum, row) => sum + row.taxableRupees, 0);
  const totalCgst = (rows ?? []).reduce((sum, row) => sum + row.cgstRupees, 0);
  const totalSgst = (rows ?? []).reduce((sum, row) => sum + row.sgstRupees, 0);
  const totalIgst = (rows ?? []).reduce((sum, row) => sum + row.igstRupees, 0);
  const totalGateway = (rows ?? []).reduce((sum, row) => sum + row.gatewayGstRupees, 0);
  const total = (rows ?? []).reduce((sum, row) => sum + row.totalRupees, 0);

  async function downloadCsv() {
    setDownloadError(null);
    try {
      const blob = await api.admin.accounts.inputGstCsv(from, to);
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `input-gst-${from}-${to}.csv`;
      link.click();
      setTimeout(() => URL.revokeObjectURL(url), 0);
    } catch (e) {
      setDownloadError(e instanceof Error ? e.message : "Could not download the CSV");
    }
  }

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader
        title="Input GST"
        backHref="/money/accounts/"
        actions={
          <button type="button" className={buttonSecondary} disabled={!rows || rows.length === 0} onClick={downloadCsv}>
            <DownloadIcon size={18} />
            Download CSV
          </button>
        }
      />
      <div className="mb-4 flex flex-wrap gap-3">
        <TextField label="From" type="date" value={from} onChange={(e) => e.target.value && setFrom(e.target.value)} />
        <TextField label="To" type="date" value={to} onChange={(e) => e.target.value && setTo(e.target.value)} />
      </div>
      <ErrorNote>{report.error ?? downloadError}</ErrorNote>

      {!rows ? (
        <LoadState loading={report.loading} error={null} />
      ) : rows.length === 0 ? (
        <EmptyState icon={DownloadIcon} title="No input GST in this period">
          GST on supplier bills and gateway fees shows here once bills are recorded.
        </EmptyState>
      ) : (
        <div className="overflow-x-auto rounded-2xl border border-line bg-panel">
          <table className="w-full min-w-[44rem] text-left text-sm">
            <thead className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
              <tr>
                <th className="px-4 py-2 font-medium">Month</th>
                <th className="px-4 py-2 text-right font-medium">Taxable</th>
                <th className="px-4 py-2 text-right font-medium">CGST</th>
                <th className="px-4 py-2 text-right font-medium">SGST</th>
                <th className="px-4 py-2 text-right font-medium">IGST</th>
                <th className="px-4 py-2 text-right font-medium">Gateway GST</th>
                <th className="px-4 py-2 text-right font-medium">Total</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {rows.map((row) => (
                <tr key={row.month}>
                  <td className="whitespace-nowrap px-4 py-3">{monthTitle(row.month)}</td>
                  <td className="whitespace-nowrap px-4 py-3 text-right tabular-nums">{rupees(row.taxableRupees)}</td>
                  <td className="whitespace-nowrap px-4 py-3 text-right tabular-nums">{rupees(row.cgstRupees)}</td>
                  <td className="whitespace-nowrap px-4 py-3 text-right tabular-nums">{rupees(row.sgstRupees)}</td>
                  <td className="whitespace-nowrap px-4 py-3 text-right tabular-nums">{rupees(row.igstRupees)}</td>
                  <td className="whitespace-nowrap px-4 py-3 text-right tabular-nums">
                    {rupees(row.gatewayGstRupees)}
                  </td>
                  <td className="whitespace-nowrap px-4 py-3 text-right font-medium tabular-nums">
                    {rupees(row.totalRupees)}
                  </td>
                </tr>
              ))}
            </tbody>
            <tfoot className="border-t border-line font-medium">
              <tr>
                <td className="px-4 py-3">Total</td>
                <td className="px-4 py-3 text-right tabular-nums">{rupees(totalTaxable)}</td>
                <td className="px-4 py-3 text-right tabular-nums">{rupees(totalCgst)}</td>
                <td className="px-4 py-3 text-right tabular-nums">{rupees(totalSgst)}</td>
                <td className="px-4 py-3 text-right tabular-nums">{rupees(totalIgst)}</td>
                <td className="px-4 py-3 text-right tabular-nums">{rupees(totalGateway)}</td>
                <td className="px-4 py-3 text-right tabular-nums">{rupees(total)}</td>
              </tr>
            </tfoot>
          </table>
        </div>
      )}
      <p className="mt-3 text-sm text-ink-faint">
        Gateway GST is the tax charged on Razorpay fees; it is counted in the total.
      </p>
    </section>
  );
}
