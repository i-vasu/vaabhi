"use client";

import { useState } from "react";
import { ApiError, type AdminMedia, type MediaUpdate } from "@vaabhi/api-client";
import { Button, cn } from "@vaabhi/ui";
import { api } from "@/lib/api";
import { mediaUrl, uploadPhoto } from "@/lib/photo-upload";

type Upload = { id: string; name: string; state: "working" | "failed"; message?: string };

const ACCEPT = "image/jpeg,image/png,image/webp";
const HEIC_MESSAGE =
  "This is an iPhone HEIC photo, which this browser cannot open. Upload it from the iPhone itself (it converts to JPEG), or export it as JPEG first.";

/** iPhone photos; only Safari can decode them, so explain instead of showing a decoder error. */
function isHeic(file: File): boolean {
  return /^image\/hei[cf]/.test(file.type) || /\.hei[cf]$/i.test(file.name);
}
const MAX_INPUT_BYTES = 30 * 1024 * 1024;
/** Same limit as the API (ProductMediaService.MAX_PHOTOS). */
export const MAX_PHOTOS = 12;

const GUIDELINES = [
  "Formats: JPEG, PNG or WebP. iPhone (HEIC) photos: upload from the iPhone itself, or export as JPEG first.",
  "Size: up to 30 MB per photo, at least 1600 px wide so zoom stays sharp.",
  `Up to ${MAX_PHOTOS} photos per product. The first photo is shown in listings; use ← → to reorder.`,
  "Portrait 3:4 works best (e.g. 1200 × 1600 px), product centred on a plain background.",
  "Pick the colour each photo shows, so shoppers see the right photos when they choose a colour.",
  "Write a short description of each photo (alt text); it helps search engines and screen readers.",
  "Photos are resized in your browser to 400, 800 and 1600 px. Keep this tab open until uploads finish.",
];

function problem(error: unknown): string {
  if (error instanceof ApiError) return error.detail ?? error.message;
  if (error instanceof Error) return error.message;
  return "Something went wrong";
}

let uploadCounter = 0;

/**
 * Photos for a saved product: drop or pick files, they are resized in the browser and uploaded straight to
 * R2. Order, alt text and colour are saved as soon as they change.
 */
