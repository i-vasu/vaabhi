"use client";

import { useState } from "react";
import { ApiError, type AdminMedia, type MediaUpdate } from "@vaabhi/api-client";
import { Button, cn } from "@vaabhi/ui";
import { api } from "@/lib/api";
import { MAX_VIDEO_BYTES, mediaUrl, uploadPhoto, uploadVideo, VIDEO_TYPES, videoUrl } from "@/lib/photo-upload";

type Upload = { id: string; name: string; state: "working" | "failed"; message?: string };

/** A photo or video picked before the product was first saved; it uploads when the product is saved. */
export type QueuedMedia = { id: string; kind: "IMAGE" | "VIDEO"; file: File; preview: string };

/**
 * `image/*` and `video/*` (not a list of exact types) so phones open the photo gallery rather than a file
 * browser. Types are checked after picking.
 */
const ACCEPT_PHOTOS = "image/*";
const ACCEPT_VIDEO = "video/*";
const HEIC_MESSAGE =
  "This is an iPhone HEIC photo, which this browser cannot open. Upload it from the iPhone itself (it converts to JPEG), or export it as JPEG first.";

/** iPhone photos; only Safari can decode them, so explain instead of showing a decoder error. */
function isHeic(file: File): boolean {
  return /^image\/hei[cf]/.test(file.type) || /\.hei[cf]$/i.test(file.name);
}
const MAX_INPUT_BYTES = 30 * 1024 * 1024;
/** Same limit as the API (ProductMediaService.MAX_PHOTOS). */
export const MAX_PHOTOS = 12;
/** Same limit as the API (ProductMediaService.MAX_VIDEOS). */
export const MAX_VIDEOS = 3;

const GUIDELINES = [
  "Formats: JPEG, PNG or WebP. iPhone (HEIC) photos: upload from the iPhone itself, or export as JPEG first.",
  "Size: up to 30 MB per photo, at least 1600 px wide so zoom stays sharp.",
  `Up to ${MAX_PHOTOS} photos per product. The main photo is shown in listings; use Set as main to pick it and ← → to order the rest.`,
  "Colour: leave on All colours, or tag a photo with a colour to show it only when shoppers pick that colour.",
  "Portrait 3:4 works best (e.g. 1200 × 1600 px), product centred on a plain background.",
  "Write a short description of each photo (alt text); it helps search engines and screen readers.",
  "Photos are resized in your browser to 400, 800 and 1600 px. Keep this tab open until uploads finish.",
];

const VIDEO_GUIDELINE = `Up to ${MAX_VIDEOS} videos per product: MP4, MOV or WebM, up to 200 MB each. Portrait and under a minute works best; tag a video with a colour to show it only for that colour.`;

function problem(error: unknown): string {
  if (error instanceof ApiError) return error.detail ?? error.message;
  if (error instanceof Error) return error.message;
  return "Something went wrong";
}

/** Why a picked file cannot be used, or null when it can. */
function photoProblem(file: File): string | null {
  return file.size > MAX_INPUT_BYTES ? "Larger than 30 MB" : null;
}

function videoProblem(file: File): string | null {
  if (!(VIDEO_TYPES as readonly string[]).includes(file.type)) return "Use an MP4, MOV or WebM video";
  return file.size > MAX_VIDEO_BYTES ? "Larger than 200 MB" : null;
}

let uploadCounter = 0;
const nextId = (prefix: string) => `${prefix}-${++uploadCounter}`;

/** jsdom and very old browsers have no object URLs; the preview is then just the file name. */
function previewOf(file: File): string {
  return typeof URL.createObjectURL === "function" ? URL.createObjectURL(file) : "";
}

export function releasePreview(item: QueuedMedia) {
  if (item.preview && typeof URL.revokeObjectURL === "function") URL.revokeObjectURL(item.preview);
}

/**
 * The alt text a new photo or video starts with: the product name and its only colour. New media starts on
 * "All colours" (colourName null); tag a photo with a colour to show it only when that colour is picked.
 */
export function defaultDetails(productName: string, colours: string[], fallback: string) {
  const onlyColour = colours.length === 1 ? colours[0]! : null;
  return { alt: [productName, onlyColour].filter(Boolean).join(", ") || fallback, colourName: null };
}

/** Uploads a queued photo or video to a saved product; returns the product's media afterwards. */
export function uploadQueued(productId: number, item: QueuedMedia, productName: string, colours: string[]) {
  const details = defaultDetails(productName, colours, item.file.name);
  return item.kind === "VIDEO"
    ? uploadVideo(productId, item.file, details)
    : uploadPhoto(productId, item.file, details).catch((e: unknown) => {
        throw isHeic(item.file) ? new Error(HEIC_MESSAGE) : e;
      });
}

