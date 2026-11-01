import { localStore } from "./local-store";

/**
 * The shopping bag lives in the browser until checkout, where the server re-checks stock and prices.
 * A line is one variant with one add-on choice (and blouse size).
 */
export type BagLine = {
  sku: string;
  slug: string;
  name: string;
  colourName: string;
  colourHex: string;
  size: string | null;
  addonCode: string | null;
  addonName: string | null;
  blouseSize: string | null;
  unitPricePaise: number;
  addonPricePaise: number;
  imageKey: string | null;
  quantity: number;
};

/** Per line, as agreed with the shop (the API enforces the same limit). */
export const MAX_QUANTITY = 3;
const EMPTY: BagLine[] = [];
export const bagStore = localStore<BagLine[]>("bag.v1", EMPTY);

type LineIdentity = Pick<BagLine, "sku" | "addonCode" | "blouseSize">;

/** A line is one variant with one add-on choice and blouse size. */
export const lineKey = (l: LineIdentity) => `${l.sku}|${l.addonCode ?? ""}|${l.blouseSize ?? ""}`;

/** Adds one; `capped` when the line was already at the limit, so nothing changed. */
export function addToBag(line: Omit<BagLine, "quantity">): { lines: BagLine[]; capped: boolean } {
  const lines = bagStore.read();
  const existing = lines.find((l) => lineKey(l) === lineKey(line));
  if (existing && existing.quantity >= MAX_QUANTITY) return { lines, capped: true };
  const next = existing
    ? lines.map((l) => (l === existing ? { ...l, quantity: l.quantity + 1 } : l))
    : [...lines, { ...line, quantity: 1 }];
  bagStore.write(next);
  return { lines: next, capped: false };
}

export function setQuantity(key: string, quantity: number): void {
  const q = Math.max(1, Math.min(quantity, MAX_QUANTITY));
  bagStore.write(bagStore.read().map((l) => (lineKey(l) === key ? { ...l, quantity: q } : l)));
}

/** Removes a line and returns it with its position, for Undo. */
export function removeLine(key: string): { line: BagLine; index: number } | undefined {
  const lines = bagStore.read();
  const index = lines.findIndex((l) => lineKey(l) === key);
  if (index < 0) return undefined;
  bagStore.write(lines.filter((_, i) => i !== index));
  return { line: lines[index]!, index };
}

export function restoreLine({ line, index }: { line: BagLine; index: number }): void {
  const lines = bagStore.read().filter((l) => lineKey(l) !== lineKey(line));
  lines.splice(Math.min(index, lines.length), 0, line);
  bagStore.write(lines);
}

export function bagCount(lines: BagLine[]): number {
  return lines.reduce((n, l) => n + l.quantity, 0);
}

export const wishlistStore = localStore<string[]>("wishlist.v1", []);

export function addToWishlist(slug: string): void {
  const list = wishlistStore.read();
  if (!list.includes(slug)) wishlistStore.write([...list, slug]);
}

export function toggleWishlist(slug: string): boolean {
  const list = wishlistStore.read();
  const on = !list.includes(slug);
  wishlistStore.write(on ? [...list, slug] : list.filter((s) => s !== slug));
  return on;
}
