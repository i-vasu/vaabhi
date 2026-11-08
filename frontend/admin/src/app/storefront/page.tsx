"use client";

import Link from "next/link";
import { useState } from "react";
import type { Banner, BannerForm, BannerSlot, Category } from "@vaabhi/api-client";
import { ArrowDownIcon, ArrowUpIcon, ImageIcon, PlusIcon, TrashIcon } from "@/components/icons";
import { ErrorNote } from "@/components/tailoring";
import {
  EmptyState,
  ListCard,
  LoadState,
  PageHeader,
  SectionLabel,
  Sheet,
  TextAreaField,
  TextField,
  ToggleRow,
  buttonPrimary,
  buttonSecondary,
} from "@/components/ui";
import { api } from "@/lib/api";
import { mediaUrl } from "@/lib/photo-upload";
import { describeError } from "@/lib/settings";
import { SLOTS, bannerStatus, emptyBannerForm, formOf, moved, uploadSiteImage } from "@/lib/storefront";
import { useAction, useLoad } from "@/lib/tailoring";

const today = () => new Date().toLocaleDateString("en-CA");

/** What the storefront home page shows: hero slides, occasion tiles, the craft photo and category photos. */
export default function StorefrontPage() {
  const banners = useLoad(() => api.admin.storefront.listBanners(), []);
  const categories = useLoad(() => api.admin.storefront.listCategories(), []);
  const [editing, setEditing] = useState<{ id: number | null; form: BannerForm } | null>(null);
  const action = useAction();

  const inSlot = (slot: BannerSlot) => (banners.value ?? []).filter((b) => b.slot === slot);

  const reorder = async (slot: BannerSlot, id: number, step: -1 | 1) => {
    const ids = inSlot(slot).map((b) => b.id);
    const next = moved(ids, id, step);
    if (next !== ids && (await action.run(() => api.admin.storefront.reorderBanners(slot, next)))) banners.reload();
  };

  return (
    <section className="mx-auto max-w-4xl">
      <PageHeader
        title="Storefront"
        actions={
          <Link href="/storefront/pages/" className={buttonSecondary}>
            Website pages
          </Link>
        }
      />
      <p className="mb-6 text-ink-soft">
        What vaabhi.com shows on its home page. Changes appear on the website within a minute.
      </p>
      <ErrorNote>{banners.error ?? categories.error ?? action.error}</ErrorNote>
      {!banners.value ? (
        <LoadState loading={banners.loading} error={null} />
      ) : (
        SLOTS.map(({ slot, title, hint }) => (
          <div key={slot} className="mb-8">
            <SectionLabel
              action={
                <button
                  type="button"
                  className={buttonSecondary}
                  onClick={() => setEditing({ id: null, form: emptyBannerForm(slot) })}
                >
                  <PlusIcon size={18} /> Add
                </button>
              }
            >
              {title}
            </SectionLabel>
            <p className="mb-3 text-sm text-ink-faint">{hint}</p>
            {inSlot(slot).length === 0 ? (
              <p className="rounded-2xl border border-dashed border-line px-4 py-6 text-center text-ink-soft">
                Nothing here yet, so the website leaves this out.
              </p>
            ) : (
              <ListCard>
                {inSlot(slot).map((banner, index, all) => (
                  <BannerRow
                    key={banner.id}
                    banner={banner}
                    first={index === 0}
                    last={index === all.length - 1}
                    busy={action.busy}
                    onEdit={() => setEditing({ id: banner.id, form: formOf(banner) })}
                    onMove={(step) => reorder(slot, banner.id, step)}
                    onRemove={async () => {
                      if (!confirm(`Remove “${banner.title}” from the website?`)) return;
                      if (await action.run(() => api.admin.storefront.removeBanner(banner.id).then(() => true)))
                        banners.reload();
                    }}
                  />
                ))}
              </ListCard>
            )}
          </div>
        ))
      )}

      <SectionLabel>Category photos</SectionLabel>
      <p className="mb-3 text-sm text-ink-faint">The round photos under “Shop by category”. Square photos work best.</p>
      {!categories.value ? (
        <LoadState loading={categories.loading} error={null} />
      ) : categories.value.length === 0 ? (
        <EmptyState icon={ImageIcon} title="No categories yet" />
      ) : (
        <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
          {categories.value.map((category) => (
            <CategoryPhoto key={category.id} category={category} onSaved={categories.reload} />
          ))}
        </ul>
      )}

      {editing && (
        <BannerSheet
          id={editing.id}
          initial={editing.form}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            banners.reload();
          }}
        />
      )}
    </section>
  );
}

