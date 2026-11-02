"use client";

import type { PaperFormat, PrintSettings } from "@vaabhi/api-client";
import { ListCard, LoadState, PageHeader, SaveBar, SectionLabel, SelectField, ToggleRow } from "@/components/ui";
import { useSection } from "@/lib/settings";

const formats: { value: PaperFormat; label: string; note: string }[] = [
  { value: "A4", label: "A4", note: "Full invoice with bank details and terms" },
  { value: "A5", label: "A5", note: "Half page, for order and alteration slips" },
  { value: "THERMAL_80MM", label: "Thermal 80 mm", note: "Counter receipt printer" },
  { value: "THERMAL_58MM", label: "Thermal 58 mm", note: "Small receipt printer" },
];

type LayoutKey = keyof Omit<PrintSettings, "formats" | "defaultFormat">;

const layout: { key: LayoutKey; title: string; subtitle: string }[] = [
  { key: "showLogo", title: "Shop logo", subtitle: "At the top of every form" },
  { key: "showMeasurements", title: "Measurements", subtitle: "On order slips for the workshop" },
  { key: "showClothPhotos", title: "Cloth photos", subtitle: "Thumbnails of the customer's cloth on order slips" },
  { key: "showBankDetails", title: "Bank details", subtitle: "On A4 invoices" },
  { key: "showUpiQr", title: "UPI QR", subtitle: "So customers can scan and pay" },
  { key: "showTerms", title: "Terms & conditions", subtitle: "At the foot of the invoice" },
  { key: "showSignature", title: "Signature line", subtitle: "Authorised signatory box" },
  { key: "showStaffName", title: "Staff name", subtitle: "Who booked or billed the order" },
];

export default function PrintFormatsPage() {
  const section = useSection("printing");
  const p = section.value;

  if (!p) {
    return (
      <section className="mx-auto max-w-3xl">
        <PageHeader title="Print formats" backHref="/settings/printing/" />
        <LoadState loading={section.loading} error={section.loadError} />
      </section>
    );
  }

  function toggleFormat(format: PaperFormat, on: boolean) {
    const next = on ? [...new Set([...p!.formats, format])] : p!.formats.filter((f) => f !== format);
    const defaultFormat = next.includes(p!.defaultFormat) ? p!.defaultFormat : (next[0] ?? p!.defaultFormat);
    section.update({ formats: next, defaultFormat });
  }

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader title="Print formats" backHref="/settings/printing/" />
      <SectionLabel>Paper sizes offered</SectionLabel>
      <ListCard>
        {formats.map((format) => (
          <ToggleRow
            key={format.value}
            title={format.label}
            subtitle={format.note}
            checked={p.formats.includes(format.value)}
            onChange={(on) => toggleFormat(format.value, on)}
            disabled={isLastFormat(p, format.value)}
          />
        ))}
      </ListCard>
      <div className="mt-4">
        <SelectField
          label="Preselected when printing"
          options={formats.filter((f) => p.formats.includes(f.value)).map(({ value, label }) => ({ value, label }))}
          value={p.defaultFormat}
          onChange={(defaultFormat) => section.update({ defaultFormat })}
          error={section.fieldError("defaultFormatOffered")}
        />
      </div>

      <div id="layout">
        <SectionLabel>Form layout</SectionLabel>
      </div>
      <ListCard>
        {layout.map((row) => (
          <ToggleRow
            key={row.key}
            title={row.title}
            subtitle={row.subtitle}
            checked={p[row.key]}
            onChange={(checked) => section.update({ [row.key]: checked })}
          />
        ))}
      </ListCard>

      <SaveBar saving={section.saving} saved={section.saved} error={section.error} onSave={() => void section.save()} />
    </section>
  );
}

/** The last offered format cannot be switched off. */
function isLastFormat(p: PrintSettings, format: PaperFormat): boolean {
  return p.formats.length === 1 && p.formats[0] === format;
}
