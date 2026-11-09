"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense } from "react";
import type { StockUsedIn } from "@vaabhi/api-client";
import { MakeProducts } from "@/components/make-products";
import { PhotoStrip } from "@/components/photo-strip";
import { ErrorNote, Fact, Pill } from "@/components/tailoring";
import { LoadState, PageHeader, SectionLabel } from "@/components/ui";
import { api } from "@/lib/api";
import { rupees } from "@/lib/settings";
import { idParam, uploadKeyedPhoto, useLoad } from "@/lib/tailoring";
import { qty, unitLabel } from "@/lib/extras";

const when = new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", hour: "numeric", minute: "2-digit" });

const STATUS: Record<string, string> = { DRAFT: "Draft", PUBLISHED: "Published", ARCHIVED: "Archived" };

/** A product made from the item, with its status and, while it is being made, its workshop stage. */
function MadeIntoRow({ product, unit }: { product: StockUsedIn; unit: string }) {
  const piece = useLoad(
    () =>
      product.productStatus === "DRAFT" ? api.admin.workshop.pieceByProduct(product.productId) : Promise.resolve(null),
    [product.productId, product.productStatus],
  );
  return (
    <li className="flex flex-wrap items-center gap-3 px-4 py-3">
      <Link href={`/products/edit/?id=${product.productId}`} className="font-medium text-accent-ink underline">
        {product.productName}
      </Link>
      <Pill tone={product.productStatus === "PUBLISHED" ? "ok" : "default"}>
        {STATUS[product.productStatus] ?? product.productStatus}
      </Pill>
      {piece.value && <span className="text-ink-soft">In workshop: {piece.value.stage.name}</span>}
      <span className="ml-auto tabular-nums">
        {qty(product.quantity)} {unit}
      </span>
    </li>
  );
}

/** One stock item's ledger: every movement with the balance after it. */
function StockLedger({ id }: { id: number }) {
  const item = useLoad(() => api.admin.stock.get(id), [id]);
  const moves = useLoad(() => api.admin.stock.movements(id), [id]);
  const products = useLoad(() => api.admin.stock.products(id), [id]);
  const i = item.value;
  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader
        title={i?.name ?? "Stock item"}
        backHref="/stock/"
        badge={i?.low ? <Pill tone="warn">Low</Pill> : undefined}
      />
      {i && (
        <div className="mb-4">
          <PhotoStrip
            label="Stock photo"
            photos={i.photos}
            onAdd={async (file) => {
              const key = await uploadKeyedPhoto(file, () => api.admin.stock.startPhotoUpload(id));
              item.setValue(await api.admin.stock.addPhoto(id, key));
            }}
            onRemove={async (photoId) => item.setValue(await api.admin.stock.removePhoto(id, photoId))}
          />
        </div>
      )}
      {!i ? (
        <LoadState loading={item.loading} error={item.error} />
      ) : (
        <dl className="grid grid-cols-2 gap-4 rounded-2xl border border-line bg-panel p-4 sm:grid-cols-4">
          <Fact label="On hand">
            {qty(i.onHand)} {unitLabel(i.unit)}
          </Fact>
          <Fact label="Reorder at">{qty(i.reorderLevel)}</Fact>
          <Fact label="Cost per unit">{rupees(i.costRupees)}</Fact>
          <Fact label="Value">{rupees(i.valueRupees)}</Fact>
        </dl>
      )}
      {i && (i.supplierName || i.supplierPhone) && (
        <>
          <SectionLabel>Supplier</SectionLabel>
          <dl className="grid grid-cols-2 gap-4 rounded-2xl border border-line bg-panel p-4">
            <Fact label="Shop">{i.supplierName ?? "—"}</Fact>
            <Fact label="Mobile">
              {i.supplierPhone ? (
                <a href={`tel:${i.supplierPhone.replaceAll(" ", "")}`} className="text-accent-ink underline">
                  {i.supplierPhone}
                </a>
              ) : (
                "—"
              )}
            </Fact>
          </dl>
        </>
      )}
      {i && (i.material || i.colour || i.gsm || i.threadCount || i.widthIn || i.notes) && (
        <>
          <SectionLabel>Fabric details</SectionLabel>
          <dl className="grid grid-cols-2 gap-4 rounded-2xl border border-line bg-panel p-4 sm:grid-cols-3">
            {i.material && <Fact label="Material">{i.material}</Fact>}
            {i.colour && <Fact label="Colour">{i.colour}</Fact>}
            {i.gsm && <Fact label="GSM">{i.gsm}</Fact>}
            {i.threadCount && <Fact label="Thread count">{i.threadCount}</Fact>}
            {i.widthIn && <Fact label="Width">{i.widthIn} in</Fact>}
            {i.notes && <Fact label="Notes">{i.notes}</Fact>}
          </dl>
        </>
      )}
      <SectionLabel
        action={
          // Stays mounted when the whole lot is cut (on hand 0) so the success screen does not vanish.
          i ? (
            <MakeProducts
              item={i}
              onMade={() => {
                item.reload();
                products.reload();
                moves.reload();
              }}
            />
          ) : undefined
        }
      >
        Made into
      </SectionLabel>
      <ErrorNote>{products.error}</ErrorNote>
      {products.value && products.value.length === 0 && (
        <p className="text-ink-soft">No products made from this yet. Use Make products to cut it into pieces.</p>
      )}
      {products.value && products.value.length > 0 && (
        <ul className="divide-y divide-line rounded-2xl border border-line bg-panel text-sm">
          {products.value.map((p) => (
            <MadeIntoRow key={`${p.productId}-${p.createdAt}`} product={p} unit={i ? unitLabel(i.unit) : ""} />
          ))}
        </ul>
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