function BannerRow({
  banner,
  first,
  last,
  busy,
  onEdit,
  onMove,
  onRemove,
}: {
  banner: Banner;
  first: boolean;
  last: boolean;
  busy: boolean;
  onEdit: () => void;
  onMove: (step: -1 | 1) => void;
  onRemove: () => void;
}) {
  return (
    <div className="flex items-center gap-3 px-4 py-3">
      <button type="button" onClick={onEdit} className="flex min-w-0 flex-1 items-center gap-3 text-left">
        {/* eslint-disable-next-line @next/next/no-img-element -- static export, R2 serves the resized file */}
        <img
          src={mediaUrl(banner.imageKey, 400)}
          alt=""
          className="h-14 w-20 shrink-0 rounded-lg border border-line object-cover"
        />
        <span className="min-w-0">
          <span className="block truncate font-medium">{banner.title}</span>
          <span className={banner.live ? "block text-sm text-ok" : "block text-sm text-ink-faint"}>
            {bannerStatus(banner, today())}
            {banner.link ? ` · ${banner.link}` : ""}
          </span>
        </span>
      </button>
      <button
        type="button"
        aria-label={`Move ${banner.title} up`}
        disabled={busy || first}
        onClick={() => onMove(-1)}
        className="text-ink-soft hover:text-ink disabled:opacity-30"
      >
        <ArrowUpIcon size={18} />
      </button>
      <button
        type="button"
        aria-label={`Move ${banner.title} down`}
        disabled={busy || last}
        onClick={() => onMove(1)}
        className="text-ink-soft hover:text-ink disabled:opacity-30"
      >
        <ArrowDownIcon size={18} />
      </button>
      <button
        type="button"
        aria-label={`Remove ${banner.title}`}
        disabled={busy}
        onClick={onRemove}
        className="text-ink-faint hover:text-bad"
      >
        <TrashIcon size={18} />
      </button>
    </div>
  );
}

function PhotoPicker({
  imageKey,
  onUploaded,
  onError,
}: {
  imageKey: string;
  onUploaded: (key: string) => void;
  onError: (message: string | null) => void;
}) {
  const [uploading, setUploading] = useState(false);
  return (
    <div className="flex items-center gap-3">
      {imageKey && (
        // eslint-disable-next-line @next/next/no-img-element -- static export, R2 serves the resized file
        <img src={mediaUrl(imageKey, 400)} alt="" className="h-20 w-28 rounded-lg border border-line object-cover" />
      )}
      <label className={buttonSecondary}>
        {uploading ? "Uploading…" : imageKey ? "Change photo" : "Choose photo"}
        <input
          type="file"
          accept="image/*"
          className="sr-only"
          aria-label="Photo"
          disabled={uploading}
          onChange={async (event) => {
            const file = event.target.files?.[0];
            if (!file) return;
            setUploading(true);
            onError(null);
            try {
              onUploaded(await uploadSiteImage(file));
            } catch (e) {
              onError(describeError(e).message);
            } finally {
              setUploading(false);
              event.target.value = "";
            }
          }}
        />
      </label>
    </div>
  );
}

