"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import type { StockItem, StockItemForm, StockMovementForm, StockUnit } from "@vaabhi/api-client";
import { PlusIcon, TagIcon } from "@/components/icons";
import { ErrorNote, Pill, SearchBox } from "@/components/tailoring";
import {
  EmptyState,
  LoadState,
  PageHeader,
  SegmentedControl,
  SelectField,
  Sheet,
  StatTile,
  TextField,
  ToggleRow,
  buttonPrimary,
  buttonSecondary,
} from "@/components/ui";
import { api } from "@/lib/api";
import { mediaUrl } from "@/lib/photo-upload";
import { parseRupees, rupees } from "@/lib/settings";
import { useAction, useLoad } from "@/lib/tailoring";
import { UNITS, qty, unitLabel } from "@/lib/extras";

function Stock() {
  const params = useSearchParams();
  const [q, setQ] = useState("");
  const [low, setLow] = useState(params.get("low") === "1");
  const items = useLoad(() => api.admin.stock.list({ q: q || undefined, low }), [q, low]);
  const [editing, setEditing] = useState<{ item: StockItem | null } | null>(null);
  const [moving, setMoving] = useState<StockItem | null>(null);
  const list = items.value ?? [];
  const value = list.reduce((sum, i) => sum + i.valueRupees, 0);

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader
        title="Stock"
        actions={
          <button type="button" className={buttonPrimary} onClick={() => setEditing({ item: null })}>
            <PlusIcon size={18} /> Add item
          </button>
        }
      />
      <div className="mb-4 flex flex-col gap-3">
        <SearchBox label="Search stock" placeholder="Name, SKU or category" onSearch={setQ} />
        <div className="rounded-2xl border border-line bg-panel">
          <ToggleRow title="Only items running low" checked={low} onChange={setLow} />
        </div>
      </div>
      <ErrorNote>{items.error}</ErrorNote>
      {!items.value ? (
        <LoadState loading={items.loading} error={null} />
      ) : list.length === 0 ? (
        <EmptyState icon={TagIcon} title={low ? "Nothing is running low" : "No stock items yet"}>
          Add cloth, lining, thread and buttons to track what is left.
        </EmptyState>
      ) : (
        <>
          <div className="mb-4 grid grid-cols-2 gap-3">
            <StatTile label="Items" value={list.length} />
            <StatTile label="Stock value" value={rupees(value)} />
          </div>
          <ul className="divide-y divide-line rounded-2xl border border-line bg-panel">
            {list.map((item) => (
              <li key={item.id} className="flex flex-wrap items-center gap-3 px-4 py-3">
                {item.photos[0] && (
                  // eslint-disable-next-line @next/next/no-img-element -- static export, R2 serves the resized file
                  <img
                    src={mediaUrl(item.photos[0].key)}
                    alt=""
                    className="size-12 shrink-0 rounded-lg border border-line object-cover"
                  />
                )}
                <Link href={`/stock/view/?id=${item.id}`} className="min-w-0 flex-1">
                  <span className="block font-medium">
                    {item.name} {item.low && <Pill tone="warn">Low</Pill>}
                  </span>
                  <span className="block text-sm text-ink-soft">
                    {[item.category, item.material, item.supplierName, item.sku].filter(Boolean).join(" · ") || "—"}
                  </span>
                </Link>
                <span className="text-right tabular-nums">
                  <span className="block font-medium">
                    {qty(item.onHand)} {unitLabel(item.unit)}
                  </span>
                  <span className="block text-xs text-ink-faint">reorder at {qty(item.reorderLevel)}</span>
                </span>
                <button type="button" className={buttonSecondary} onClick={() => setMoving(item)}>
                  In / out
                </button>
                <button
                  type="button"
                  className="text-sm text-accent-ink underline"
                  onClick={() => setEditing({ item })}
                >
                  Edit
                </button>
              </li>
            ))}
          </ul>
        </>
      )}
      {editing && (
        <ItemSheet
          item={editing.item}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            items.reload();
          }}
        />
      )}
      {moving && (
        <MoveSheet
          item={moving}
          onClose={() => setMoving(null)}
          onSaved={() => {
            setMoving(null);
            items.reload();
          }}
        />
      )}
    </section>
  );
}

