"use client";

import { useState } from "react";
import { UploadIcon } from "@/components/icons";
import { LoadState, PageHeader, SaveBar, SectionLabel, TextAreaField, TextField } from "@/components/ui";
import { mediaUrl } from "@/lib/photo-upload";
import { describeError, orNull, uploadSettingsImage, useSection } from "@/lib/settings";

const UNDERTAKING_EXAMPLE = "e.g. I understand that alterations are best-effort and the final fit is not guaranteed.";

export default function DocumentsPage() {
  const section = useSection("documents");
  const d = section.value;
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);

  if (!d) {
    return (
      <section className="mx-auto max-w-3xl">
        <PageHeader title="Bank & documents" backHref="/settings/billing/" />
        <LoadState loading={section.loading} error={section.loadError} />
      </section>
    );
  }

  async function chooseQr(file: File | undefined) {
    if (!file) return;
    setUploading(true);
    setUploadError(null);
    try {
      section.update({ upiQrKey: await uploadSettingsImage("UPI_QR", file) });
    } catch (e) {
      setUploadError(describeError(e).message);
    } finally {
      setUploading(false);
    }
  }

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader title="Bank & documents" backHref="/settings/billing/" />

      <SectionLabel>Shop slogan</SectionLabel>
      <TextField
        label="Slogan"
        hint="A short tagline printed under your shop name on the invoice."
        placeholder="e.g. Tailoring with a perfect fit since 1998"
        maxLength={120}
        value={d.slogan ?? ""}
        onChange={(event) => section.update({ slogan: orNull(event.target.value) })}
      />

      <SectionLabel>Bank details</SectionLabel>
      <p className="mb-3 text-sm text-ink-faint">
        Printed at the foot of an A4 invoice, so customers can pay you by transfer. Leave blank to print no bank block.
      </p>
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField
          label="Bank name"
          placeholder="e.g. State Bank of India"
          value={d.bankName ?? ""}
          onChange={(event) => section.update({ bankName: orNull(event.target.value) })}
        />
        <TextField
          label="Branch"
          placeholder="e.g. Benz Circle"
          value={d.branch ?? ""}
          onChange={(event) => section.update({ branch: orNull(event.target.value) })}
        />
        <TextField
          label="Account number"
          inputMode="numeric"
          value={d.accountNumber ?? ""}
          onChange={(event) => section.update({ accountNumber: orNull(event.target.value) })}
          error={section.fieldError("accountNumber")}
        />
        <TextField
          label="IFSC code"
          placeholder="e.g. SBIN0001234"
          maxLength={11}
          value={d.ifsc ?? ""}
          onChange={(event) => section.update({ ifsc: orNull(event.target.value.toUpperCase()) })}
          error={section.fieldError("ifsc")}
        />
      </div>

      <SectionLabel>UPI payment</SectionLabel>
      <p className="mb-3 text-sm text-ink-faint">
        Shown at the till when the payment mode is UPI, and printed on the bill.
      </p>
      <div className="grid items-start gap-4 sm:grid-cols-2">
        <TextField
          label="UPI ID"
          placeholder="shop@okbank"
          value={d.upiId ?? ""}
          onChange={(event) => section.update({ upiId: orNull(event.target.value) })}
          error={section.fieldError("upiId")}
        />
        <div>
          <p className="mb-1.5 text-sm font-medium text-ink-soft">QR image (optional)</p>
          <div className="flex items-center gap-3">
            {d.upiQrKey && (
              // eslint-disable-next-line @next/next/no-img-element -- static export, images are served by R2
              <img src={mediaUrl(d.upiQrKey)} alt="UPI QR" className="size-16 rounded-lg bg-white object-contain" />
            )}
            <label className="inline-flex cursor-pointer items-center gap-2 rounded-xl border border-line-strong px-4 py-3 text-accent-ink hover:bg-panel-2">
              <UploadIcon size={18} /> {uploading ? "Uploading…" : d.upiQrKey ? "Replace QR" : "Upload QR image"}
              <input
                type="file"
                accept="image/*"
                className="sr-only"
                disabled={uploading}
                onChange={(event) => void chooseQr(event.target.files?.[0])}
              />
            </label>
            {d.upiQrKey && (
              <button type="button" className="text-sm text-bad" onClick={() => section.update({ upiQrKey: null })}>
                Remove
              </button>
            )}
          </div>
          {uploadError && <p className="mt-1 text-sm text-bad">{uploadError}</p>}
        </div>
      </div>

      <SectionLabel>Alteration undertaking</SectionLabel>
      <TextAreaField
        label="Wording on the alteration slip"
        hint="Printed when a booking is marked as undertaken. Leave blank to use the built-in wording."
        placeholder={UNDERTAKING_EXAMPLE}
        maxLength={1000}
        value={d.alterationUndertaking ?? ""}
        onChange={(event) => section.update({ alterationUndertaking: orNull(event.target.value) })}
      />

      <div id="terms">
        <SectionLabel>Terms & conditions</SectionLabel>
        <TextAreaField
          label="Printed at the foot of the invoice"
          rows={5}
          maxLength={2000}
          value={d.terms ?? ""}
          onChange={(event) => section.update({ terms: orNull(event.target.value) })}
        />
      </div>

      <SaveBar saving={section.saving} saved={section.saved} error={section.error} onSave={() => void section.save()} />
    </section>
  );
}
