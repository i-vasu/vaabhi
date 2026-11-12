"use client";

import { useState } from "react";
import type { BillingSettings, NumberSequence, TaxClass, TaxClassForm } from "@vaabhi/api-client";
import { BankIcon, DocumentIcon, PlusIcon, TagIcon, TrashIcon } from "@/components/icons";
import { ErrorNote } from "@/components/tailoring";
import {
  EmptyState,
  ListCard,
  ListRow,
  LoadState,
  PageHeader,
  SaveBar,
  SectionLabel,
  SelectField,
  Sheet,
  TextField,
  ToggleRow,
  buttonPrimary,
  buttonSecondary,
} from "@/components/ui";
import { api } from "@/lib/api";
import { orNull, useSaveable, useSection } from "@/lib/settings";
import { useAction, useLoad } from "@/lib/tailoring";

const kinds: { value: TaxClass["kind"]; label: string }[] = [
  { value: "GOODS", label: "Goods (HSN)" },
  { value: "SERVICE", label: "Service (SAC)" },
];

const blankClass: TaxClass = {
  id: 0,
  name: "",
  kind: "GOODS",
  hsnCode: "",
  ratePercent: 0,
  slabLimitRupees: null,
  rateAbovePercent: null,
  inUse: false,
};

/** "5% up to ₹2,500, then 18%", or just "5%" when the rate does not change with the price. */
export function rateText(taxClass: TaxClass): string {
  if (taxClass.slabLimitRupees === null || taxClass.rateAbovePercent === null) return `${taxClass.ratePercent}%`;
  const limit = taxClass.slabLimitRupees.toLocaleString("en-IN", { maximumFractionDigits: 2 });
  return `${taxClass.ratePercent}% up to ₹${limit}, then ${taxClass.rateAbovePercent}%`;
}

const discountBases: { value: BillingSettings["discountAppliesTo"]; label: string }[] = [
  { value: "ORDER_TOTAL", label: "Order total" },
  { value: "ITEMS_BEFORE_TAX", label: "Items, before tax" },
];

const sequenceTitles: Record<NumberSequence["kind"], string> = {
  ORDER: "Order numbering",
  INVOICE: "Invoice numbering",
  ALTERATION: "Alteration numbering",
  ITEM: "Item codes",
  WEB_ORDER: "Website order numbering",
  CREDIT_NOTE: "Credit note numbering",
  STOCK_ITEM: "Stock codes",
  PRODUCT: "Product style codes",
};

const RESETS: { value: NonNullable<NumberSequence["reset"]>; label: string }[] = [
  { value: "NEVER", label: "One running series" },
  { value: "FY", label: "Start again every April" },
];

/** The financial year (April–March) a date falls in, as {FY} prints it: "26-27". Mirrors the server. */
export function financialYear(date: Date): string {
  const start = date.getMonth() >= 3 ? date.getFullYear() : date.getFullYear() - 1;
  const two = (year: number) => String(year % 100).padStart(2, "0");
  return `${two(start)}-${two(start + 1)}`;
}

/** Mirrors NumberSequence.format on the server, so the preview updates while typing. */
export function preview(sequence: NumberSequence, today: Date = new Date()): string {
  const number = sequence.pad > 0 ? String(sequence.nextValue).padStart(sequence.pad, "0") : String(sequence.nextValue);
  return `${sequence.prefix}${number}${sequence.suffix}`.replaceAll("{FY}", financialYear(today));
}

