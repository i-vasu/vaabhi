/**
 * Delivery estimate shown after a pincode check, until a courier integration gives real dates:
 * 4–6 days from today, plus the stitching time of the chosen blouse option.
 */
export const BASE_DELIVERY_DAYS = { min: 4, max: 6 } as const;

export function isValidPincode(value: string): boolean {
  return /^[1-9]\d{5}$/.test(value.trim());
}

/** Extra days for stitching, by add-on input: standard sizes 5–7, made to measure 7–10. */
export function stitchingDays(input: "NONE" | "SIZE" | "MEASUREMENTS" | undefined): { min: number; max: number } {
  if (input === "SIZE") return { min: 5, max: 7 };
  if (input === "MEASUREMENTS") return { min: 7, max: 10 };
  return { min: 0, max: 0 };
}

export function deliveryWindow(from: Date, extra: { min: number; max: number }): { earliest: Date; latest: Date } {
  const add = (days: number) => {
    const d = new Date(from);
    d.setDate(d.getDate() + days);
    return d;
  };
  return { earliest: add(BASE_DELIVERY_DAYS.min + extra.min), latest: add(BASE_DELIVERY_DAYS.max + extra.max) };
}

const day = new Intl.DateTimeFormat("en-IN", { weekday: "short", day: "numeric", month: "short" });

export function formatDeliveryWindow({ earliest, latest }: { earliest: Date; latest: Date }): string {
  return `${day.format(earliest)} – ${day.format(latest)}`;
}