export function ProductPhotos({
  productId,
  productName,
  colours,
  media,
  onChange,
}: Readonly<{
  productId?: number;
  productName: string;
  colours: string[];
  media: AdminMedia[];
  onChange: (media: AdminMedia[]) => void;
}>) {
  const [uploads, setUploads] = useState<Upload[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [dragging, setDragging] = useState(false);

  if (productId === undefined) {
    return (
      <section className="rounded-lg border border-neutral-200 p-5" aria-labelledby="photos">
        <h2 id="photos" className="mb-1 font-semibold">
          Photos
        </h2>
        <p className="text-sm text-neutral-500">Save the product first, then add photos.</p>
      </section>
    );
  }

  const defaultColour = colours.length === 1 ? colours[0]! : null;
  const full = media.length >= MAX_PHOTOS;

  async function addFiles(files: File[]) {
    setError(null);
    let slots = MAX_PHOTOS - media.length;
    for (const file of files) {
      const id = `upload-${++uploadCounter}`;
      if (slots <= 0) {
        setUploads((u) => [
          ...u,
          { id, name: file.name, state: "failed", message: `Over the ${MAX_PHOTOS}-photo limit` },
        ]);
        continue;
      }
      if (file.size > MAX_INPUT_BYTES) {
        setUploads((u) => [...u, { id, name: file.name, state: "failed", message: "Larger than 30 MB" }]);
        continue;
      }
      slots--;
      setUploads((u) => [...u, { id, name: file.name, state: "working" }]);
      try {
        const alt = [productName, defaultColour].filter(Boolean).join(", ") || file.name;
        onChange(await uploadPhoto(productId!, file, { alt, colourName: defaultColour }));
        setUploads((u) => u.filter((x) => x.id !== id));
      } catch (e) {
        const message = isHeic(file) ? HEIC_MESSAGE : problem(e);
        setUploads((u) => u.map((x) => (x.id === id ? { ...x, state: "failed", message } : x)));
      }
    }
  }

  async function save(next: AdminMedia[]) {
    onChange(next);
    const updates: MediaUpdate[] = next.map((m) => ({ id: m.id, alt: m.alt, colourName: m.colourName }));
    try {
      onChange(await api.admin.updateMedia(productId!, updates));
    } catch (e) {
      setError(problem(e));
    }
  }

  async function remove(item: AdminMedia) {
    try {
      onChange(await api.admin.deleteMedia(productId!, item.id));
    } catch (e) {
      setError(problem(e));
    }
  }

  const move = (index: number, by: -1 | 1) => {
    const next = [...media];
    const [item] = next.splice(index, 1);
    next.splice(index + by, 0, item!);
    void save(next);
  };
  const edit = (index: number, patch: Partial<AdminMedia>) =>
    media.map((m, i) => (i === index ? { ...m, ...patch } : m));

  return (
    <section className="rounded-lg border border-neutral-200 p-5" aria-labelledby="photos">
      <h2 id="photos" className="mb-1 font-semibold">
        Photos
      </h2>
      <p className="mb-2 text-sm text-neutral-500">
        {media.length} of {MAX_PHOTOS} photos
      </p>
      <div className="mb-4 rounded-md bg-neutral-50 p-4 text-sm">
        <p className="mb-2 font-medium">Before you upload</p>
        <ul aria-label="Photo guidelines" className="list-disc space-y-1 pl-5 text-neutral-600">
          {GUIDELINES.map((line) => (
            <li key={line}>{line}</li>
          ))}
        </ul>
      </div>
      {error && (
        <p role="alert" className="mb-3 text-sm text-red-700">
          {error}
        </p>
      )}

      <label
        onDragOver={(e) => {
          e.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(e) => {
          e.preventDefault();
          setDragging(false);
          if (full) return;
          void addFiles([...e.dataTransfer.files].filter((f) => f.type.startsWith("image/") || isHeic(f)));
        }}
        className={cn(
          "mb-4 flex cursor-pointer flex-col items-center gap-1 rounded-md border-2 border-dashed p-6 text-sm",
          "has-[:focus-visible]:outline-2",
          dragging ? "border-neutral-900 bg-neutral-50" : "border-neutral-300",
          full && "cursor-not-allowed opacity-60",
        )}
      >
        <span className="font-medium">
          {full
            ? `This product has ${MAX_PHOTOS} photos. Delete one to add another.`
            : "Drop photos here or choose files"}
        </span>
        <span className="text-neutral-500">JPEG, PNG or WebP · up to 30 MB each</span>
        <input
          type="file"
          accept={ACCEPT}
          multiple
          disabled={full}
          className="sr-only"
          aria-label="Add photos"
          onChange={(e) => {
            void addFiles([...(e.target.files ?? [])]);
            e.target.value = "";
          }}
        />
      </label>

      {uploads.length > 0 && (
        <ul aria-label="Uploads" className="mb-4 flex flex-col gap-1 text-sm">
          {uploads.map((u) => (
            <li key={u.id} className={u.state === "failed" ? "text-red-700" : "text-neutral-600"}>
              {u.name}: {u.state === "working" ? "uploading…" : `failed. ${u.message ?? ""}`}
            </li>
          ))}
        </ul>
      )}

      {media.length === 0 ? (
        <p className="text-sm text-neutral-500">No photos yet.</p>
      ) : (
        <ol className="grid grid-cols-2 gap-4 md:grid-cols-4">
          {media.map((item, i) => (
            <li key={item.id} className="flex flex-col gap-2 rounded-md border border-neutral-200 p-2">
              {item.status === "READY" ? (
                // eslint-disable-next-line @next/next/no-img-element -- static export; R2 serves sized WebP files
                <img
                  src={mediaUrl(item.key, 400)}
                  alt={item.alt}
                  className="aspect-[3/4] w-full rounded object-cover"
                />
              ) : (
                <div className="flex aspect-[3/4] items-center justify-center rounded bg-amber-50 p-2 text-center text-xs text-amber-900">
                  Upload did not finish. Delete and try again.
                </div>
              )}
              <label className="flex flex-col gap-1 text-xs font-medium">
                <span>Description (alt text)</span>
                <input
                  className="rounded border border-neutral-300 px-2 py-1 text-sm font-normal"
                  value={item.alt}
                  onChange={(e) => onChange(edit(i, { alt: e.target.value }))}
                  onBlur={() => void save(media)}
                />
              </label>
              <label className="flex flex-col gap-1 text-xs font-medium">
                <span>Colour</span>
                <select
                  className="rounded border border-neutral-300 px-2 py-1 text-sm font-normal"
                  value={item.colourName ?? ""}
                  onChange={(e) => void save(edit(i, { colourName: e.target.value || null }))}
                >
                  <option value="">All colours</option>
                  {[...new Set([...colours, ...(item.colourName ? [item.colourName] : [])])].map((c) => (
                    <option key={c} value={c}>
                      {c}
                    </option>
                  ))}
                </select>
              </label>
              <div className="flex items-center justify-between gap-1">
                <span className="flex gap-1">
                  <Button
                    variant="secondary"
                    className="px-2 py-1"
                    disabled={i === 0}
                    aria-label={`Move photo ${i + 1} earlier`}
                    onClick={() => move(i, -1)}
                  >
                    ←
                  </Button>
                  <Button
                    variant="secondary"
                    className="px-2 py-1"
                    disabled={i === media.length - 1}
                    aria-label={`Move photo ${i + 1} later`}
                    onClick={() => move(i, 1)}
                  >
                    →
                  </Button>
                </span>
                <button
                  type="button"
                  className="text-xs text-red-700 hover:underline"
                  aria-label={`Delete photo ${i + 1}`}
                  onClick={() => void remove(item)}
                >
                  Delete
                </button>
              </div>
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}