/** Whether a product has what it needs to publish: at least one photo and a video, uploaded or waiting. */
export function mediaReady(media: AdminMedia[], queued: QueuedMedia[]) {
  const ready = media.filter((m) => m.status === "READY");
  return {
    hasPhoto: ready.some((m) => m.kind === "IMAGE") || queued.some((q) => q.kind === "IMAGE"),
    hasVideo: ready.some((m) => m.kind === "VIDEO") || queued.some((q) => q.kind === "VIDEO"),
  };
}

/**
 * Photos and the video of a product. Picked files are resized in the browser (photos) and uploaded straight
 * to R2; before the product's first save they wait in `queued` and upload when it is saved. Order, alt text
 * and colour are saved as soon as they change. The video can be added once there is a photo.
 */
export function ProductPhotos({
  productId,
  productName,
  colours,
  media,
  onChange,
  queued = [],
  onQueue = () => {},
}: Readonly<{
  productId?: number;
  productName: string;
  colours: string[];
  media: AdminMedia[];
  onChange: (media: AdminMedia[]) => void;
  queued?: QueuedMedia[];
  onQueue?: (queued: QueuedMedia[]) => void;
}>) {
  const [uploads, setUploads] = useState<Upload[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [dragging, setDragging] = useState(false);

  const photos = media.filter((m) => m.kind === "IMAGE");
  const videos = media.filter((m) => m.kind === "VIDEO");
  const queuedPhotos = queued.filter((q) => q.kind === "IMAGE");
  const queuedVideos = queued.filter((q) => q.kind === "VIDEO");
  const photoCount = photos.length + queuedPhotos.length;
  const full = photoCount >= MAX_PHOTOS;
  const { hasPhoto } = mediaReady(media, queued);
  const videoUploading = uploads.some((u) => u.id.startsWith("video") && u.state === "working");
  const videoCount = videos.length + queuedVideos.length;
  const videosFull = videoCount >= MAX_VIDEOS || (videoUploading && videoCount + 1 >= MAX_VIDEOS);
  const saved = productId !== undefined;

  const fail = (id: string, name: string, message: string) =>
    setUploads((u) => [...u.filter((x) => x.id !== id), { id, name, state: "failed", message }]);

  async function run(id: string, name: string, upload: () => Promise<AdminMedia[]>) {
    setUploads((u) => [...u, { id, name, state: "working" }]);
    try {
      onChange(await upload());
      setUploads((u) => u.filter((x) => x.id !== id));
    } catch (e) {
      fail(id, name, problem(e));
    }
  }

  async function addFiles(files: File[]) {
    setError(null);
    let slots = MAX_PHOTOS - photoCount;
    const queue: QueuedMedia[] = [];
    for (const file of files) {
      const id = nextId("upload");
      const reason = slots <= 0 ? `Over the ${MAX_PHOTOS}-photo limit` : photoProblem(file);
      if (reason) {
        fail(id, file.name, reason);
        continue;
      }
      slots--;
      const item: QueuedMedia = { id, kind: "IMAGE", file, preview: saved ? "" : previewOf(file) };
      if (saved) {
        await run(id, file.name, () => uploadQueued(productId, item, productName, colours));
      } else {
        queue.push(item);
      }
    }
    if (queue.length) onQueue([...queued, ...queue]);
  }

  async function addVideo(file: File) {
    setError(null);
    const id = nextId("video");
    const reason = videoProblem(file);
    if (reason) {
      fail(id, file.name, reason);
      return;
    }
    const item: QueuedMedia = { id, kind: "VIDEO", file, preview: saved ? "" : previewOf(file) };
    if (saved) {
      await run(id, file.name, () => uploadQueued(productId, item, productName, colours));
    } else {
      onQueue([...queued, item]);
    }
  }

  function unqueue(item: QueuedMedia) {
    releasePreview(item);
    onQueue(queued.filter((q) => q.id !== item.id));
  }

  /** Saves a new photo order (the video keeps its place at the end), alt text and colour tags. */
  async function save(nextPhotos: AdminMedia[], nextVideos: AdminMedia[] = videos) {
    const next = [...nextPhotos, ...nextVideos];
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
    const next = [...photos];
    const [item] = next.splice(index, 1);
    next.splice(index + by, 0, item!);
    void save(next);
  };
  /** The main photo is the first one: listings, the home page and the bag show it. */
  const makeMain = (index: number) => {
    const next = [...photos];
    const [item] = next.splice(index, 1);
    void save([item!, ...next]);
  };
  const makeQueuedMain = (item: QueuedMedia) => onQueue([item, ...queued.filter((q) => q.id !== item.id)]);
  const edit = (index: number, patch: Partial<AdminMedia>) =>
    photos.map((m, i) => (i === index ? { ...m, ...patch } : m));

  return (
    <section className="rounded-lg border border-line p-5" aria-labelledby="photos">
      <h2 id="photos" className="mb-1 font-semibold">
        Photos &amp; video
      </h2>
      <p className="mb-2 text-sm text-ink-soft">
        {photoCount} of {MAX_PHOTOS} photos ·{" "}
        {videoCount === 0 ? "no video yet" : `${videoCount} of ${MAX_VIDEOS} videos`}
        {!saved && queued.length > 0 && " · uploads when you save"}
      </p>
      <details className="mb-4 rounded-md bg-panel p-4 text-sm">
        <summary className="cursor-pointer font-medium">Before you upload</summary>
        <ul aria-label="Photo guidelines" className="mt-2 list-disc space-y-1 pl-5 text-ink-soft">
          {[...GUIDELINES, VIDEO_GUIDELINE].map((line) => (
            <li key={line}>{line}</li>
          ))}
        </ul>
      </details>
      {error && (
        <p role="alert" className="mb-3 text-sm text-bad">
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
          "mb-4 flex cursor-pointer flex-col items-center gap-1 rounded-md border-2 border-dashed p-6 text-center text-sm",
          "has-[:focus-visible]:outline-2",
          dragging ? "border-accent bg-panel" : "border-line-strong",
          full && "cursor-not-allowed opacity-60",
        )}
      >
        <span className="font-medium">
          {full ? `This product has ${MAX_PHOTOS} photos. Delete one to add another.` : "Add photos"}
        </span>
        {!full && <span className="text-ink-soft">Tap to choose from your gallery, or drop files here</span>}
        <input
          type="file"
          accept={ACCEPT_PHOTOS}
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
            <li key={u.id} className={u.state === "failed" ? "text-bad" : "text-ink-soft"}>
              {u.name}: {u.state === "working" ? "uploading…" : `failed. ${u.message ?? ""}`}
            </li>
          ))}
        </ul>
      )}

      {photoCount === 0 ? (
        <p className="text-sm text-ink-soft">No photos yet.</p>
      ) : (
        <ol className="grid grid-cols-2 gap-4 md:grid-cols-4">
          {photos.map((item, i) => (
            <li key={item.id} className="flex flex-col gap-2 rounded-md border border-line p-2">
              {item.status === "READY" ? (
                // eslint-disable-next-line @next/next/no-img-element -- static export; R2 serves sized WebP files
                <img
                  src={mediaUrl(item.key, 400)}
                  alt={item.alt}
                  className="aspect-[3/4] w-full rounded object-cover"
                />
              ) : (
                <div className="flex aspect-[3/4] items-center justify-center rounded bg-warn-soft p-2 text-center text-xs text-warn">
                  Upload did not finish. Delete and try again.
                </div>
              )}
              <label className="flex flex-col gap-1 text-xs font-medium">
                <span>Description (alt text)</span>
                <input
                  className="rounded border border-line-strong px-2 py-1 text-sm font-normal"
                  value={item.alt}
                  onChange={(e) => onChange([...edit(i, { alt: e.target.value }), ...videos])}
                  onBlur={() => void save(photos)}
                />
              </label>
              <label className="flex flex-col gap-1 text-xs font-medium">
                <span>Colour</span>
                <select
                  className="rounded border border-line-strong px-2 py-1 text-sm font-normal"
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
              <MainChoice main={i === 0} label={`Set photo ${i + 1} as main`} onPick={() => makeMain(i)} />
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
                    disabled={i === photos.length - 1}
                    aria-label={`Move photo ${i + 1} later`}
                    onClick={() => move(i, 1)}
                  >
                    →
                  </Button>
                </span>
                <button
                  type="button"
                  className="text-xs text-bad hover:underline"
                  aria-label={`Delete photo ${i + 1}`}
                  onClick={() => void remove(item)}
                >
                  Delete
                </button>
              </div>
            </li>
          ))}
          {queuedPhotos.map((item) => (
            <li key={item.id} className="flex flex-col gap-2 rounded-md border border-dashed border-line-strong p-2">
              {item.preview ? (
                // eslint-disable-next-line @next/next/no-img-element -- a local preview of the picked file
                <img src={item.preview} alt={item.file.name} className="aspect-[3/4] w-full rounded object-cover" />
              ) : (
                <div className="flex aspect-[3/4] items-center justify-center rounded bg-panel p-2 text-center text-xs">
                  {item.file.name}
                </div>
              )}
              <MainChoice
                main={photos.length === 0 && queuedPhotos[0]?.id === item.id}
                label={`Set ${item.file.name} as main`}
                onPick={() => (photos.length === 0 ? makeQueuedMain(item) : undefined)}
                disabled={photos.length > 0}
              />
              <span className="text-xs text-ink-soft">Uploads when you save</span>
              <button
                type="button"
                className="self-end text-xs text-bad hover:underline"
                aria-label={`Remove ${item.file.name}`}
                onClick={() => unqueue(item)}
              >
                Remove
              </button>
            </li>
          ))}
        </ol>
      )}

      <div className="mt-5 border-t border-line pt-4">
        <h3 className="mb-2 text-sm font-semibold">Videos</h3>
        <ul aria-label="Videos" className="mb-3 flex flex-wrap gap-4">
          {videos.map((item, i) => (
            <li key={item.id} className="flex w-40 flex-col gap-2">
              {item.status === "READY" ? (
                <video
                  src={videoUrl(item.key)}
                  controls
                  playsInline
                  preload="metadata"
                  aria-label={item.alt}
                  className="aspect-[3/4] w-40 rounded bg-panel object-cover"
                />
              ) : (
                <div className="flex aspect-[3/4] w-40 items-center justify-center rounded bg-warn-soft p-2 text-center text-xs text-warn">
                  Upload did not finish. Delete and try again.
                </div>
              )}
              <label className="flex flex-col gap-1 text-xs font-medium">
                <span>Colour</span>
                <select
                  className="rounded border border-line-strong px-2 py-1 text-sm font-normal"
                  aria-label={`Colour of video ${i + 1}`}
                  value={item.colourName ?? ""}
                  onChange={(e) =>
                    void save(
                      photos,
                      videos.map((v) => (v.id === item.id ? { ...v, colourName: e.target.value || null } : v)),
                    )
                  }
                >
                  <option value="">All colours</option>
                  {[...new Set([...colours, ...(item.colourName ? [item.colourName] : [])])].map((c) => (
                    <option key={c} value={c}>
                      {c}
                    </option>
                  ))}
                </select>
              </label>
              <button
                type="button"
                className="self-start text-xs text-bad hover:underline"
                aria-label={`Delete video ${i + 1}`}
                onClick={() => void remove(item)}
              >
                Delete
              </button>
            </li>
          ))}
          {queuedVideos.map((item) => (
            <li key={item.id} className="flex w-40 flex-col gap-2">
              {item.preview ? (
                <video
                  src={item.preview}
                  controls
                  playsInline
                  preload="metadata"
                  aria-label={item.file.name}
                  className="aspect-[3/4] w-40 rounded bg-panel object-cover"
                />
              ) : (
                <span className="text-sm">{item.file.name}</span>
              )}
              <span className="text-xs text-ink-soft">Uploads when you save; pick its colour after.</span>
              <button
                type="button"
                className="self-start text-xs text-bad hover:underline"
                aria-label={`Remove ${item.file.name}`}
                onClick={() => unqueue(item)}
              >
                Remove
              </button>
            </li>
          ))}
        </ul>
        {!videosFull && (
          <div className="flex flex-wrap items-center gap-3">
            <label
              className={cn(
                "inline-flex items-center rounded-md border border-line-strong px-3 py-1.5 text-sm font-medium",
                "has-[:focus-visible]:outline-2",
                hasPhoto ? "cursor-pointer hover:bg-panel-2" : "cursor-not-allowed opacity-50",
              )}
            >
              Add video
              <input
                type="file"
                accept={ACCEPT_VIDEO}
                disabled={!hasPhoto}
                className="sr-only"
                aria-label="Add video"
                onChange={(e) => {
                  const file = e.target.files?.[0];
                  if (file) void addVideo(file);
                  e.target.value = "";
                }}
              />
            </label>
            <span className="text-xs text-ink-soft">
              {hasPhoto
                ? videoCount === 0
                  ? "Opens your gallery. At least one is needed to publish."
                  : `Opens your gallery. ${MAX_VIDEOS - videoCount} more allowed.`
                : "Add a photo first."}
            </span>
          </div>
        )}
      </div>
    </section>
  );
}

/** The "Main" label on the main photo, or a button to make another photo the main one. */
function MainChoice({
  main,
  label,
  onPick,
  disabled = false,
}: Readonly<{ main: boolean; label: string; onPick: () => void; disabled?: boolean }>) {
  if (main) {
    return <span className="self-start rounded-full bg-accent px-2 py-0.5 text-xs font-medium text-white">Main</span>;
  }
  if (disabled) return null;
  return (
    <button
      type="button"
      className="self-start text-xs text-accent-ink hover:underline"
      aria-label={label}
      onClick={onPick}
    >
      Set as main
    </button>
  );
}
