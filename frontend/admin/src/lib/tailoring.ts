"use client";

import { useCallback, useEffect, useState } from "react";
import {
  ApiError,
  type AlterationKind,
  type BillingSettings,
  type Client,
  type ClientSummary,
  type ClientType,
  type DiscountKind,
  type OrderStatus,
  type PaymentMode,
  type TailoringImagePurpose,
} from "@vaabhi/api-client";
import type { Tone } from "@/components/ui";
import { api } from "./api";
import { resizeToWebp, type Rendition } from "./image-resize";
import { describeError, roundRupees } from "./settings";

// --- labels ---------------------------------------------------------------------------------------------------

export const STATUS_LABELS: Record<OrderStatus, string> = {
  BOOKED: "Booked",
  IN_PROGRESS: "In progress",
  READY: "Ready",
  DELIVERED: "Delivered",
  CANCELLED: "Cancelled",
};

export const STATUS_TONES: Record<OrderStatus, Tone> = {
  BOOKED: "default",
  IN_PROGRESS: "accent",
  READY: "ok",
  DELIVERED: "ok",
  CANCELLED: "bad",
};

export const PAYMENT_MODES: { value: PaymentMode; label: string }[] = [
  { value: "CASH", label: "Cash" },
  { value: "UPI", label: "UPI" },
  { value: "CARD", label: "Card" },
  { value: "BANK", label: "Bank" },
];

export const CLIENT_TYPES: { value: ClientType; label: string }[] = [
  { value: "RETAIL", label: "Retail" },
  { value: "WHOLESALE", label: "Wholesale" },
  { value: "VIP", label: "VIP" },
];

export const ALTERATION_KINDS: { value: AlterationKind; label: string }[] = [
  { value: "FIT_ADJUSTMENT", label: "Fit adjustment" },
  { value: "LENGTH", label: "Length" },
  { value: "REPAIR", label: "Repair" },
  { value: "REWORK", label: "Rework" },
];

export const isOpen = (status: OrderStatus) => status !== "DELIVERED" && status !== "CANCELLED";

// --- dates ----------------------------------------------------------------------------------------------------

/** Today as yyyy-mm-dd in the browser's time zone (the shop's). */
export function today(now: Date = new Date()): string {
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 10);
}

