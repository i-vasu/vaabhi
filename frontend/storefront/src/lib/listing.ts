import type { ListingParams, ProductListing, ProductSummary } from "@vaabhi/api-client";
import type { BadgeTone, ProductCardProps } from "@vaabhi/ui";
import { discountLabel, formatPrice } from "./format";

/**
 * The listing's state lives in the URL, so filtered pages are shareable, survive reloads and work without
 * JavaScript. Prices in the URL are rupees (`priceMax=5000`); the API takes paise.
 */
export type ListingState = {
  /** Selected codes by filter group: facets (fabric, occasion…), colour and size. */
  selected: Record<string, string[]>;
  inStock: boolean;
  priceMin?: number;
  priceMax?: number;
  sort: SortValue;
  /** Pages loaded so far ("load more" grows this). */
  page: number;
};

export type SearchParams = Record<string, string | string[] | undefined>;

export const PAGE_SIZE = 24;
export const MAX_PAGES = 10;

export const SORTS = [
  { value: "popular", label: "Popular", short: "Popular" },
  { value: "new", label: "New arrivals", short: "Newest" },
  { value: "price_asc", label: "Price: low to high", short: "Price ↑" },
  { value: "price_desc", label: "Price: high to low", short: "Price ↓" },
  { value: "discount", label: "Biggest discount", short: "Discount" },
] as const;
export type SortValue = (typeof SORTS)[number]["value"];

/** Price bands in rupees for the filter sheet (single choice). */
export const PRICE_BANDS = [
  { label: "Under ₹2,000", max: 2000 },
  { label: "₹2,000 – ₹5,000", min: 2000, max: 5000 },
  { label: "₹5,000 – ₹10,000", min: 5000, max: 10000 },
  { label: "₹10,000 – ₹20,000", min: 10000, max: 20000 },
  { label: "Above ₹20,000", min: 20000 },
] as const satisfies ReadonlyArray<{ label: string; min?: number; max?: number }>;

const RESERVED = new Set(["inStock", "priceMin", "priceMax", "sort", "page"]);
const CODE = /^[a-z0-9-]{1,60}$/i;

