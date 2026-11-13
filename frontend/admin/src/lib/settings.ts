"use client";

import { useCallback, useEffect, useState } from "react";
import { ApiError, type FieldError, type SettingsImagePurpose, type SettingsSections } from "@vaabhi/api-client";
import { api } from "./api";
import { resizeToWebp, type Rendition } from "./image-resize";

/** Turns any failure into one sentence for the page and the API's per-field messages. */
export function describeError(error: unknown): { message: string; fields: FieldError[] } {
  if (error instanceof ApiError) {
    return { message: error.detail ?? "Could not save. Try again.", fields: error.errors };
  }
  // fetch() rejects with a TypeError when the request never got an answer; anything else (a photo the browser
  // cannot open, a missing WebP encoder…) says what went wrong itself.
  if (error instanceof Error && !(error instanceof TypeError) && error.message) {
    return { message: error.message, fields: [] };
  }
  return { message: "Could not reach the server. Check your connection and try again.", fields: [] };
}

export type Saveable<T> = {
  value: T | undefined;
  setValue: (next: T) => void;
  /** Patch one or more fields. */
  update: (patch: Partial<T>) => void;
  loading: boolean;
  loadError: string | null;
  saving: boolean;
  saved: boolean;
  error: string | null;
  fieldError: (field: string) => string | undefined;
  /** Saves the current value, or `override` (used by toggles that save at once). */
  save: (override?: T) => Promise<boolean>;
};

/**
 * Load-edit-save for anything the API reads with `load` and writes with `store` (settings sections, lists).
 * The saved flag clears on the next edit.
 */
export function useSaveable<T>(load: () => Promise<T>, store: (value: T) => Promise<T>): Saveable<T> {
  const [value, setValueState] = useState<T>();
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fields, setFields] = useState<FieldError[]>([]);

  useEffect(() => {
    let cancelled = false;
    load()
      .then((loaded) => !cancelled && setValueState(loaded))
      .catch((e: unknown) => !cancelled && setLoadError(describeError(e).message))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
    // load is stable per page (an inline call to a fixed endpoint)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const setValue = useCallback((next: T) => {
    setValueState(next);
    setSaved(false);
  }, []);

  const update = useCallback((patch: Partial<T>) => {
    setValueState((current) => (current === undefined ? current : { ...current, ...patch }));
    setSaved(false);
  }, []);

  const save = async (override?: T) => {
    const toSave = override ?? value;
    if (toSave === undefined) return false;
    if (override !== undefined) setValueState(override);
    setSaving(true);
    setError(null);
    setFields([]);
    try {
      setValueState(await store(toSave));
      setSaved(true);
      return true;
    } catch (e) {
      const described = describeError(e);
      setError(described.message);
      setFields(described.fields);
      setSaved(false);
      return false;
    } finally {
      setSaving(false);
    }
  };

  return {
    value,
    setValue,
    update,
    loading,
    loadError,
    saving,
    saved,
    error,
    fieldError: (field) => fields.find((f) => f.field === field)?.message,
    save,
  };
}

/** One JSON settings section, e.g. `useSection("billing")`. */
export function useSection<K extends keyof SettingsSections>(section: K): Saveable<SettingsSections[K]> {
  return useSaveable(
    () => api.admin.settings.get(section),
    (value) => api.admin.settings.save(section, value),
  );
}

type Resize = (file: Blob, widths: readonly number[]) => Promise<Rendition[]>;

/** Resizes a logo or QR to a 400 px WebP and uploads it; returns the key to save in the section. */
export async function uploadSettingsImage(
  purpose: SettingsImagePurpose,
  file: Blob,
  resize: Resize = resizeToWebp,
): Promise<string> {
  const [rendition] = await resize(file, [400]);
  const upload = await api.admin.settings.startImageUpload(purpose);
  const response = await fetch(upload.uploadUrl, {
    method: "PUT",
    body: rendition!.blob,
    headers: { "Content-Type": "image/webp", "Cache-Control": "public, max-age=31536000, immutable" },
  });
  if (!response.ok) throw new ApiError(response.status, "Uploading the image failed");
  return upload.key;
}

/** 1250 → "₹ 1,250", 1250.5 → "₹ 1,250.5" (amounts are rupees with up to two decimals). */
export function rupees(amount: number): string {
  return `₹ ${amount.toLocaleString("en-IN", { maximumFractionDigits: 2 })}`;
}

/** Rounds to paise (two decimals), so sums of rupee amounts do not drift (0.1 + 0.2). */
export function roundRupees(amount: number): number {
  return Math.round(amount * 100) / 100;
}

/** "1,250.50" typed by a person → 1250.5 rupees, or null when it is not a number. */
export function parseRupees(text: string): number | null {
  const cleaned = text.replace(/[₹,\s]/g, "");
  if (!/^\d+(\.\d{1,2})?$/.test(cleaned)) return null;
  return roundRupees(Number(cleaned));
}

/** Empty inputs become null so optional fields are cleared rather than saved as "". */
export function orNull(text: string): string | null {
  const trimmed = text.trim();
  return trimmed === "" ? null : trimmed;
}
