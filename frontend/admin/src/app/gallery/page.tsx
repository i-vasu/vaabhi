"use client";

import { useState } from "react";
import { ApiError, type GalleryPhoto } from "@vaabhi/api-client";
import { ImageIcon, PlusIcon, TrashIcon } from "@/components/icons";
import { ErrorNote, SearchBox } from "@/components/tailoring";
import {
  ChipGroup,
  EmptyState,
  LoadState,
  PageHeader,
  SelectField,
  Sheet,
  TextField,
  buttonPrimary,
  buttonSecondary,
} from "@/components/ui";
import { api } from "@/lib/api";
import { resizeToWebp } from "@/lib/image-resize";
import { describeError } from "@/lib/settings";
import { useAction, useLoad } from "@/lib/tailoring";

type Kind = GalleryPhoto["kind"];

/** Photo URL for a gallery key (800 px WebP on the media domain). */
function galleryUrl(key: string): string {
  const base = process.env.NEXT_PUBLIC_MEDIA_URL || "https://media.vaabhi.com";
  return `${base.replace(/\/$/, "")}/${key}/800.webp`;
}

/** Resizes to an 800 px WebP and uploads it; returns the key. */
async function uploadGalleryPhoto(file: Blob, resize = resizeToWebp): Promise<string> {
  const [rendition] = await resize(file, [800]);
  const upload = await api.admin.gallery.startUpload();
  const response = await fetch(upload.uploadUrl, {
    method: "PUT",
    body: rendition!.blob,
    headers: { "Content-Type": "image/webp", "Cache-Control": "public, max-age=31536000, immutable" },
  });
  if (!response.ok) throw new ApiError(response.status, "Uploading the photo failed");
  return upload.key;
}

/** Design ideas to show customers and photos of finished garments. */
export default function GalleryPage() {
  const [kind, setKind] = useState<Kind>("DESIGN");
  const [q, setQ] = useState("");
  const photos = useLoad(() => api.admin.gallery.list({ kind, q: q || undefined }), [kind, q]);
  const templates = useLoad(() => api.admin.settings.garmentTemplates(), []);
  const [adding, setAdding] = useState(false);
  const action = useAction();

  return (
    <section className="mx-auto max-w-5xl">
      <PageHeader
        title="Gallery"
        actions={
          <button type="button" className={buttonPrimary} onClick={() => setAdding(true)}>
            <PlusIcon size={18} /> Add photo
          </button>
        }
      />
      <div className="mb-4 flex flex-col gap-3">
        <ChipGroup
          label="Gallery"
          options={[
            { value: "DESIGN", label: "Designs" },
            { value: "CUSTOMER", label: "Customer garments" },
          ]}
          value={kind}
          onChange={setKind}
        />
        <SearchBox label="Search gallery" placeholder="Title or tag, e.g. boat neck" onSearch={setQ} />
      </div>
      <ErrorNote>{photos.error ?? action.error}</ErrorNote>
      {!photos.value ? (
        <LoadState loading={photos.loading} error={null} />
      ) : photos.value.length === 0 ? (
        <EmptyState icon={ImageIcon} title="No photos here yet" />
      ) : (
        <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
          {photos.value.map((p) => (
            <li key={p.id} className="overflow-hidden rounded-2xl border border-line bg-panel">
              {/* eslint-disable-next-line @next/next/no-img-element -- static export, R2 serves the resized file */}
              <img src={galleryUrl(p.photoKey)} alt={p.title} className="aspect-[3/4] w-full object-cover" />
              <div className="flex items-start gap-2 p-3">
                <span className="min-w-0 flex-1">
                  <span className="block truncate font-medium">{p.title}</span>
                  <span className="block truncate text-xs text-ink-soft">
                    {[p.garment, p.clientName, p.tags].filter(Boolean).join(" · ")}
                  </span>
                </span>
                <button
                  type="button"
                  aria-label={`Remove ${p.title}`}
                  className="text-ink-faint hover:text-bad"
                  disabled={action.busy}
                  onClick={async () => {
                    if (await action.run(() => api.admin.gallery.remove(p.id).then(() => true))) photos.reload();
                  }}
                >
                  <TrashIcon size={18} />
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
      {adding && (
        <AddPhotoSheet
          kind={kind}
          templates={(templates.value ?? []).map((t) => ({ value: String(t.id), label: t.name }))}
          onClose={() => setAdding(false)}
          onSaved={() => {
            setAdding(false);
            photos.reload();
          }}
        />
      )}
    </section>
  );
}

function AddPhotoSheet({
  kind: initialKind,
  templates,
  onClose,
  onSaved,
}: {
  kind: Kind;
  templates: { value: string; label: string }[];
  onClose: () => void;
  onSaved: () => void;
}) {
  const [kind, setKind] = useState<Kind>(initialKind);
  const [title, setTitle] = useState("");
  const [tags, setTags] = useState("");
  const [templateId, setTemplateId] = useState("");
  const [key, setKey] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const action = useAction();

  return (
    <Sheet open title="Add to gallery" onClose={onClose}>
      <div className="flex flex-col gap-4">
        <SelectField
          label="Gallery"
          options={[
            { value: "DESIGN", label: "Designs" },
            { value: "CUSTOMER", label: "Customer garments" },
          ]}
          value={kind}
          onChange={setKind}
        />
        <label className={buttonSecondary}>
          {uploading ? "Uploading…" : key ? "Photo uploaded — change" : "Choose photo"}
          <input
            type="file"
            accept="image/*"
            className="sr-only"
            aria-label="Photo"
            onChange={async (event) => {
              const file = event.target.files?.[0];
              if (!file) return;
              setUploading(true);
              action.setError(null);
              try {
                setKey(await uploadGalleryPhoto(file));
              } catch (e) {
                action.setError(describeError(e).message);
              } finally {
                setUploading(false);
              }
            }}
          />
        </label>
        <TextField label="Title" maxLength={120} value={title} onChange={(e) => setTitle(e.target.value)} />
        <TextField
          label="Tags"
          maxLength={200}
          placeholder="boat neck, silk"
          value={tags}
          onChange={(e) => setTags(e.target.value)}
        />
        <SelectField
          label="Garment"
          options={[{ value: "", label: "Any" }, ...templates]}
          value={templateId}
          onChange={setTemplateId}
        />
        <ErrorNote>{action.error}</ErrorNote>
        <button
          type="button"
          className={buttonPrimary}
          disabled={action.busy || uploading}
          onClick={async () => {
            if (!key) return action.setError("Choose the photo first.");
            if (!title.trim()) return action.setError("Give it a title.");
            const saved = await action.run(() =>
              api.admin.gallery.add({
                kind,
                title: title.trim(),
                tags: tags.trim() || null,
                templateId: templateId ? Number(templateId) : null,
                clientId: null,
                orderId: null,
                photoKey: key,
              }),
            );
            if (saved) onSaved();
          }}
        >
          {action.busy ? "Saving…" : "Save photo"}
        </button>
      </div>
    </Sheet>
  );
}
