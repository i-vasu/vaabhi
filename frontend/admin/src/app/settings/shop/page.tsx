"use client";

import { useState } from "react";
import type { BusinessType, ShopProfile } from "@vaabhi/api-client";
import { initials } from "@/components/app-shell";
import { UploadIcon } from "@/components/icons";
import { LoadState, PageHeader, SaveBar, SectionLabel, SelectField, TextAreaField, TextField } from "@/components/ui";
import { mediaUrl } from "@/lib/photo-upload";
import { describeError, orNull, uploadSettingsImage, useSection } from "@/lib/settings";

const businessTypes: { value: BusinessType; label: string }[] = [
  { value: "LADIES_BOUTIQUE", label: "Ladies boutique" },
  { value: "GENTS_TAILORING", label: "Gents tailoring" },
  { value: "UNISEX_TAILORING", label: "Unisex tailoring" },
  { value: "KIDS_WEAR", label: "Kids wear" },
  { value: "DESIGNER_STUDIO", label: "Designer studio" },
];

/** "09:00:00" from the API → "09:00" for a time input, and back. */
const hhmm = (time: string) => time.slice(0, 5);

type TextKey = {
  [K in keyof ShopProfile]: ShopProfile[K] extends string | null | undefined ? K : never;
}[keyof ShopProfile];

export default function ShopProfilePage() {
  const section = useSection("shop-profile");
  const { value: profile, update, fieldError } = section;
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);

  if (!profile) {
    return (
      <section className="mx-auto max-w-3xl">
        <PageHeader title="Shop profile" backHref="/settings/" />
        <LoadState loading={section.loading} error={section.loadError} />
      </section>
    );
  }

  const text = (key: TextKey, label: string, extra: Partial<Parameters<typeof TextField>[0]> = {}) => (
    <TextField
      label={label}
      value={(profile[key] as string | null | undefined) ?? ""}
      onChange={(event) => update({ [key]: extra.required ? event.target.value : orNull(event.target.value) })}
      error={fieldError(key)}
      {...extra}
    />
  );

  async function chooseLogo(file: File | undefined) {
    if (!file) return;
    setUploading(true);
    setUploadError(null);
    try {
      update({ logoKey: await uploadSettingsImage("LOGO", file) });
    } catch (e) {
      setUploadError(describeError(e).message);
    } finally {
      setUploading(false);
    }
  }

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader title="Shop profile" backHref="/settings/" />

      <SectionLabel>Business profile</SectionLabel>
      <div className="rounded-2xl border border-line bg-panel p-5">
        <div className="mb-6 flex items-center gap-4 border-b border-line pb-6">
          {profile.logoKey ? (
            // eslint-disable-next-line @next/next/no-img-element -- static export, images are served by R2
            <img src={mediaUrl(profile.logoKey)} alt="Shop logo" className="size-20 rounded-full object-cover" />
          ) : (
            <span className="grid size-20 place-items-center rounded-full bg-accent text-2xl font-bold text-white">
              {initials(profile.shopName)}
            </span>
          )}
          <div>
            <p className="text-lg font-semibold">{profile.shopName}</p>
            <label className="mt-1 inline-flex cursor-pointer items-center gap-2 text-sm text-accent-ink">
              <UploadIcon size={16} /> {uploading ? "Uploading…" : "Change logo"}
              <input
                type="file"
                accept="image/*"
                className="sr-only"
                disabled={uploading}
                onChange={(event) => void chooseLogo(event.target.files?.[0])}
              />
            </label>
            {uploadError && <p className="text-sm text-bad">{uploadError}</p>}
          </div>
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          {text("ownerName", "Owner name", { required: true })}
          {text("shopName", "Shop name", { required: true })}
          <SelectField
            label="Business type"
            hint="Filters garment templates on a new order."
            options={businessTypes}
            value={profile.businessType}
            onChange={(businessType) => update({ businessType })}
          />
          {text("phone", "Phone number", {
            type: "tel",
            hint: "Also your WhatsApp: the website's chat button and Contact page use it.",
          })}
          {text("publicEmail", "Public email", { type: "email", placeholder: "shop@example.com" })}
          {text("googleReviewLink", "Google review link", {
            placeholder: "https://g.page/r/…",
            hint: "Where the share link and every review request send your customer.",
          })}
          {text("website", "Website", { placeholder: "https://yourshop.in" })}
          {text("mapsLink", "Google Maps link", { placeholder: "https://maps.app.goo.gl/…" })}
          {text("instagram", "Instagram", { placeholder: "https://instagram.com/…" })}
          {text("facebook", "Facebook", { placeholder: "https://facebook.com/…" })}
          {text("youtube", "YouTube", { placeholder: "https://youtube.com/…" })}
          {text("threads", "Threads", { placeholder: "https://threads.net/…" })}
          {text("whatsappChannel", "WhatsApp channel link", {
            placeholder: "https://whatsapp.com/channel/…",
            hint: "The website's “Join on WhatsApp” button opens it. A community invite (chat.whatsapp.com/…) works too. Empty: customers message your phone instead.",
          })}
        </div>

        <SectionLabel>Address</SectionLabel>
        <TextAreaField
          label="Address"
          value={profile.address ?? ""}
          onChange={(event) => update({ address: orNull(event.target.value) })}
          error={fieldError("address")}
        />
        <div className="mt-4 grid grid-cols-2 gap-4">
          {text("doorNo", "Door no.")}
          {text("street", "Street name")}
          {text("area", "Area")}
          {text("city", "City")}
          {text("district", "District")}
          {text("state", "State")}
          {text("country", "Country code", { required: true, maxLength: 2, hint: "IN for India (GST)" })}
          {text("pinCode", "PIN code", { inputMode: "numeric", maxLength: 6 })}
        </div>
      </div>

      <SectionLabel>Currency & shop hours</SectionLabel>
      <div className="grid gap-4 rounded-2xl border border-line bg-panel p-5 sm:grid-cols-3">
        {text("currency", "Currency", { required: true, maxLength: 3 })}
        <TextField
          label="Opens at"
          type="time"
          value={hhmm(profile.opensAt)}
          onChange={(event) => update({ opensAt: event.target.value })}
          error={fieldError("opensAt")}
        />
        <TextField
          label="Closes at"
          type="time"
          value={hhmm(profile.closesAt)}
          onChange={(event) => update({ closesAt: event.target.value })}
          error={fieldError("closesAt")}
        />
        <p className="text-sm text-ink-faint sm:col-span-3">
          Alerts are held outside shop hours and delivered when you next open.
        </p>
      </div>

      <SaveBar saving={section.saving} saved={section.saved} error={section.error} onSave={() => void section.save()} />
    </section>
  );
}
