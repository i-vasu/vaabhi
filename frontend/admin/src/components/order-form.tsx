"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import type {
  ClientSummary,
  DiscountKind,
  GarmentTemplate,
  Measurement,
  OrderForm,
  PaymentMode,
  ServiceProduct,
} from "@vaabhi/api-client";
import { api } from "@/lib/api";
import { newKey } from "@/lib/product-editor";
import { describeError, parseRupees, rupees } from "@/lib/settings";
import { PAYMENT_MODES, addDays, orderTotals, parsePercent, today, useLoad } from "@/lib/tailoring";
import { PlusIcon, TrashIcon } from "./icons";
import { ClientPicker, PhotoInput } from "./tailoring";
import {
  LoadState,
  SaveBar,
  SectionLabel,
  SegmentedControl,
  SelectField,
  TextAreaField,
  TextField,
  buttonSecondary,
} from "./ui";

export type ItemRow = {
  key: string;
  templateId: number | null;
  serviceProductId: number | null;
  description: string;
  quantity: string;
  price: string;
  colour: string;
  note: string;
  /** Null until edited: the customer's latest measurements for the garment are used. */
  measurements: Record<string, string> | null;
  clothPhotoKey: string | null;
};

export type OrderDraft = {
  orderDate: string;
  deliveryDate: string;
  dueTime: string;
  trialDate: string;
  deliveryPackageId: number | null;
  items: ItemRow[];
  discountKind: DiscountKind;
  discount: string;
  couponCode: string;
  advance: string;
  advanceMode: PaymentMode;
  notes: string;
};

export function blankItem(templateId: number | null = null): ItemRow {
  return {
    key: newKey(),
    templateId,
    serviceProductId: null,
    description: "",
    quantity: "1",
    price: "",
    colour: "",
    note: "",
    measurements: null,
    clothPhotoKey: null,
  };
}

export function blankDraft(now: Date = new Date()): OrderDraft {
  const date = today(now);
  return {
    orderDate: date,
    deliveryDate: addDays(date, 7),
    dueTime: "",
    trialDate: "",
    deliveryPackageId: null,
    items: [blankItem()],
    discountKind: "AMOUNT",
    discount: "",
    couponCode: "",
    advance: "",
    advanceMode: "CASH",
    notes: "",
  };
}

/** The newest measurements the customer has for a garment, copied onto a new item. */
export function latestMeasurements(measurements: Measurement[], templateId: number | null): Record<string, string> {
  return measurements.find((m) => m.templateId === templateId)?.values ?? {};
}

/** What the item will be stitched to: what was typed, or else the customer's latest for the garment. */
export function itemMeasurements(row: ItemRow, known: Measurement[]): Record<string, string> {
  return row.measurements ?? latestMeasurements(known, row.templateId);
}

/** Turns the draft into the API form, or explains the first thing that still needs filling in. */
export function formFromDraft(
  clientId: number | null,
  draft: OrderDraft,
  known: Measurement[] = [],
): { form: OrderForm; error: null } | { form: null; error: string } {
  if (clientId === null) return { form: null, error: "Choose the customer." };
  const items = [];
  for (const [index, row] of draft.items.entries()) {
    const label = `Item ${index + 1}`;
    if (!row.description.trim()) return { form: null, error: `${label}: say what is being stitched.` };
    const priceRupees = parseRupees(row.price);
    if (priceRupees === null) return { form: null, error: `${label}: enter the price.` };
    const quantity = Number(row.quantity);
    if (!Number.isInteger(quantity) || quantity < 1) return { form: null, error: `${label}: enter the quantity.` };
    const measurements = Object.fromEntries(
      Object.entries(itemMeasurements(row, known)).filter(([, value]) => value.trim()),
    );
    items.push({
      templateId: row.templateId,
      serviceProductId: row.serviceProductId,
      description: row.description.trim(),
      quantity,
      priceRupees,
      colour: row.colour.trim() || null,
      note: row.note.trim() || null,
      measurements: Object.keys(measurements).length > 0 ? measurements : null,
      clothPhotoKey: row.clothPhotoKey,
    });
  }
  const discountValue =
    draft.discount.trim() === ""
      ? 0
      : draft.discountKind === "PERCENT"
        ? parsePercent(draft.discount)
        : parseRupees(draft.discount);
  if (discountValue === null) return { form: null, error: "Enter the discount as a number." };
  const advanceRupees = draft.advance.trim() === "" ? 0 : parseRupees(draft.advance);
  if (advanceRupees === null) return { form: null, error: "Enter the advance as a number." };
  if (!draft.deliveryDate) return { form: null, error: "Pick a delivery date." };
  return {
    error: null,
    form: {
      clientId,
      orderDate: draft.orderDate,
      deliveryDate: draft.deliveryDate,
      dueTime: draft.dueTime || null,
      trialDate: draft.trialDate || null,
      deliveryPackageId: draft.deliveryPackageId,
      items,
      discountKind: draft.discountKind,
      discountValue,
      couponCode: draft.couponCode.trim() || null,
      // The API now takes reward points to redeem on the booking; this form does not offer it yet.
      redeemPoints: 0,
      advanceRupees,
      advanceMode: draft.advanceMode,
      notes: draft.notes.trim() || null,
    },
  };
}