export function addDays(isoDate: string, days: number): string {
  const date = new Date(`${isoDate}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

const shortDate = new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", timeZone: "UTC" });
const longDate = new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", year: "numeric", timeZone: "UTC" });

/** "2026-11-02" → "2 Nov" (this year) or "2 Nov 2025". */
export function formatDate(isoDate: string, now: Date = new Date()): string {
  const date = new Date(`${isoDate.slice(0, 10)}T00:00:00Z`);
  return date.getUTCFullYear() === now.getFullYear() ? shortDate.format(date) : longDate.format(date);
}

/** "+917013468353" → "70134 68353"; other numbers are shown as stored. */
export function formatPhone(phone: string): string {
  const match = /^\+91(\d{5})(\d{5})$/.exec(phone);
  return match ? `${match[1]} ${match[2]}` : phone;
}

// --- money ----------------------------------------------------------------------------------------------------

export type Totals = { itemsRupees: number; deliveryRupees: number; discountRupees: number; totalRupees: number };

/**
 * The same sums the API makes when it books an order, so the form shows the total that will be saved.
 * `discountValue` is rupees for AMOUNT and percent for PERCENT (10.5 = 10.5 %).
 */
export function orderTotals(
  items: { priceRupees: number; quantity: number }[],
  deliveryRupees: number,
  kind: DiscountKind,
  discountValue: number,
  base: BillingSettings["discountAppliesTo"],
): Totals {
  const itemsRupees = roundRupees(items.reduce((sum, item) => sum + item.priceRupees * item.quantity, 0));
  const discountBase = roundRupees(base === "ORDER_TOTAL" ? itemsRupees + deliveryRupees : itemsRupees);
  const discountRupees =
    kind === "PERCENT"
      ? roundRupees((discountBase * Math.min(discountValue, 100)) / 100)
      : Math.min(discountValue, discountBase);
  return {
    itemsRupees,
    deliveryRupees,
    discountRupees,
    totalRupees: roundRupees(itemsRupees + deliveryRupees - discountRupees),
  };
}

/** "12.5" (percent typed by a person) → 12.5, or null. */
export function parsePercent(text: string): number | null {
  const cleaned = text.replace(/[%\s]/g, "");
  if (!/^\d+(\.\d{1,2})?$/.test(cleaned)) return null;
  const value = Number(cleaned);
  return value <= 100 ? value : null;
}

// --- photos ---------------------------------------------------------------------------------------------------

type Resize = (file: Blob, widths: readonly number[]) => Promise<Rendition[]>;

/** Resizes a counter photo to a 400 px WebP and uploads it; returns the key to send with the form. */
export async function uploadTailoringPhoto(
  purpose: TailoringImagePurpose,
  file: Blob,
  resize: Resize = resizeToWebp,
): Promise<string> {
  return uploadKeyedPhoto(file, () => api.admin.startTailoringImageUpload(purpose), resize);
}

/**
 * Resizes a photo to a 400 px WebP and PUTs it to the URL `start` hands out (stock and piece photos use their own
 * endpoints); returns the key to save.
 */
export async function uploadKeyedPhoto(
  file: Blob,
  start: () => Promise<{ key: string; uploadUrl: string }>,
  resize: Resize = resizeToWebp,
): Promise<string> {
  const [rendition] = await resize(file, [400]);
  const upload = await start();
  const response = await fetch(upload.uploadUrl, {
    method: "PUT",
    body: rendition!.blob,
    headers: { "Content-Type": "image/webp", "Cache-Control": "public, max-age=31536000, immutable" },
  });
  if (!response.ok) throw new ApiError(response.status, "Uploading the photo failed");
  return upload.key;
}

// --- loading --------------------------------------------------------------------------------------------------

export type Loaded<T> = {
  value: T | undefined;
  setValue: (value: T) => void;
  error: string | null;
  loading: boolean;
  reload: () => void;
};

/** Loads `load()` and again whenever `deps` change; keeps the last value while reloading. */
export function useLoad<T>(load: () => Promise<T>, deps: readonly unknown[]): Loaded<T> {
  const [value, setValue] = useState<T>();
  const [error, setError] = useState<string | null>(null);
  const [tick, setTick] = useState(0);
  // Loading until the request for the current inputs has settled.
  const key = JSON.stringify([...deps, tick]);
  const [settled, setSettled] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    load()
      .then((loaded) => {
        if (cancelled) return;
        setValue(loaded);
        setError(null);
      })
      .catch((e: unknown) => !cancelled && setError(describeError(e).message))
      .finally(() => !cancelled && setSettled(key));
    return () => {
      cancelled = true;
    };
    // load is an inline call whose inputs are listed in deps (and so in key)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);

  const reload = useCallback(() => setTick((t) => t + 1), []);
  return { value, setValue, error, loading: settled !== key, reload };
}

/** Runs an action with a busy flag and an error message, e.g. for the buttons of a sheet. */
export function useAction() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const run = async <T>(action: () => Promise<T>): Promise<T | undefined> => {
    setBusy(true);
    setError(null);
    try {
      return await action();
    } catch (e) {
      setError(describeError(e).message);
      return undefined;
    } finally {
      setBusy(false);
    }
  };
  return { busy, error, setError, run };
}

/** The list-row view of a full customer record, for pickers. */
export function summaryOf(client: Client): ClientSummary {
  return {
    id: client.id,
    name: [client.firstName, client.lastName].filter(Boolean).join(" "),
    phone: client.phone,
    photoKey: client.photoKey,
    clientType: client.clientType,
    orderCount: client.orderCount,
    spentRupees: client.spentRupees,
    outstandingRupees: client.outstandingRupees,
    lastOrderDate: null,
  };
}

/** Loads the customer named by `?clientId=` (from the customer's page) so forms can start with them chosen. */
export function useInitialClient(clientId: number | null): { ready: boolean; client: ClientSummary | null } {
  const loaded = useLoad(
    () => (clientId ? api.admin.clients.get(clientId).then(summaryOf) : Promise.resolve(null)),
    [clientId],
  );
  return { ready: !loaded.loading || loaded.error !== null, client: loaded.value ?? null };
}

/** Reads a positive integer id from the query string (`?id=42`), or null. */
export function idParam(value: string | null): number | null {
  const id = Number(value);
  return Number.isInteger(id) && id > 0 ? id : null;
}
