"use client";

import { useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import type { PaperFormat } from "@vaabhi/api-client";
import { PaperLine, PrintDocument } from "@/components/print";
import { ErrorNote } from "@/components/tailoring";
import { LoadState } from "@/components/ui";
import { api } from "@/lib/api";
import { rupees } from "@/lib/settings";
import { formatDate, formatPhone, idParam, useLoad } from "@/lib/tailoring";

/** The order slip handed to the customer at booking: items, measurements, money and terms. */
function OrderSlip({ id }: { id: number }) {
  const data = useLoad(
    () =>
      Promise.all([
        api.admin.orders.get(id),
        api.admin.settings.get("shop-profile"),
        api.admin.settings.get("printing"),
        api.admin.settings.get("documents"),
      ]),
    [id],
  );
  const [format, setFormat] = useState<PaperFormat | null>(null);
  if (!data.value) return <LoadState loading={data.loading} error={data.error} />;
  const [o, shop, printing, documents] = data.value;

  return (
    <PrintDocument
      printing={printing}
      format={format ?? printing.defaultFormat}
      onFormat={setFormat}
      shop={shop}
      title={`Order ${o.number}`}
    >
      <div className="mb-3">
        <PaperLine label="Customer" value={o.client.name} />
        <PaperLine label="Mobile" value={formatPhone(o.client.phone)} />
        <PaperLine label="Booked" value={formatDate(o.orderDate)} />
        <PaperLine
          label="Delivery"
          value={`${formatDate(o.deliveryDate)}${o.dueTime ? ` ${o.dueTime.slice(0, 5)}` : ""}`}
        />
        {o.trialDate && <PaperLine label="Trial" value={formatDate(o.trialDate)} />}
      </div>
      <div className="mb-3 border-t border-black/20 pt-2">
        {o.items.map((item) => (
          <div key={item.id} className="mb-2">
            <PaperLine
              label={`${item.quantity > 1 ? `${item.quantity} × ` : ""}${item.description}${item.colour ? ` (${item.colour})` : ""}`}
              value={rupees(item.pricePaise * item.quantity)}
            />
            {printing.showMeasurements && item.measurements && (
              <p className="text-black/70">
                {Object.entries(item.measurements)
                  .map(([k, v]) => `${k.replace(/_/g, " ")} ${v}`)
                  .join(", ")}
              </p>
            )}
            {item.note && <p className="text-black/70">{item.note}</p>}
          </div>
        ))}
      </div>
      <div className="border-t border-black/20 pt-2">
        {o.deliveryChargePaise > 0 && <PaperLine label="Delivery" value={rupees(o.deliveryChargePaise)} />}
        {o.discountPaise > 0 && <PaperLine label="Discount" value={`− ${rupees(o.discountPaise)}`} />}
        <PaperLine label="Total" value={rupees(o.totalPaise)} strong />
        <PaperLine label="Paid" value={rupees(o.paidPaise)} />
        <PaperLine label="Balance" value={rupees(Math.max(0, o.balancePaise))} strong />
      </div>
      {printing.showBankDetails && documents.upiId && <p className="mt-3">UPI: {documents.upiId}</p>}
      {printing.showTerms && documents.terms && (
        <p className="mt-3 whitespace-pre-line text-black/70">{documents.terms}</p>
      )}
      {printing.showStaffName && o.createdBy && <p className="mt-3 text-black/70">Booked by {o.createdBy}</p>}
      {printing.showSignature && (
        <div className="mt-8 flex justify-between text-black/70">
          <span>Customer</span>
          <span>For {shop.shopName}</span>
        </div>
      )}
    </PrintDocument>
  );
}

function Page() {
  const id = idParam(useSearchParams().get("id"));
  return id ? <OrderSlip key={id} id={id} /> : <ErrorNote>No order selected.</ErrorNote>;
}

export default function PrintOrderPage() {
  return (
    <Suspense fallback={<p className="text-ink-soft">Loading…</p>}>
      <Page />
    </Suspense>
  );
}
