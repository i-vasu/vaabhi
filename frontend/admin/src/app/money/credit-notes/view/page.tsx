"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense } from "react";
import { AmountLine } from "@/components/money";
import { ErrorNote, Fact } from "@/components/tailoring";
import { LoadState, PageHeader, buttonSecondary } from "@/components/ui";
import { amountInWords } from "@/lib/amount-in-words";
import { api } from "@/lib/api";
import { rupees } from "@/lib/settings";
import { formatDate, formatPhone, idParam, useLoad } from "@/lib/tailoring";

const REASONS = { RETURN: "Return", CORRECTION: "Correction", CANCELLED: "Cancelled" } as const;

function CreditNoteView({ id }: { id: number }) {
  const note = useLoad(() => api.admin.money.creditNotes.get(id), [id]);
  const states = useLoad(() => api.admin.settings.states(), []);

  if (!note.value) {
    return (
      <section className="mx-auto max-w-3xl">
        <PageHeader title="Credit Note" backHref="/money/credit-notes/" />
        <LoadState loading={note.loading} error={note.error} />
      </section>
    );
  }
  const v = note.value;
  const gst = v.cgstRupees + v.sgstRupees + v.igstRupees;
  const inter = v.supplyType === "INTER";
  const stateName = states.value?.find((s) => s.code === v.placeOfSupply)?.name;

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader
        title="Credit Note"
        backHref="/money/credit-notes/"
        actions={
          <button type="button" className={`${buttonSecondary} print:hidden`} onClick={() => window.print()}>
            Print
          </button>
        }
      />
      <div className="grid gap-4 rounded-2xl border border-line bg-panel p-4 sm:grid-cols-2">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.12em] text-ink-faint">Issued by</p>
          <p className="mt-1 font-medium">{v.seller.name}</p>
          {v.seller.address && <p className="text-sm text-ink-soft">{v.seller.address}</p>}
          {v.seller.cin && <p className="text-sm text-ink-soft">CIN: {v.seller.cin}</p>}
          {v.seller.state && (
            <p className="text-sm text-ink-soft">
              State: {v.seller.state}
              {v.seller.stateCode && ` (${v.seller.stateCode})`}
            </p>
          )}
        </div>
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.12em] text-ink-faint">Credited to</p>
          <p className="mt-1 font-medium">{v.buyer.name}</p>
          {v.buyer.phone && <p className="text-sm text-ink-soft">{formatPhone(v.buyer.phone)}</p>}
          {v.buyer.address && <p className="text-sm text-ink-soft">{v.buyer.address}</p>}
          {v.buyer.state && <p className="text-sm text-ink-soft">State: {v.buyer.state}</p>}
        </div>
      </div>
      <dl className="mt-4 grid grid-cols-2 gap-4 rounded-2xl border border-line bg-panel p-4 sm:grid-cols-3">
        <Fact label="Credit note no.">{v.number}</Fact>
        <Fact label="Date">{formatDate(v.issuedOn)}</Fact>
        <Fact label="Against invoice">
          <Link href={`/money/invoices/view/?id=${v.invoiceId}`} className="text-accent-ink underline">
            {v.invoiceNumber}
          </Link>
        </Fact>
        <Fact label="Reason">{REASONS[v.reason]}</Fact>
        {gst > 0 && v.placeOfSupply && (
          <Fact label="Place of supply">{stateName ? `${stateName} (${v.placeOfSupply})` : v.placeOfSupply}</Fact>
        )}
      </dl>
      {v.note && <p className="mt-3 text-sm text-ink-soft">{v.note}</p>}

      <div className="mt-4 overflow-x-auto rounded-2xl border border-line bg-panel">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
            <tr>
              <th className="px-4 py-2 font-medium">Item</th>
              {gst > 0 && <th className="px-4 py-2 font-medium">HSN/SAC</th>}
              <th className="px-4 py-2 text-right font-medium">Qty</th>
              <th className="px-4 py-2 text-right font-medium">Rate</th>
              {gst > 0 && <th className="px-4 py-2 text-right font-medium">GST %</th>}
              <th className="px-4 py-2 text-right font-medium">Amount</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-line">
            {v.lines.map((line) => (
              <tr key={line.id}>
                <td className="px-4 py-3">{line.description}</td>
                {gst > 0 && <td className="px-4 py-3">{line.hsnCode ?? "—"}</td>}
                <td className="px-4 py-3 text-right tabular-nums">{line.quantity}</td>
                <td className="px-4 py-3 text-right tabular-nums">{rupees(line.unitRupees)}</td>
                {gst > 0 && <td className="px-4 py-3 text-right tabular-nums">{line.gstRate}%</td>}
                <td className="px-4 py-3 text-right tabular-nums">{rupees(line.amountRupees)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <dl aria-label="Credit note totals" className="mt-4 rounded-2xl border border-line bg-panel p-4 text-sm">
        {gst > 0 && (
          <>
            <AmountLine label="Taxable value" value={rupees(v.taxableRupees)} />
            {inter ? (
              <AmountLine label="IGST" value={rupees(v.igstRupees)} />
            ) : (
              <>
                <AmountLine label="CGST" value={rupees(v.cgstRupees)} />
                <AmountLine label="SGST" value={rupees(v.sgstRupees)} />
              </>
            )}
          </>
        )}
        <AmountLine label="Total credited" value={rupees(v.totalRupees)} strong />
      </dl>
      {gst > 0 && <p className="mt-3 text-sm">{amountInWords(v.totalRupees)}</p>}
      <p className="mt-3 text-sm text-ink-faint">Issued by {v.createdBy ?? "the system"}</p>
    </section>
  );
}

function Page() {
  const id = idParam(useSearchParams().get("id"));
  return id ? <CreditNoteView key={id} id={id} /> : <ErrorNote>No credit note selected.</ErrorNote>;
}

export default function CreditNotePage() {
  return (
    <Suspense fallback={<p className="text-ink-soft">Loading…</p>}>
      <Page />
    </Suspense>
  );
}