function ItemSheet({ item, onClose, onSaved }: { item: StockItem | null; onClose: () => void; onSaved: () => void }) {
  const [name, setName] = useState(item?.name ?? "");
  const [sku, setSku] = useState(item?.sku ?? "");
  const [category, setCategory] = useState(item?.category ?? "");
  const [unit, setUnit] = useState<StockUnit>(item?.unit ?? "M");
  const [reorder, setReorder] = useState(item ? qty(item.reorderLevel) : "0");
  const [cost, setCost] = useState(item ? String(item.costRupees) : "");
  const [supplierName, setSupplierName] = useState(item?.supplierName ?? "");
  const [supplierPhone, setSupplierPhone] = useState(item?.supplierPhone ?? "");
  const [material, setMaterial] = useState(item?.material ?? "");
  const [colour, setColour] = useState(item?.colour ?? "");
  const [gsm, setGsm] = useState(item?.gsm == null ? "" : String(item.gsm));
  const [threadCount, setThreadCount] = useState(item?.threadCount == null ? "" : String(item.threadCount));
  const [width, setWidth] = useState(item?.widthIn == null ? "" : String(item.widthIn));
  const [notes, setNotes] = useState(item?.notes ?? "");
  const action = useAction();

  /** "" → null; a whole number otherwise (NaN when it is not one, caught below). */
  const wholeOrNull = (value: string) => (value.trim() === "" ? null : Number(value.trim()));

  async function save() {
    if (!name.trim()) return action.setError("Enter the name.");
    const costRupees = cost.trim() === "" ? 0 : parseRupees(cost);
    const numbers = [wholeOrNull(gsm), wholeOrNull(threadCount), wholeOrNull(width)];
    if (costRupees === null || Number.isNaN(Number(reorder)) || numbers.some((n) => n !== null && Number.isNaN(n)))
      return action.setError("Enter numbers for cost, reorder level, GSM, thread count and width.");
    const form: StockItemForm = {
      name: name.trim(),
      sku: sku.trim() || null,
      category: category.trim() || null,
      unit,
      reorderLevel: Number(reorder || "0"),
      costRupees,
      supplierName: supplierName.trim() || null,
      supplierPhone: supplierPhone.trim() || null,
      material: material.trim() || null,
      colour: colour.trim() || null,
      gsm: numbers[0] ?? null,
      threadCount: numbers[1] ?? null,
      widthIn: numbers[2] ?? null,
      notes: notes.trim() || null,
    };
    const saved = await action.run(() => (item ? api.admin.stock.update(item.id, form) : api.admin.stock.create(form)));
    if (saved) onSaved();
  }

  return (
    <Sheet open title={item ? "Edit item" : "New stock item"} onClose={onClose}>
      <div className="flex flex-col gap-4">
        <TextField label="Name" maxLength={120} value={name} onChange={(e) => setName(e.target.value)} />
        <div className="grid grid-cols-2 gap-3">
          <TextField
            label="Category"
            maxLength={40}
            placeholder="Lining"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
          />
          <TextField label="SKU" maxLength={40} value={sku} onChange={(e) => setSku(e.target.value)} />
          <SelectField label="Unit" options={UNITS} value={unit} onChange={setUnit} />
          <TextField
            label="Reorder at"
            inputMode="decimal"
            value={reorder}
            onChange={(e) => setReorder(e.target.value)}
          />
          <TextField
            label="Cost per unit (₹)"
            inputMode="decimal"
            value={cost}
            onChange={(e) => setCost(e.target.value)}
          />
        </div>
        <p className="text-sm font-medium text-ink-soft">Supplier</p>
        <div className="grid grid-cols-2 gap-3">
          <TextField
            label="Shop name"
            maxLength={120}
            placeholder="Sri Lakshmi Silks"
            value={supplierName}
            onChange={(e) => setSupplierName(e.target.value)}
          />
          <TextField
            label="Mobile number"
            type="tel"
            inputMode="tel"
            maxLength={20}
            value={supplierPhone}
            onChange={(e) => setSupplierPhone(e.target.value)}
          />
        </div>
        <p className="text-sm font-medium text-ink-soft">Fabric details</p>
        <div className="grid grid-cols-2 gap-3">
          <TextField
            label="Material"
            maxLength={60}
            placeholder="Cotton, silk…"
            value={material}
            onChange={(e) => setMaterial(e.target.value)}
          />
          <TextField label="Colour" maxLength={60} value={colour} onChange={(e) => setColour(e.target.value)} />
          <TextField label="GSM" inputMode="numeric" value={gsm} onChange={(e) => setGsm(e.target.value)} />
          <TextField
            label="Thread count"
            inputMode="numeric"
            value={threadCount}
            onChange={(e) => setThreadCount(e.target.value)}
          />
          <TextField
            label="Width (inches)"
            inputMode="decimal"
            value={width}
            onChange={(e) => setWidth(e.target.value)}
          />
        </div>
        <TextField label="Notes" maxLength={500} value={notes} onChange={(e) => setNotes(e.target.value)} />
        <ErrorNote>{action.error}</ErrorNote>
        <div className="flex flex-wrap gap-2">
          <button type="button" className={buttonPrimary} disabled={action.busy} onClick={() => void save()}>
            {action.busy ? "Saving…" : "Save item"}
          </button>
          {item && (
            <button
              type="button"
              className={`${buttonSecondary} text-bad`}
              disabled={action.busy}
              onClick={async () => {
                if (await action.run(() => api.admin.stock.remove(item.id).then(() => true))) onSaved();
              }}
            >
              Remove
            </button>
          )}
        </div>
      </div>
    </Sheet>
  );
}

