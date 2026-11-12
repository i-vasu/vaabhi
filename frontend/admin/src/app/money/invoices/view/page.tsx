"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { AmountLine } from "@/components/money";
import { CreditNoteAction } from "@/components/credit-note-sheet";
import { ErrorNote, Fact, Pill } from "@/components/tailoring";
import { LoadState, PageHeader, Sheet, buttonPrimary, buttonSecondary } from "@/components/ui";
import { api } from "@/lib/api";
import { amountInWords } from "@/lib/amount-in-words";
import { rupees } from "@/lib/settings";
import { formatDate, formatPhone, idParam, useAction, useLoad } from "@/lib/tailoring";

/** Today's date in India (yyyy-MM-dd): invoices are dated by the shop's calendar. */
function todayInKolkata() {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Kolkata" }).format(new Date());
}

function InvoiceView({ id }: { id: number }) {
  const invoice = useLoad(() => api.admin.money.invoice(id), [id]);
  const action = useAction();
  const states = useLoad(() => api.admin.settings.states(), []);
  const [confirming, setConfirming] = useState(false);

  if (!invoice.value) {
    return (
      <section className="mx-auto max-w-3xl">
        <PageHeader title="Invoice" backHref="/money/invoices/" />
        <LoadState loading={invoice.loading} error={invoice.error} />
      </section>
    );
  }
  const v = invoice.value;
  const gst = v.cgstRupees + v.sgstRupees + v.igstRupees;
  const inter = v.supplyType === "INTER";
  const stateName = states.value?.find((s) => s.code === v.placeOfSupply)?.name;
  const byRate = [...new Set(v.lines.map((line) => line.gstRate))]
    .sort((a, b) => a - b)
    .map((rate) => {
      const lines = v.lines.filter((line) => line.gstRate === rate);
      const sum = (pick: (line: (typeof lines)[number]) => number) =>
        lines.reduce((total, line) => total + pick(line), 0);
      return {
        rate,
        taxable: sum((line) => line.taxableRupees),
        cgst: sum((line) => line.cgstRupees),
        sgst: sum((line) => line.sgstRupees),
        igst: sum((line) => line.igstRupees),
      };
    });
  const canCancel = !v.cancelledAt && v.issuedOn === todayInKolkata() && v.creditNotes.length === 0;

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader
        title={v.title}
        backHref="/money/invoices/"
        badge={v.cancelledAt ? <Pill tone="bad">Cancelled</Pill> : undefined}
        actions={
          <button type="button" className={`${buttonSecondary} print:hidden`} onClick={() => window.print()}>
            Print
          </button>
        }
      />
      <div className="grid gap-4 rounded-2xl border border-line bg-panel p-4 sm:grid-cols-2">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.12em] text-ink-faint">Sold by</p>
          <p className="mt-1 font-medium">{v.seller.name}</p>
          {v.seller.address && <p className="text-sm text-ink-soft">{v.seller.address}</p>}
          {v.seller.cin && <p className="text-sm text-ink-soft">CIN: {v.seller.cin}</p>}
          {v.seller.state && (
            <p className="text-sm text-ink-soft">
              State: {v.seller.state}
              {v.seller.stateCode && ` (${v.seller.stateCode})`}
            </p>
          )}
          {v.shopGstin && <p className="text-sm text-ink-soft">GSTIN: {v.shopGstin}</p>}
        </div>
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.12em] text-ink-faint">Billed to</p>
          <p className="mt-1 font-medium">{v.buyer.name}</p>
          {v.buyer.phone && <p className="text-sm text-ink-soft">{formatPhone(v.buyer.phone)}</p>}
          {v.buyer.address && <p className="text-sm text-ink-soft">{v.buyer.address}</p>}
          {v.buyer.state && <p className="text-sm text-ink-soft">State: {v.buyer.state}</p>}
          {v.clientGstin && <p className="text-sm text-ink-soft">GSTIN: {v.clientGstin}</p>}
        </div>
      </div>
      <dl className="mt-4 grid grid-cols-2 gap-4 rounded-2xl border border-line bg-panel p-4 sm:grid-cols-3">
        <Fact label="Invoice no.">{v.number}</Fact>
        <Fact label="Date">{formatDate(v.issuedOn)}</Fact>
        <Fact label="Order">
          {v.source === "WEB" ? (
            <Link
              href={`/online-orders/view/?number=${encodeURIComponent(v.orderNumber)}`}
              className="text-accent-ink underline"
            >
              {v.orderNumber}
            </Link>
          ) : v.orderId ? (
            <Link href={`/orders/view/?id=${v.orderId}`} className="text-accent-ink underline">
              {v.orderNumber}
            </Link>
          ) : (
            v.orderNumber
          )}
        </Fact>
        {gst > 0 && v.placeOfSupply && (
          <Fact label="Place of supply">{stateName ? `${stateName} (${v.placeOfSupply})` : v.placeOfSupply}</Fact>
        )}
      </dl>

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

      {gst > 0 && (
        <div className="mt-4 overflow-x-auto rounded-2xl border border-line bg-panel">
          <table aria-label="Tax summary" className="w-full text-left text-sm">
            <thead className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
              <tr>
                <th className="px-4 py-2 font-medium">GST %</th>
                <th className="px-4 py-2 text-right font-medium">Taxable</th>
                {inter ? (
                  <th className="px-4 py-2 text-right font-medium">IGST</th>
                ) : (
                  <>
                    <th className="px-4 py-2 text-right font-medium">CGST</th>
                    <th className="px-4 py-2 text-right font-medium">SGST</th>
                  </>
                )}
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {byRate.map((row) => (
                <tr key={row.rate}>
                  <td className="px-4 py-3">{row.rate}%</td>
                  <td className="px-4 py-3 text-right tabular-nums">{rupees(row.taxable)}</td>
                  {inter ? (
                    <td className="px-4 py-3 text-right tabular-nums">{rupees(row.igst)}</td>
                  ) : (
                    <>
                      <td className="px-4 py-3 text-right tabular-nums">{rupees(row.cgst)}</td>
                      <td className="px-4 py-3 text-right tabular-nums">{rupees(row.sgst)}</td>
                    </>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <dl aria-label="Invoice totals" className="mt-4 rounded-2xl border border-line bg-panel p-4 text-sm">
        {v.discountRupees > 0 && <AmountLine label="Discount" value={`− ${rupees(v.discountRupees)}`} />}
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
        <AmountLine label="Total" value={rupees(v.totalRupees)} strong />
        {gst > 0 && v.pricesIncludeTax && <p className="text-xs text-ink-faint">Prices include GST.</p>}
        <AmountLine label="Paid" value={rupees(v.paidRupees)} />
        <AmountLine label="Balance" value={rupees(Math.max(0, v.balanceRupees))} strong />
      </dl>
      {gst > 0 && <p className="mt-3 text-sm">{amountInWords(v.totalRupees)}</p>}

      {v.creditNotes.length > 0 && (
        <div className="mt-4 rounded-2xl border border-line bg-panel p-4 text-sm">
          <p className="text-xs font-semibold uppercase tracking-[0.12em] text-ink-faint">Credit notes</p>
          <ul className="mt-1">
            {v.creditNotes.map((n) => (
              <li key={n.id}>
                <Link href={`/money/credit-notes/view/?id=${n.id}`} className="text-accent-ink underline">
                  {n.number}
                </Link>{" "}
                · {formatDate(n.issuedOn)} · {rupees(n.totalRupees)}
              </li>
            ))}
          </ul>
        </div>
      )}

      <p className="mt-3 text-sm text-ink-faint">
        Issued by {v.createdBy ?? "the system"}
        {v.cancelledAt && ` · cancelled by ${v.cancelledBy ?? "staff"} on ${formatDate(v.cancelledAt)}`}
      </p>

      {!v.cancelledAt && (
        <div className="mt-6 flex flex-wrap gap-2 print:hidden">
          {v.balanceRupees > 0 && v.source === "SHOP" && v.orderId && (
            <Link href={`/orders/view/?id=${v.orderId}`} className={buttonPrimary}>
              Take payment
            </Link>
          )}
          <CreditNoteAction invoice={v} onIssued={() => invoice.reload()} />
          {canCancel && (
            <button type="button" className={`${buttonSecondary} text-bad`} onClick={() => setConfirming(true)}>
              Cancel invoice
            </button>
          )}
        </div>
      )}
      {confirming && (
        <Sheet open title={`Cancel ${v.number}?`} onClose={() => setConfirming(false)}>
          <p className="text-ink-soft">
            The number stays in the register as cancelled.{" "}
            {v.source === "WEB"
              ? "Cancel this invoice only if the order was never sent; a cancelled web invoice is not reissued."
              : "You can then issue a new invoice from the order."}
          </p>
          <ErrorNote>{action.error}</ErrorNote>
          <div className="mt-4 flex gap-2">
            <button
              type="button"
              className={`${buttonPrimary} bg-bad hover:bg-bad`}
              disabled={action.busy}
              onClick={async () => {
                const updated = await action.run(() => api.admin.money.cancelInvoice(id));
                if (updated) {
                  invoice.setValue(updated);
                  setConfirming(false);
                }
              }}
            >
              Cancel invoice
            </button>
            <button type="button" className={buttonSecondary} onClick={() => setConfirming(false)}>
              Keep it
            </button>
          </div>
        </Sheet>
      )}
    </section>
  );
}

function Page() {
  const id = idParam(useSearchParams().get("id"));
  return id ? <InvoiceView key={id} id={id} /> : <ErrorNote>No invoice selected.</ErrorNote>;
}

export default function InvoicePage() {
  return (
    <Suspense fallback={<p className="text-ink-soft">Loading…</p>}>
      <Page />
    </Suspense>
  );
}
