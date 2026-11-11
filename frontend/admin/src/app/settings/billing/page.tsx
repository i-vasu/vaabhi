"use client";

import type { BillingSettings, NumberSequence } from "@vaabhi/api-client";
import { BankIcon, DocumentIcon } from "@/components/icons";
import {
  ListCard,
  ListRow,
  LoadState,
  PageHeader,
  SaveBar,
  SectionLabel,
  SelectField,
  TextField,
  ToggleRow,
} from "@/components/ui";
import { api } from "@/lib/api";
import { orNull, useSaveable, useSection } from "@/lib/settings";
import { useLoad } from "@/lib/tailoring";

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
  const b = billing.value;
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
                <TextField
                  label="Default GST rate %"
                  hint="Starting rate for a new category"
                  inputMode="decimal"
                  value={b.defaultGstRate}
                  onChange={(event) => billing.update({ defaultGstRate: Number(event.target.value) || 0 })}
                  error={billing.fieldError("defaultGstRate")}
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
