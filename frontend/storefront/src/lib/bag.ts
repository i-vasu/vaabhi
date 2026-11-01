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

export const MAX_QUANTITY = 5;
const EMPTY: BagLine[] = [];
export const bagStore = localStore<BagLine[]>("bag.v1", EMPTY);

const sameLine = (a: BagLine, b: BagLine) =>
  a.sku === b.sku && a.addonCode === b.addonCode && a.blouseSize === b.blouseSize;

export function addToBag(line: Omit<BagLine, "quantity">): BagLine[] {
  const lines = bagStore.read();
  const existing = lines.find((l) => sameLine(l, { ...line, quantity: 1 }));
  const next = existing
    ? lines.map((l) => (l === existing ? { ...l, quantity: Math.min(l.quantity + 1, MAX_QUANTITY) } : l))
    : [...lines, { ...line, quantity: 1 }];
  bagStore.write(next);
  return next;
}

export function bagCount(lines: BagLine[]): number {
  return lines.reduce((n, l) => n + l.quantity, 0);
}

export const wishlistStore = localStore<string[]>("wishlist.v1", []);

export function toggleWishlist(slug: string): boolean {
  const list = wishlistStore.read();
  const on = !list.includes(slug);
  wishlistStore.write(on ? [...list, slug] : list.filter((s) => s !== slug));
  return on;
}