function MoveSheet({ item, onClose, onSaved }: { item: StockItem; onClose: () => void; onSaved: () => void }) {
  const [kind, setKind] = useState<StockMovementForm["kind"]>("OUT");
  const [amount, setAmount] = useState("");
  const [note, setNote] = useState("");
  const action = useAction();
  return (
    <Sheet open title={`${item.name}: in or out`} onClose={onClose}>
      <div className="flex flex-col gap-4">
        <p className="text-ink-soft">
          {qty(item.onHand)} {unitLabel(item.unit)} in stock
        </p>
        <SegmentedControl
          label="Movement"
          options={[
            { value: "IN", label: "Bought / in" },
            { value: "OUT", label: "Used / out" },
            { value: "ADJUST", label: "Correct count" },
          ]}
          value={kind}
          onChange={setKind}
        />
        <TextField
          label={
            kind === "ADJUST" ? `Change (${unitLabel(item.unit)}, − to reduce)` : `How much (${unitLabel(item.unit)})`
          }
          inputMode="decimal"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
        />
        <TextField label="Note" maxLength={200} value={note} onChange={(e) => setNote(e.target.value)} />
        <ErrorNote>{action.error}</ErrorNote>
        <button
          type="button"
          className={buttonPrimary}
          disabled={action.busy}
          onClick={async () => {
            const quantity = Number(amount);
            if (!amount.trim() || Number.isNaN(quantity) || quantity === 0) return action.setError("Enter how much.");
            const saved = await action.run(() =>
              api.admin.stock.move(item.id, { kind, quantity, orderId: null, note: note.trim() || null }),
            );
            if (saved) onSaved();
          }}
        >
          {action.busy ? "Saving…" : "Save"}
        </button>
      </div>
    </Sheet>
  );
}

export default function StockPage() {
  return (
    <Suspense fallback={<p className="text-ink-soft">Loading…</p>}>
      <Stock />
    </Suspense>
  );
}