function first(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

function positiveInt(value: string | undefined): number | undefined {
  if (!value || !/^\d{1,9}$/.test(value)) return undefined;
  return Number(value);
}

export function parseListingState(params: SearchParams): ListingState {
  const selected: Record<string, string[]> = {};
  for (const [key, raw] of Object.entries(params)) {
    if (RESERVED.has(key) || !CODE.test(key) || raw === undefined) continue;
    const codes = [...new Set((Array.isArray(raw) ? raw : [raw]).flatMap((v) => v.split(",")).map((v) => v.trim()))];
    const valid = codes.filter((code) => CODE.test(code));
    if (valid.length) selected[key] = valid;
  }
  const sort = SORTS.find((s) => s.value === first(params.sort))?.value ?? "popular";
  let priceMin = positiveInt(first(params.priceMin));
  let priceMax = positiveInt(first(params.priceMax));
  if (priceMin !== undefined && priceMax !== undefined && priceMin > priceMax) {
    [priceMin, priceMax] = [priceMax, priceMin];
  }
  const page = Math.min(Math.max(positiveInt(first(params.page)) ?? 1, 1), MAX_PAGES);
  return { selected, inStock: first(params.inStock) === "true", priceMin, priceMax, sort, page };
}

/** Canonical query string for a state (sorted keys, defaults omitted), with a leading "?" or empty. */
export function listingHref(pathname: string, state: ListingState): string {
  const search = new URLSearchParams();
  for (const key of Object.keys(state.selected).sort()) {
    const codes = state.selected[key];
    if (codes?.length) search.set(key, [...codes].sort().join(","));
  }
  if (state.inStock) search.set("inStock", "true");
  if (state.priceMin !== undefined) search.set("priceMin", String(state.priceMin));
  if (state.priceMax !== undefined) search.set("priceMax", String(state.priceMax));
  if (state.sort !== "popular") search.set("sort", state.sort);
  if (state.page > 1) search.set("page", String(state.page));
  const query = search.toString();
  return query ? `${pathname}?${query}` : pathname;
}

/** Any filter change starts again from the first page. */
function changed(state: ListingState, patch: Partial<ListingState>): ListingState {
  return { ...state, ...patch, page: 1 };
}

export function toggleValue(state: ListingState, group: string, code: string): ListingState {
  const current = state.selected[group] ?? [];
  const next = current.includes(code) ? current.filter((c) => c !== code) : [...current, code];
  const selected = { ...state.selected, [group]: next };
  if (!next.length) delete selected[group];
  return changed(state, { selected });
}

export function withPrice(state: ListingState, min?: number, max?: number): ListingState {
  return changed(state, { priceMin: min, priceMax: max });
}

export function withInStock(state: ListingState, inStock: boolean): ListingState {
  return changed(state, { inStock });
}

export function withSort(state: ListingState, sort: SortValue): ListingState {
  return changed(state, { sort });
}

export function withPage(state: ListingState, page: number): ListingState {
  return { ...state, page: Math.min(page, MAX_PAGES) };
}

/** Clears every filter but keeps the sort order. */
export function clearFilters(state: ListingState): ListingState {
  return changed(state, { selected: {}, inStock: false, priceMin: undefined, priceMax: undefined });
}

export function activeFilterCount(state: ListingState): number {
  const values = Object.values(state.selected).reduce((n, codes) => n + codes.length, 0);
  const price = state.priceMin !== undefined || state.priceMax !== undefined ? 1 : 0;
  return values + price + (state.inStock ? 1 : 0);
}

export function apiParams(state: ListingState, page: number): ListingParams {
  const params: ListingParams = { sort: state.sort, page, pageSize: PAGE_SIZE, inStock: state.inStock || undefined };
  for (const [group, codes] of Object.entries(state.selected)) params[group] = codes.join(",");
  if (state.priceMin !== undefined) params.priceMin = state.priceMin * 100;
  if (state.priceMax !== undefined) params.priceMax = state.priceMax * 100;
  return params;
}

export function priceLabel(min?: number, max?: number): string | undefined {
  const band = PRICE_BANDS.find(
    (b) => ("min" in b ? b.min : undefined) === min && ("max" in b ? b.max : undefined) === max,
  );
  if (band) return band.label;
  const rupees = (n: number) => formatPrice(n);
  if (min !== undefined && max !== undefined) return `${rupees(min)} – ${rupees(max)}`;
  if (max !== undefined) return `Under ${rupees(max)}`;
  if (min !== undefined) return `Above ${rupees(min)}`;
  return undefined;
}

export type AppliedFilter = { key: string; label: string; remove: ListingState };

/** One removable chip per applied value, labelled with the names the API returned. */
export function appliedFilters(state: ListingState, listing: ProductListing): AppliedFilter[] {
  const chips: AppliedFilter[] = [];
  for (const [group, codes] of Object.entries(state.selected)) {
    const options = listing.filters.find((g) => g.code === group)?.options ?? [];
    for (const code of codes) {
      const name = options.find((o) => o.code === code)?.name;
      if (name) chips.push({ key: `${group}:${code}`, label: name, remove: toggleValue(state, group, code) });
    }
  }
  const price = priceLabel(state.priceMin, state.priceMax);
  if (price) chips.push({ key: "price", label: price, remove: withPrice(state) });
  if (state.inStock) chips.push({ key: "inStock", label: "In stock", remove: withInStock(state, false) });
  return chips;
}

const badgeTones: Record<NonNullable<ProductSummary["badge"]>, BadgeTone> = {
  NEW: "festive",
  BESTSELLER: "gold",
  BRIDAL: "gold",
  HANDLOOM: "olive",
};

/** Darkens a #RRGGBB colour, for the two-tone photo placeholder. */
export function shade(hex: string, factor = 0.55): string {
  const n = Number.parseInt(hex.slice(1), 16);
  const channel = (shift: number) =>
    Math.round(((n >> shift) & 255) * factor)
      .toString(16)
      .padStart(2, "0");
  return `#${channel(16)}${channel(8)}${channel(0)}`.toUpperCase();
}

const PLACEHOLDER: [string, string] = ["#E4DCD0", "#A88B5A"];

export function mediaUrl(key: string, width: 400 | 800 | 1600 = 800): string | undefined {
  const base = process.env.NEXT_PUBLIC_MEDIA_URL;
  return base ? `${base.replace(/\/$/, "")}/${key}/${width}.webp` : undefined;
}

export function productCardProps(product: ProductSummary): ProductCardProps {
  const price = product.pricePaise / 100;
  const mrp = product.mrpPaise / 100;
  const detail = [
    product.subtitle,
    !product.inStock ? "Out of stock" : product.stockLeft ? `Only ${product.stockLeft} left` : undefined,
  ]
    .filter(Boolean)
    .join(" · ");
  const src = product.image ? mediaUrl(product.image.key) : undefined;
  const colour = product.colours[0]?.hex;
  return {
    name: product.name,
    detail,
    price: formatPrice(price),
    mrp: mrp > price ? formatPrice(mrp) : undefined,
    discount: discountLabel(price, mrp),
    href: `/p/${product.slug}`,
    badge: product.badge ? { label: product.badge.toLowerCase(), tone: badgeTones[product.badge] } : undefined,
    image:
      src && product.image
        ? { src, alt: product.image.alt }
        : { placeholder: colour ? [colour, shade(colour)] : PLACEHOLDER },
  };
}