export default function BillingPage() {
  const billing = useSection("billing");
  const numbering = useSaveable(api.admin.settings.numbering, api.admin.settings.saveNumbering);
  const states = useLoad(() => api.admin.settings.states(), []);
  const taxClasses = useLoad(() => api.admin.money.taxClasses.list(), []);
  const categories = useLoad(() => api.admin.categories.list(), []);
  const categoryAction = useAction();
  const [editing, setEditing] = useState<TaxClass | null>(null);
  const b = billing.value;
  const defaultName = taxClasses.value?.find((c) => c.id === b?.defaultTaxClassId)?.name ?? "none";
  const company = b?.company ?? { legalName: null, cin: null, registeredOffice: null, state: null };

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader title="Billing & tax" backHref="/settings/" />

      <SectionLabel>GST</SectionLabel>
      {!b ? (
        <LoadState loading={billing.loading} error={billing.loadError} />
      ) : (
        <>
          <div className="rounded-2xl border border-line bg-panel">
            <ToggleRow
              title="Charge GST"
              subtitle={b.chargeGst ? "Bills carry GST lines" : "Bills carry no GST"}
              checked={b.chargeGst}
              onChange={(chargeGst) => billing.update({ chargeGst })}
            />
            <p className="px-4 pb-3 text-sm text-ink-faint">
              When on: each line is taxed by its tax class, orders to other states get IGST, and bills are titled Tax
              Invoice. Check the rates with your CA first.
            </p>
            <div className="px-4 pb-4">
              <TextField
                label="GSTIN"
                placeholder="e.g. 27ABCDE1234F1Z5"
                maxLength={15}
                value={b.gstin ?? ""}
                onChange={(event) => billing.update({ gstin: orNull(event.target.value.toUpperCase()) })}
                error={billing.fieldError("gstin")}
                hint="Leave GSTIN blank to bill without GST tax lines."
              />
            </div>
          </div>
          <div className="mt-4">
            <ListCard>
              <div className="grid gap-4 p-4 sm:grid-cols-2">
                <SelectField
                  label="Default tax class"
                  hint="Used for anything without a tax class of its own"
                  options={[
                    { value: "", label: "None" },
                    ...(taxClasses.value ?? []).map((c) => ({ value: String(c.id), label: c.name })),
                  ]}
                  value={b.defaultTaxClassId === null ? "" : String(b.defaultTaxClassId)}
                  onChange={(value) => billing.update({ defaultTaxClassId: value === "" ? null : Number(value) })}
                  error={billing.fieldError("defaultTaxClassId")}
                />
                <SelectField
                  label="Discount comes off"
                  hint="What a discount at booking is taken off"
                  options={discountBases}
                  value={b.discountAppliesTo}
                  onChange={(discountAppliesTo) => billing.update({ discountAppliesTo })}
                />
              </div>
              <ToggleRow
                title="Prices include tax"
                subtitle="GST is included in the product price"
                checked={b.pricesIncludeTax}
                onChange={(pricesIncludeTax) => billing.update({ pricesIncludeTax })}
              />
              <ToggleRow
                title="HSN codes"
                subtitle={b.showHsnCodes ? "Printed on invoices" : "Hidden everywhere"}
                checked={b.showHsnCodes}
                onChange={(showHsnCodes) => billing.update({ showHsnCodes })}
              />
              <ToggleRow
                title="Auto-generate invoice"
                subtitle="Create a bill when an order is delivered"
                checked={b.autoInvoice}
                onChange={(autoInvoice) => billing.update({ autoInvoice })}
              />
            </ListCard>
          </div>
          <SaveBar
            label="Save GST settings"
            saving={billing.saving}
            saved={billing.saved}
            error={billing.error}
            onSave={() => void billing.save()}
          />
        </>
      )}

      {b && (
        <>
          <SectionLabel>Company</SectionLabel>
          <div className="rounded-2xl border border-line bg-panel p-4">
            <div className="grid gap-4 sm:grid-cols-2">
              <TextField
                label="Legal name"
                value={company.legalName ?? ""}
                onChange={(event) => updateCompany({ legalName: orNull(event.target.value) })}
                error={billing.fieldError("company.legalName")}
              />
              <TextField
                label="CIN"
                maxLength={21}
                value={company.cin ?? ""}
                onChange={(event) => updateCompany({ cin: orNull(event.target.value.toUpperCase()) })}
                error={billing.fieldError("company.cin")}
              />
              <TextField
                label="Registered office"
                className="sm:col-span-2"
                value={company.registeredOffice ?? ""}
                onChange={(event) => updateCompany({ registeredOffice: orNull(event.target.value) })}
                error={billing.fieldError("company.registeredOffice")}
              />
              <SelectField
                label="State"
                hint="Printed on invoices with its GST state code"
                options={[
                  { value: "", label: "Not set" },
                  ...(states.value ?? []).map((s) => ({ value: s.name, label: `${s.name} (${s.code})` })),
                ]}
                value={company.state ?? ""}
                onChange={(state) => updateCompany({ state: orNull(state) })}
              />
            </div>
          </div>
          <SaveBar
            label="Save company details"
            saving={billing.saving}
            saved={billing.saved}
            error={billing.error}
            onSave={() => void billing.save()}
          />
        </>
      )}

      <SectionLabel>Tax classes</SectionLabel>
      <div className="mb-3 flex justify-end">
        <button type="button" className={buttonSecondary} onClick={() => setEditing(blankClass)}>
          <PlusIcon size={18} /> Add tax class
        </button>
      </div>
      {!taxClasses.value ? (
        <LoadState loading={taxClasses.loading} error={taxClasses.error} />
      ) : taxClasses.value.length === 0 ? (
        <EmptyState icon={TagIcon} title="No tax classes yet">
          Add a tax class with its HSN or SAC code and rate.
        </EmptyState>
      ) : (
        <ListCard>
          {taxClasses.value.map((taxClass) => (
            <button
              key={taxClass.id}
              type="button"
              className="block w-full text-left hover:bg-panel-2"
              onClick={() => setEditing(taxClass)}
            >
              <ListRow
                title={taxClass.name}
                subtitle={`${taxClass.kind === "GOODS" ? "HSN" : "SAC"} ${taxClass.hsnCode}`}
                trailing={rateText(taxClass)}
              />
            </button>
          ))}
        </ListCard>
      )}
      <p className="mt-2 text-sm text-ink-faint">
        The starting rates are placeholders. Confirm them with your CA before you charge GST.
      </p>
      {editing && (
        <TaxClassSheet
          taxClass={editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            taxClasses.reload();
          }}
        />
      )}

      <SectionLabel>Tax by category</SectionLabel>
      {!categories.value ? (
        <LoadState loading={categories.loading} error={categories.error} />
      ) : categories.value.length === 0 ? (
        <EmptyState icon={TagIcon} title="No categories yet">
          Categories added in the catalogue show up here.
        </EmptyState>
      ) : (
        <div className="rounded-2xl border border-line bg-panel p-4">
          <ErrorNote>{categoryAction.error}</ErrorNote>
          <div className="grid gap-4 sm:grid-cols-2">
            {categories.value.map((category) => (
              <SelectField
                key={category.id}
                label={category.name}
                options={[
                  { value: "", label: `Default (${defaultName})` },
                  ...(taxClasses.value ?? []).map((c) => ({ value: String(c.id), label: c.name })),
                ]}
                value={category.taxClassId === null ? "" : String(category.taxClassId)}
                disabled={categoryAction.busy}
                onChange={async (value) => {
                  const done = await categoryAction.run(() =>
                    api.admin.categories.setTaxClass(category.id, value === "" ? null : Number(value)).then(() => true),
                  );
                  if (done) categories.reload();
                }}
              />
            ))}
          </div>
        </div>
      )}

      <SectionLabel>Numbering</SectionLabel>
      {!numbering.value ? (
        <LoadState loading={numbering.loading} error={numbering.loadError} />
      ) : (
        <>
          <div className="flex flex-col gap-3">
            {numbering.value.map((sequence, index) => (
              <div key={sequence.kind} className="rounded-2xl border border-line bg-panel p-4">
                <div className="mb-3 flex items-center justify-between gap-2">
                  <h3 className="font-semibold">{sequenceTitles[sequence.kind]}</h3>
                  <span className="rounded-lg bg-accent-soft px-3 py-1 text-sm text-accent-ink">
                    Next: {preview(sequence)}
                  </span>
                </div>
                <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                  <TextField
                    label="Prefix"
                    maxLength={20}
                    value={sequence.prefix}
                    onChange={(event) => replaceSequence(index, { ...sequence, prefix: event.target.value })}
                  />
                  <TextField
                    label="Next no."
                    type="number"
                    min={1}
                    value={sequence.nextValue}
                    onChange={(event) =>
                      replaceSequence(index, { ...sequence, nextValue: Math.max(1, Number(event.target.value)) })
                    }
                  />
                  <TextField
                    label="Suffix"
                    maxLength={20}
                    value={sequence.suffix}
                    onChange={(event) => replaceSequence(index, { ...sequence, suffix: event.target.value })}
                  />
                  <SelectField
                    label="Restart"
                    options={RESETS}
                    value={sequence.reset ?? "NEVER"}
                    onChange={(reset) => replaceSequence(index, { ...sequence, reset })}
                  />
                </div>
              </div>
            ))}
          </div>
          <p className="mt-2 text-sm text-ink-faint">
            Lowering a next number can repeat numbers already used, so only do it for a fresh start. Put {"{FY}"} in a
            prefix to print the financial year; invoices and credit notes start again every April, as GST requires.
          </p>
          <SaveBar
            label="Save numbering"
            saving={numbering.saving}
            saved={numbering.saved}
            error={numbering.error}
            onSave={() => void numbering.save()}
          />
        </>
      )}

      <SectionLabel>On the bill</SectionLabel>
      <ListCard>
        <ListRow
          icon={BankIcon}
          title="Bank & documents"
          subtitle="Bank details, UPI, slogan, alteration wording"
          href="/settings/documents/"
        />
        <ListRow
          icon={DocumentIcon}
          title="Terms & conditions"
          subtitle="Printed at the foot of the invoice"
          href="/settings/documents/#terms"
        />
      </ListCard>
    </section>
  );

  function updateCompany(change: Partial<NonNullable<BillingSettings["company"]>>) {
    billing.update({ company: { ...company, ...change } });
  }

  function replaceSequence(index: number, sequence: NumberSequence) {
    numbering.setValue(numbering.value!.map((s, i) => (i === index ? sequence : s)));
  }
}