export function OrderEditor({ initialClient }: { initialClient: ClientSummary | null }) {
  const router = useRouter();
  const [client, setClient] = useState<ClientSummary | null>(initialClient);
  const [draft, setDraft] = useState<OrderDraft>(() => blankDraft());
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [coupon, setCoupon] = useState<string | null>(null);

  const setup = useLoad(
    () =>
      Promise.all([
        api.admin.settings.garmentTemplates(),
        api.admin.serviceProducts.list(),
        api.admin.settings.deliveryPackages(),
        api.admin.settings.get("billing"),
        api.admin.settings.get("orders"),
      ]),
    [],
  );
  const full = useLoad(() => (client ? api.admin.clients.get(client.id) : Promise.resolve(null)), [client?.id]);
  const measurements = full.value?.measurements ?? [];

  if (!setup.value) return <LoadState loading={setup.loading} error={setup.error} />;
  const [templates, products, packages, billing, orderSettings] = setup.value;

  const update = (patch: Partial<OrderDraft>) => setDraft((d) => ({ ...d, ...patch }));
  const updateItem = (key: string, patch: Partial<ItemRow>) =>
    setDraft((d) => ({ ...d, items: d.items.map((row) => (row.key === key ? { ...row, ...patch } : row)) }));

  const pkg = packages.find((p) => p.id === draft.deliveryPackageId);
  const priced = draft.items.map((row) => ({
    priceRupees: parseRupees(row.price) ?? 0,
    quantity: Math.max(0, Math.floor(Number(row.quantity) || 0)),
  }));
  const discountValue =
    (draft.discountKind === "PERCENT" ? parsePercent(draft.discount) : parseRupees(draft.discount)) ?? 0;
  const totals = orderTotals(
    priced,
    pkg?.priceRupees ?? 0,
    draft.discountKind,
    discountValue,
    billing.discountAppliesTo,
  );
  const advanceRupees = parseRupees(draft.advance) ?? 0;

  async function save() {
    const result = formFromDraft(client?.id ?? null, draft, measurements);
    if (result.error !== null) {
      setError(result.error);
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const order = await api.admin.orders.create(result.form);
      router.replace(`/orders/view/?id=${order.id}`);
    } catch (e) {
      setError(describeError(e).message);
      setSaving(false);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <SectionLabel>Customer</SectionLabel>
      <ClientPicker value={client} onChange={setClient} />

      <SectionLabel
        action={
          <button
            type="button"
            className={buttonSecondary}
            onClick={() => update({ items: [...draft.items, blankItem(draft.items.at(-1)?.templateId ?? null)] })}
          >
            <PlusIcon size={18} /> Add item
          </button>
        }
      >
        Items
      </SectionLabel>
      {draft.items.map((row, index) => (
        <ItemEditor
          key={row.key}
          index={index}
          row={row}
          templates={templates}
          products={products}
          measurements={measurements}
          onChange={(patch) => updateItem(row.key, patch)}
          onRemove={
            draft.items.length > 1
              ? () => update({ items: draft.items.filter((other) => other.key !== row.key) })
              : undefined
          }
        />
      ))}

      <SectionLabel>Delivery</SectionLabel>
      {packages.length > 0 && (
        <SelectField
          label="Delivery package"
          options={[
            { value: "", label: "None" },
            ...packages.map((p) => ({
              value: String(p.id),
              label: `${p.name} · ${p.days} days${p.priceRupees > 0 ? ` · +${rupees(p.priceRupees)}` : ""}`,
            })),
          ]}
          value={draft.deliveryPackageId === null ? "" : String(draft.deliveryPackageId)}
          onChange={(value) => {
            const picked = packages.find((p) => String(p.id) === value);
            update({
              deliveryPackageId: picked?.id ?? null,
              ...(picked ? { deliveryDate: addDays(draft.orderDate, picked.days) } : {}),
            });
          }}
        />
      )}
      <div className="grid gap-4 sm:grid-cols-3">
        <TextField
          label="Order date"
          type="date"
          value={draft.orderDate}
          onChange={(event) => update({ orderDate: event.target.value })}
        />
        <TextField
          label="Delivery date"
          type="date"
          min={draft.orderDate}
          value={draft.deliveryDate}
          onChange={(event) => update({ deliveryDate: event.target.value })}
        />
        <TextField
          label="Due time"
          type="time"
          value={draft.dueTime}
          onChange={(event) => update({ dueTime: event.target.value })}
        />
      </div>
      {orderSettings.trialDate && (
        <TextField
          label="Trial date"
          type="date"
          min={draft.orderDate}
          max={draft.deliveryDate}
          value={draft.trialDate}
          onChange={(event) => update({ trialDate: event.target.value })}
        />
      )}

      <SectionLabel>Payment</SectionLabel>
      <div className="grid gap-4 sm:grid-cols-2">
        <div>
          <SegmentedControl
            label="Discount type"
            options={[
              { value: "AMOUNT", label: "₹ off" },
              { value: "PERCENT", label: "% off" },
            ]}
            value={draft.discountKind}
            onChange={(discountKind) => update({ discountKind })}
          />
          <TextField
            className="mt-2"
            label={draft.discountKind === "PERCENT" ? "Discount (%)" : "Discount (₹)"}
            inputMode="decimal"
            value={draft.discount}
            onChange={(event) => update({ discount: event.target.value })}
          />
        </div>
        <div>
          <div className="flex items-end gap-2">
            <TextField
              className="flex-1"
              label="Coupon code"
              maxLength={40}
              value={draft.couponCode}
              onChange={(event) => {
                update({ couponCode: event.target.value });
                setCoupon(null);
              }}
            />
            <button
              type="button"
              className={buttonSecondary}
              disabled={!draft.couponCode.trim()}
              onClick={async () => {
                try {
                  const check = await api.admin.engage.checkCoupon(
                    draft.couponCode.trim(),
                    totals.itemsRupees + totals.deliveryRupees,
                  );
                  setCoupon(check.valid ? `${check.code}: ${rupees(check.discountRupees)} off` : check.reason);
                  if (check.valid && check.kind) {
                    update({
                      couponCode: check.code,
                      discountKind: check.kind,
                      discount: String(check.value ?? ""),
                    });
                  }
                } catch (e) {
                  setCoupon(describeError(e).message);
                }
              }}
            >
              Apply
            </button>
          </div>
          {coupon && (
            <p role="status" className="mt-1.5 text-sm text-ink-soft">
              {coupon}
            </p>
          )}
        </div>
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField
          label="Advance (₹)"
          inputMode="decimal"
          value={draft.advance}
          error={advanceRupees > totals.totalRupees ? "More than the order total" : undefined}
          onChange={(event) => update({ advance: event.target.value })}
        />
        <SelectField
          label="Advance paid by"
          options={PAYMENT_MODES}
          value={draft.advanceMode}
          onChange={(advanceMode) => update({ advanceMode })}
        />
      </div>
      <TextAreaField
        label="Notes"
        maxLength={2000}
        value={draft.notes}
        onChange={(event) => update({ notes: event.target.value })}
      />

      <dl aria-label="Order total" className="rounded-2xl border border-line bg-panel p-4 text-sm">
        <Line label="Items" value={rupees(totals.itemsRupees)} />
        {totals.deliveryRupees > 0 && <Line label="Delivery" value={rupees(totals.deliveryRupees)} />}
        {totals.discountRupees > 0 && <Line label="Discount" value={`− ${rupees(totals.discountRupees)}`} />}
        <Line label="Total" value={rupees(totals.totalRupees)} strong />
        {advanceRupees > 0 && <Line label="Advance" value={`− ${rupees(advanceRupees)}`} />}
        {advanceRupees > 0 && <Line label="Balance" value={rupees(totals.totalRupees - advanceRupees)} strong />}
      </dl>
      <SaveBar saving={saving} saved={false} error={error} onSave={() => void save()} label="Book order" />
    </div>
  );
}

function Line({ label, value, strong = false }: { label: string; value: string; strong?: boolean }) {
  return (
    <div
      className={strong ? "mt-1 flex justify-between text-base font-semibold" : "flex justify-between text-ink-soft"}
    >
      <dt>{label}</dt>
      <dd className="tabular-nums">{value}</dd>
    </div>
  );
}

function ItemEditor({
  index,
  row,
  templates,
  products,
  measurements,
  onChange,
  onRemove,
}: {
  index: number;
  row: ItemRow;
  templates: GarmentTemplate[];
  products: ServiceProduct[];
  measurements: Measurement[];
  onChange: (patch: Partial<ItemRow>) => void;
  onRemove?: () => void;
}) {
  const [showMeasurements, setShowMeasurements] = useState(false);
  const template = templates.find((t) => t.id === row.templateId);
  const choices = products.filter((p) => row.templateId === null || p.templateId === row.templateId);
  const shown = itemMeasurements(row, measurements);
  const filled = Object.values(shown).filter((v) => v.trim()).length;

  return (
    <fieldset className="rounded-2xl border border-line bg-panel p-4">
      <legend className="sr-only">Item {index + 1}</legend>
      <div className="mb-3 flex items-center">
        <span className="font-medium">Item {index + 1}</span>
        {onRemove && (
          <button
            type="button"
            aria-label={`Remove item ${index + 1}`}
            className="ml-auto rounded-lg p-2 text-bad hover:bg-bad-soft"
            onClick={onRemove}
          >
            <TrashIcon size={18} />
          </button>
        )}
      </div>
      <div className="grid gap-3 sm:grid-cols-2">
        <SelectField
          label="Garment"
          options={[{ value: "", label: "Other" }, ...templates.map((t) => ({ value: String(t.id), label: t.name }))]}
          value={row.templateId === null ? "" : String(row.templateId)}
          onChange={(value) => {
            const templateId = value === "" ? null : Number(value);
            onChange({ templateId, serviceProductId: null, measurements: null });
          }}
        />
        <SelectField
          label="From price list"
          options={[
            { value: "", label: "Custom" },
            ...choices.map((p) => ({ value: String(p.id), label: `${p.name} · ${rupees(p.priceRupees)}` })),
          ]}
          value={row.serviceProductId === null ? "" : String(row.serviceProductId)}
          onChange={(value) => {
            const product = choices.find((p) => String(p.id) === value);
            if (!product) {
              onChange({ serviceProductId: null });
              return;
            }
            onChange({
              serviceProductId: product.id,
              templateId: product.templateId,
              description: product.name,
              price: String(product.priceRupees),
              ...(product.templateId !== row.templateId ? { measurements: null } : {}),
            });
          }}
        />
      </div>
      <div className="mt-3 grid grid-cols-[1fr_5rem_7rem] gap-3">
        <TextField
          label="Description"
          maxLength={120}
          value={row.description}
          onChange={(event) => onChange({ description: event.target.value })}
        />
        <TextField
          label="Qty"
          inputMode="numeric"
          value={row.quantity}
          onChange={(event) => onChange({ quantity: event.target.value })}
        />
        <TextField
          label="Price (₹)"
          inputMode="decimal"
          value={row.price}
          onChange={(event) => onChange({ price: event.target.value })}
        />
      </div>
      <div className="mt-3 grid gap-3 sm:grid-cols-2">
        <TextField
          label="Colour"
          maxLength={60}
          value={row.colour}
          onChange={(event) => onChange({ colour: event.target.value })}
        />
        <TextField
          label="Note"
          maxLength={500}
          value={row.note}
          onChange={(event) => onChange({ note: event.target.value })}
        />
      </div>
      <div className="mt-3">
        <PhotoInput
          label="Cloth photo"
          purpose="CLOTH"
          value={row.clothPhotoKey}
          onChange={(clothPhotoKey) => onChange({ clothPhotoKey })}
        />
      </div>
      {template && template.fields.length > 0 && (
        <div className="mt-3">
          <button
            type="button"
            aria-expanded={showMeasurements}
            className="text-sm text-accent-ink underline"
            onClick={() => setShowMeasurements(!showMeasurements)}
          >
            Measurements ({filled} of {template.fields.length})
          </button>
          {showMeasurements && (
            <div className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-3">
              {template.fields.map((field) => (
                <TextField
                  key={field.key}
                  label={field.unit ? `${field.label} (${field.unit})` : field.label}
                  maxLength={40}
                  value={shown[field.key] ?? ""}
                  onChange={(event) => onChange({ measurements: { ...shown, [field.key]: event.target.value } })}
                />
              ))}
            </div>
          )}
        </div>
      )}
    </fieldset>
  );
}
