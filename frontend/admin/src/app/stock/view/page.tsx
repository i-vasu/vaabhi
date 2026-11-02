"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense } from "react";
import { ErrorNote, Fact, Pill } from "@/components/tailoring";
import { LoadState, PageHeader, SectionLabel } from "@/components/ui";
import { api } from "@/lib/api";
import { rupees } from "@/lib/settings";
import { idParam, useLoad } from "@/lib/tailoring";
import { qty, unitLabel } from "@/lib/extras";

const when = new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", hour: "numeric", minute: "2-digit" });

/** One stock item's ledger: every movement with the balance after it. */
function StockLedger({ id }: { id: number }) {
  const item = useLoad(() => api.admin.stock.get(id), [id]);
  const moves = useLoad(() => api.admin.stock.movements(id), [id]);
  const i = item.value;
  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader
        title={i?.name ?? "Stock item"}
        backHref="/stock/"
        badge={i?.low ? <Pill tone="warn">Low</Pill> : undefined}
      />
      {!i ? (
        <LoadState loading={item.loading} error={item.error} />
      ) : (
        <dl className="grid grid-cols-2 gap-4 rounded-2xl border border-line bg-panel p-4 sm:grid-cols-4">
          <Fact label="On hand">
            {qty(i.onHand)} {unitLabel(i.unit)}
          </Fact>
          <Fact label="Reorder at">{qty(i.reorderLevel)}</Fact>
          <Fact label="Cost per unit">{rupees(i.costPaise)}</Fact>
          <Fact label="Value">{rupees(i.valuePaise)}</Fact>
        </dl>
      )}
      <SectionLabel>Stock ledger</SectionLabel>
      <ErrorNote>{moves.error}</ErrorNote>
      {moves.value && moves.value.length === 0 && <p className="text-ink-soft">No movements yet.</p>}
      {moves.value && moves.value.length > 0 && (
        <ul className="divide-y divide-line rounded-2xl border border-line bg-panel text-sm">
          {moves.value.map((m) => (
            <li key={m.id} className="flex flex-wrap items-center gap-x-3 gap-y-1 px-4 py-3">
              <span className="text-ink-soft">{when.format(new Date(m.movedAt))}</span>
              <span className={m.quantity < 0 ? "text-warn" : "text-ok"}>
                {m.quantity > 0 ? "+" : ""}
                {qty(m.quantity)}
              </span>
              <span>
                {m.kind === "IN" ? "In" : m.kind === "OUT" ? "Out" : "Correction"}
                {m.note && ` · ${m.note}`}
                {m.movedBy && ` · ${m.movedBy}`}
              </span>
              {m.orderId && (
                <Link href={`/orders/view/?id=${m.orderId}`} className="text-accent-ink underline">
                  {m.orderNumber}
                </Link>
              )}
              <span className="ml-auto tabular-nums">balance {qty(m.balance)}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function Page() {
  const id = idParam(useSearchParams().get("id"));
  return id ? <StockLedger key={id} id={id} /> : <ErrorNote>No stock item selected.</ErrorNote>;
}

export default function StockItemPage() {
  return (
    <Suspense fallback={<p className="text-ink-soft">Loading…</p>}>
      <Page />
    </Suspense>
  );
}