function TaxClassSheet({
  taxClass,
  onClose,
  onSaved,
}: {
  taxClass: TaxClass;
  onClose: () => void;
  onSaved: () => void;
}) {
  const isNew = taxClass.id === 0;
  const [name, setName] = useState(taxClass.name);
  const [kind, setKind] = useState(taxClass.kind);
  const [hsnCode, setHsnCode] = useState(taxClass.hsnCode);
  const [rate, setRate] = useState(isNew ? "" : String(taxClass.ratePercent));
  const [limit, setLimit] = useState(taxClass.slabLimitRupees === null ? "" : String(taxClass.slabLimitRupees));
  const [above, setAbove] = useState(taxClass.rateAbovePercent === null ? "" : String(taxClass.rateAbovePercent));
  const action = useAction();

  async function save() {
    const ratePercent = Number(rate);
    if (!name.trim()) return action.setError("Enter the name.");
    if (!hsnCode.trim()) return action.setError("Enter the HSN or SAC code.");
    if (rate.trim() === "" || Number.isNaN(ratePercent)) return action.setError("Enter the rate.");
    const hasLimit = limit.trim() !== "";
    const hasAbove = above.trim() !== "";
    if (hasLimit !== hasAbove) return action.setError("Enter both the limit and the rate above it, or neither.");
    const form: TaxClassForm = {
      name: name.trim(),
      kind,
      hsnCode: hsnCode.trim(),
      ratePercent,
      slabLimitRupees: hasLimit ? Number(limit) : null,
      rateAbovePercent: hasAbove ? Number(above) : null,
    };
    if ((hasLimit && Number.isNaN(form.slabLimitRupees)) || (hasAbove && Number.isNaN(form.rateAbovePercent))) {
      return action.setError("Enter the limit and the rate above it as numbers.");
    }
    const saved = await action.run(() =>
      isNew ? api.admin.money.taxClasses.create(form) : api.admin.money.taxClasses.update(taxClass.id, form),
    );
    if (saved) onSaved();
  }

  return (
    <Sheet open title={isNew ? "New tax class" : "Edit tax class"} onClose={onClose}>
      <div className="flex flex-col gap-4">
        <TextField label="Name" maxLength={60} value={name} onChange={(e) => setName(e.target.value)} />
        <div className="grid grid-cols-2 gap-3">
          <SelectField label="Kind" options={kinds} value={kind} onChange={setKind} />
          <TextField
            label="HSN / SAC code"
            maxLength={8}
            value={hsnCode}
            onChange={(e) => setHsnCode(e.target.value)}
          />
        </div>
        <TextField label="Rate %" inputMode="decimal" value={rate} onChange={(e) => setRate(e.target.value)} />
        <div className="grid grid-cols-2 gap-3">
          <TextField
            label="Limit per piece (₹)"
            hint="Optional"
            inputMode="decimal"
            value={limit}
            onChange={(e) => setLimit(e.target.value)}
          />
          <TextField
            label="Rate above limit %"
            hint="Optional"
            inputMode="decimal"
            value={above}
            onChange={(e) => setAbove(e.target.value)}
          />
        </div>
        <ErrorNote>{action.error}</ErrorNote>
        <div className="flex flex-wrap gap-2">
          <button type="button" className={buttonPrimary} disabled={action.busy} onClick={() => void save()}>
            {action.busy ? "Saving…" : "Save tax class"}
          </button>
          {!isNew && (
            <button
              type="button"
              className={`${buttonSecondary} text-bad`}
              disabled={action.busy}
              onClick={async () => {
                const done = await action.run(() => api.admin.money.taxClasses.remove(taxClass.id).then(() => true));
                if (done) onSaved();
              }}
            >
              <TrashIcon size={18} /> Remove
            </button>
          )}
        </div>
      </div>
    </Sheet>
  );
}