function BannerSheet({
  id,
  initial,
  onClose,
  onSaved,
}: {
  id: number | null;
  initial: BannerForm;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [form, setForm] = useState(initial);
  const action = useAction();
  const set = (patch: Partial<BannerForm>) => setForm((f) => ({ ...f, ...patch }));
  const text = (value: string) => (value.trim() ? value : null);
  const slotTitle = SLOTS.find((s) => s.slot === form.slot)?.title ?? "Banner";

  return (
    <Sheet open title={id ? `Edit · ${slotTitle}` : `Add · ${slotTitle}`} onClose={onClose}>
      <div className="flex flex-col gap-4">
        <PhotoPicker imageKey={form.imageKey} onUploaded={(imageKey) => set({ imageKey })} onError={action.setError} />
        <TextField
          label="Photo description"
          hint="Read aloud to people who can't see the photo"
          maxLength={200}
          value={form.altText ?? ""}
          onChange={(e) => set({ altText: text(e.target.value) })}
        />
        {form.slot !== "OCCASION" && (
          <TextField
            label="Small line above the title"
            placeholder="THE WEDDING EDIT · 2026"
            maxLength={60}
            value={form.eyebrow ?? ""}
            onChange={(e) => set({ eyebrow: text(e.target.value) })}
          />
        )}
        <TextField
          label="Title"
          placeholder={form.slot === "OCCASION" ? "Wedding" : "Heirloom silks for the season"}
          maxLength={120}
          value={form.title}
          onChange={(e) => set({ title: e.target.value })}
        />
        {form.slot !== "OCCASION" && (
          <TextAreaField
            label="Text"
            maxLength={240}
            value={form.subtitle ?? ""}
            onChange={(e) => set({ subtitle: text(e.target.value) })}
          />
        )}
        <TextField
          label="Opens"
          hint="A page on the website: /c/sarees, or /search?occasion=wedding for one occasion across the shop"
          placeholder="/c/sarees"
          maxLength={200}
          value={form.link ?? ""}
          onChange={(e) => set({ link: text(e.target.value) })}
        />
        <TextField
          label="Button text"
          placeholder={form.slot === "OCCASION" ? "Shop now" : "Explore the edit"}
          maxLength={40}
          value={form.ctaLabel ?? ""}
          onChange={(e) => set({ ctaLabel: text(e.target.value) })}
        />
        <div className="grid grid-cols-2 gap-3">
          <TextField
            label="Show from"
            type="date"
            value={form.startsOn ?? ""}
            onChange={(e) => set({ startsOn: e.target.value || null })}
          />
          <TextField
            label="Show until"
            type="date"
            value={form.endsOn ?? ""}
            onChange={(e) => set({ endsOn: e.target.value || null })}
          />
        </div>
        <ListCard>
          <ToggleRow
            title="Show on the website"
            subtitle="Off keeps it here without showing it"
            checked={form.active}
            onChange={(active) => set({ active })}
          />
        </ListCard>
        <ErrorNote>{action.error}</ErrorNote>
        <button
          type="button"
          className={buttonPrimary}
          disabled={action.busy}
          onClick={async () => {
            if (!form.imageKey) return action.setError("Choose the photo first.");
            if (!form.title.trim()) return action.setError("Give it a title.");
            const body = { ...form, title: form.title.trim() };
            const saved = await action.run(() =>
              id ? api.admin.storefront.updateBanner(id, body) : api.admin.storefront.createBanner(body),
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

function CategoryPhoto({ category, onSaved }: { category: Category; onSaved: () => void }) {
  const action = useAction();
  return (
    <li className="flex flex-col items-center gap-2 rounded-2xl border border-line bg-panel p-3 text-center">
      {category.imageKey ? (
        // eslint-disable-next-line @next/next/no-img-element -- static export, R2 serves the resized file
        <img src={mediaUrl(category.imageKey, 400)} alt="" className="size-20 rounded-full object-cover" />
      ) : (
        <span className="grid size-20 place-items-center rounded-full border border-dashed border-line text-ink-faint">
          <ImageIcon />
        </span>
      )}
      <span className="font-medium">{category.name}</span>
      <PhotoPicker
        imageKey=""
        onError={action.setError}
        onUploaded={async (key) => {
          if (await action.run(() => api.admin.storefront.setCategoryImage(category.id, key))) onSaved();
        }}
      />
      {category.imageKey && (
        <button
          type="button"
          className="text-sm text-ink-faint hover:text-bad"
          disabled={action.busy}
          onClick={async () => {
            if (await action.run(() => api.admin.storefront.setCategoryImage(category.id, null))) onSaved();
          }}
        >
          Remove photo
        </button>
      )}
      <ErrorNote>{action.error}</ErrorNote>
    </li>
  );
}
