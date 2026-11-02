"use client";

import { useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import type { PaperFormat } from "@vaabhi/api-client";
import { PaperLine, PrintDocument } from "@/components/print";
import { ErrorNote } from "@/components/tailoring";
import { LoadState } from "@/components/ui";
import { api } from "@/lib/api";
import { rupees } from "@/lib/settings";
import { ALTERATION_KINDS, formatDate, formatPhone, idParam, useLoad } from "@/lib/tailoring";

/** The alteration slip with the undertaking the customer accepted. */
function AlterationSlip({ id }: { id: number }) {
  const data = useLoad(
    () =>
      Promise.all([
        api.admin.alterations.get(id),
        api.admin.settings.get("shop-profile"),
        api.admin.settings.get("printing"),
      ]),
    [id],
  );
  const [format, setFormat] = useState<PaperFormat | null>(null);
  if (!data.value) return <LoadState loading={data.loading} error={data.error} />;
  const [a, shop, printing] = data.value;

  return (
    <PrintDocument
      printing={printing}
      format={format ?? printing.defaultFormat}
      onFormat={setFormat}
      shop={shop}
      title={`Alteration ${a.number}`}
    >
      <div className="mb-3">
        <PaperLine label="Customer" value={a.client.name} />
        <PaperLine label="Mobile" value={formatPhone(a.client.phone)} />
        <PaperLine label="Ready by" value={formatDate(a.readyBy)} />
        {a.orderNumber && <PaperLine label="Order" value={a.orderNumber} />}
      </div>
      <div className="mb-3 border-t border-black/20 pt-2">
        {a.items.map((item) => (
          <div key={item.id} className="mb-2">
            <PaperLine
              label={`${item.garment} — ${ALTERATION_KINDS.find((k) => k.value === item.kind)?.label}`}
              value={a.paid ? rupees(item.costPaise) : ""}
            />
            {item.instructions && <p className="text-black/70">{item.instructions}</p>}
          </div>
        ))}
      </div>
      <div className="border-t border-black/20 pt-2">
        <PaperLine label="Charge" value={a.paid ? rupees(a.totalPaise) : "Free"} strong />
        {a.paid && <PaperLine label="Due" value={rupees(a.balancePaise)} />}
      </div>
      <p className="mt-3 text-black/70">{a.undertaking}</p>
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
  return id ? <AlterationSlip key={id} id={id} /> : <ErrorNote>No alteration selected.</ErrorNote>;
}

export default function PrintAlterationPage() {
  return (
    <Suspense fallback={<p className="text-ink-soft">Loading…</p>}>
      <Page />
    </Suspense>
  );
}
