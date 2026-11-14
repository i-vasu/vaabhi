"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense } from "react";
import type { Gstr3bRow, Gstr3bSetOff } from "@vaabhi/api-client";
import { DownloadIcon } from "@/components/icons";
import { ErrorNote } from "@/components/tailoring";
import { LoadState, PageHeader, SectionLabel, TextField, buttonSecondary } from "@/components/ui";
import { api } from "@/lib/api";
import { rupees } from "@/lib/settings";
import { today, useAction, useLoad } from "@/lib/tailoring";

const th = "whitespace-nowrap px-4 py-2 font-medium";
const cell = "px-4 py-3";
const num = "whitespace-nowrap px-4 py-3 text-right tabular-nums";

/** The month before today's, e.g. "2026-10" on 14 Nov 2026. */
function previousMonth(now: string): string {
  const date = new Date(`${now}T00:00:00Z`);
  date.setUTCDate(1);
  date.setUTCMonth(date.getUTCMonth() - 1);
  return date.toISOString().slice(0, 7);
}

function OutwardTable({ title, rows }: { title: string; rows: Gstr3bRow[] }) {
  return (
    <>
      <SectionLabel>{title}</SectionLabel>
      <div className="overflow-x-auto rounded-2xl border border-line bg-panel">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
            <tr>
              <th className={th}>Section</th>
              <th className={th}>Description</th>
              <th className={`${th} text-right`}>Taxable</th>
              <th className={`${th} text-right`}>IGST</th>
              <th className={`${th} text-right`}>CGST</th>
              <th className={`${th} text-right`}>SGST</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-line">
            {rows.map((row) => (
              <tr key={row.section}>
                <td className={`${cell} whitespace-nowrap font-medium`}>{row.section}</td>
                <td className={cell}>{row.description}</td>
                <td className={num}>{rupees(row.taxableRupees)}</td>
                <td className={num}>{rupees(row.igstRupees)}</td>
                <td className={num}>{rupees(row.cgstRupees)}</td>
                <td className={num}>{rupees(row.sgstRupees)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}

function InterStateTable({ rows }: { rows: Gstr3bRow[] }) {
  return (
    <>
      <SectionLabel>3.2 Inter-state supplies to unregistered persons</SectionLabel>
      {rows.length === 0 ? (
        <p className="text-sm text-ink-soft">No inter-state sales to unregistered buyers</p>
      ) : (
        <div className="overflow-x-auto rounded-2xl border border-line bg-panel">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
              <tr>
                <th className={th}>Place of supply</th>
                <th className={`${th} text-right`}>Taxable</th>
                <th className={`${th} text-right`}>IGST</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {rows.map((row) => (
                <tr key={row.description}>
                  <td className={cell}>{row.description}</td>
                  <td className={num}>{rupees(row.taxableRupees)}</td>
                  <td className={num}>{rupees(row.igstRupees)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}

function SetOffTable({ rows }: { rows: Gstr3bSetOff[] }) {
  return (
    <>
      <SectionLabel>6.1 Payment of tax</SectionLabel>
      <div className="overflow-x-auto rounded-2xl border border-line bg-panel">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
            <tr>
              <th className={th}>Head</th>
              <th className={`${th} text-right`}>Output tax</th>
              <th className={`${th} text-right`}>Paid by ITC</th>
              <th className={`${th} text-right`}>Cash</th>
              <th className={`${th} text-right`}>ITC carried forward</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-line">
            {rows.map((row) => (
              <tr key={row.head}>
                <td className={`${cell} font-medium`}>{row.head}</td>
                <td className={num}>{rupees(row.outputRupees)}</td>
                <td className={num}>{rupees(row.itcUsedRupees)}</td>
                <td className={num}>{rupees(row.cashRupees)}</td>
                <td className={num}>{rupees(row.carriedForwardRupees)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}

function Route() {
  const router = useRouter();
  const raw = useSearchParams().get("month");
  const month = raw && /^\d{4}-(0[1-9]|1[0-2])$/.test(raw) ? raw : previousMonth(today());
  const report = useLoad(() => api.admin.accounts.gstr3b(month), [month]);
  const download = useAction();
  const g = report.value;

  async function downloadCsv() {
    await download.run(async () => {
      const blob = await api.admin.accounts.gstr3bCsv(month);
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `gstr3b-${month}.csv`;
      link.click();
      setTimeout(() => URL.revokeObjectURL(url), 0);
    });
  }

  return (
    <section className="mx-auto max-w-4xl">
      <PageHeader
        title="GSTR-3B"
        backHref="/money/accounts/"
        actions={
          <button type="button" className={buttonSecondary} disabled={download.busy} onClick={downloadCsv}>
            <DownloadIcon size={18} /> Download CSV
          </button>
        }
      />
      <TextField
        label="Month"
        type="month"
        className="mb-4 max-w-48"
        value={month}
        onChange={(e) => {
          const value = e.target.value;
          if (value) router.replace(`/money/accounts/gstr3b/?month=${value}`);
        }}
      />
      <ErrorNote>{report.error ?? download.error}</ErrorNote>
      {!g ? (
        <LoadState loading={report.loading} error={null} />
      ) : (
        <>
          {!g.gstOn && (
            <p className="mb-4 rounded-2xl border border-line bg-panel px-4 py-3 text-sm text-ink-soft">
              Not registered for GST — these figures are for reference only.
            </p>
          )}
          <OutwardTable title="3.1 Outward supplies" rows={g.outward} />
          <InterStateTable rows={g.interState} />
          <OutwardTable title="4 Eligible ITC" rows={[g.itc]} />
          <SetOffTable rows={g.setOff} />
        </>
      )}
    </section>
  );
}

/** GSTR-3B for one month: outward supplies, input credit and the tax set off against it, with a CSV. */
export default function Gstr3bPage() {
  return (
    <Suspense fallback={<p className="text-ink-soft">Loading…</p>}>
      <Route />
    </Suspense>
  );
}
