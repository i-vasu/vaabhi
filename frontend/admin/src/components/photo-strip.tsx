"use client";

import { useState } from "react";
import { mediaUrl } from "@/lib/photo-upload";
import { describeError } from "@/lib/settings";
import { CloseIcon, ImageIcon } from "./icons";
import { Sheet } from "./ui";

export type StripPhoto = { id: number; key: string };

/**
 * A row of photos (stock references, design references) with an add tile until `max`, remove buttons and tap to
 * enlarge. `onAdd` gets the picked file and does the upload and save; its failure is shown under the strip.
 */
export function PhotoStrip({
  label,
  photos,
  max = 6,
  onAdd,
  onRemove,
}: {
  label: string;
  photos: StripPhoto[];
  max?: number;
  onAdd: (file: File) => Promise<void>;
  onRemove: (id: number) => Promise<void>;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState<StripPhoto | null>(null);

  const act = async (action: () => Promise<void>) => {
    setBusy(true);
    setError(null);
    try {
      await action();
    } catch (e) {
      setError(describeError(e).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div>
      <ul className="flex flex-wrap gap-3">
        {photos.map((photo, index) => (
          <li key={photo.id} className="relative">
            <button
              type="button"
              onClick={() => setOpen(photo)}
              aria-label={`Open ${label.toLowerCase()} ${index + 1}`}
            >
              {/* eslint-disable-next-line @next/next/no-img-element -- static export, R2 serves the resized file */}
              <img
                src={mediaUrl(photo.key)}
                alt={`${label} ${index + 1}`}
                className="size-20 rounded-xl border border-line object-cover"
              />
            </button>
            <button
              type="button"
              disabled={busy}
              aria-label={`Remove ${label.toLowerCase()} ${index + 1}`}
              className="absolute -right-2 -top-2 grid size-6 place-items-center rounded-full border border-line bg-panel text-ink-faint hover:text-bad"
              onClick={() => act(() => onRemove(photo.id))}
            >
              <CloseIcon size={14} />
            </button>
          </li>
        ))}
        {photos.length < max && (
          <li>
            <label className="grid size-20 cursor-pointer place-items-center rounded-xl border border-dashed border-line-strong text-center text-xs text-ink-faint hover:text-accent-ink">
              {busy ? (
                "Uploading…"
              ) : (
                <span className="grid place-items-center gap-1">
                  <ImageIcon />
                  Add photo
                </span>
              )}
              <input
                type="file"
                accept="image/*"
                className="sr-only"
                disabled={busy}
                aria-label={`Add ${label.toLowerCase()}`}
                onChange={(event) => {
                  const file = event.target.files?.[0];
                  event.target.value = "";
                  if (file) void act(() => onAdd(file));
                }}
              />
            </label>
          </li>
        )}
      </ul>
      {error && (
        <p role="alert" className="mt-1.5 text-sm text-bad">
          {error}
        </p>
      )}
      <Sheet open={open !== null} title={label} onClose={() => setOpen(null)}>
        {open && (
          // eslint-disable-next-line @next/next/no-img-element -- static export, R2 serves the resized file
          <img src={mediaUrl(open.key)} alt={label} className="w-full rounded-2xl border border-line" />
        )}
      </Sheet>
    </div>
  );
}
