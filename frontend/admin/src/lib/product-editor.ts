import type { AddonForm, AdminProduct, FieldError, ProductForm, ProductStatus, VariantForm } from "@vaabhi/api-client";

/**
 * The product editor works in rupees and strings (what people type); the API takes paise and numbers.
 * Every row has a `key` so React keeps inputs stable while rows are added and removed.
 */
export type VariantRow = {
  key: string;
  sku: string;
  colourName: string;
  colourHex: string;
  colourFamily: string;
  /** "" = one size. */
  size: string;
  price: string;
  mrp: string;
  stock: string;
  active: boolean;
};

export type AddonRow = {
  key: string;
  code: string;
  name: string;
  description: string;
  price: string;
  input: AddonForm["input"];
  active: boolean;
};

export type EditorState = {
  name: string;
  slug: string;
  subtitle: string;
  description: string;
  categorySlug: string;
  badge: "" | NonNullable<ProductForm["badge"]>;
  careInstructions: string;
  sareeLengthM: string;
  blouseIncluded: boolean;
  blouseLengthM: string;
  attributes: Record<string, string[]>;
  variants: VariantRow[];
  addons: AddonRow[];
};

let counter = 0;
export const newKey = () => `row-${++counter}`;

export const STATUS_LABELS: Record<ProductStatus, string> = {
  DRAFT: "Draft",
  PUBLISHED: "Published",
  ARCHIVED: "Archived",
};

export const BADGES = ["NEW", "BESTSELLER", "BRIDAL", "HANDLOOM"] as const;

export function emptyState(categorySlug = ""): EditorState {
  return {
    name: "",
    slug: "",
    subtitle: "",
    description: "",
    categorySlug,
    badge: "",
    careInstructions: "",
    sareeLengthM: categorySlug === "sarees" ? "5.5" : "",
    blouseIncluded: categorySlug === "sarees",
    blouseLengthM: categorySlug === "sarees" ? "0.8" : "",
    attributes: {},
    variants: [],
    addons: [],
  };
}

const rupees = (paise: number) => String(paise / 100);
const text = (value: string | null | undefined) => value ?? "";

export function stateFromProduct(product: AdminProduct): EditorState {
  return {
    name: product.name,
    slug: product.slug,
    subtitle: text(product.subtitle),
    description: text(product.description),
    categorySlug: product.categorySlug,
    badge: product.badge ?? "",
    careInstructions: text(product.careInstructions),
    sareeLengthM: product.sareeLengthM == null ? "" : String(product.sareeLengthM),
    blouseIncluded: product.blouseIncluded,
    blouseLengthM: product.blouseLengthM == null ? "" : String(product.blouseLengthM),
    attributes: product.attributes,
    variants: product.variants.map((v) => ({
      key: newKey(),
      sku: text(v.sku),
      colourName: v.colourName,
      colourHex: v.colourHex,
      colourFamily: v.colourFamily,
      size: text(v.size),
      price: rupees(v.pricePaise),
      mrp: rupees(v.mrpPaise),
      stock: String(v.stockQty),
      active: v.active,
    })),
    addons: product.addons.map((a) => ({
      key: newKey(),
      code: a.code,
      name: a.name,
      description: text(a.description),
      price: rupees(a.pricePaise),
      input: a.input,
      active: a.active,
    })),
  };
}

/** "1,250.50" → 125050 paise; anything unparseable becomes 0 so the API reports it next to the field. */
export function toPaise(value: string): number {
  const amount = Number.parseFloat(value.replaceAll(",", "").trim());
  return Number.isFinite(amount) ? Math.round(amount * 100) : 0;
}

function toInt(value: string): number {
  const n = Number.parseInt(value.trim(), 10);
  return Number.isFinite(n) ? n : 0;
}

function toDecimal(value: string): number | null {
  const n = Number.parseFloat(value.trim());
  return Number.isFinite(n) ? n : null;
}

const orNull = (value: string) => (value.trim() === "" ? null : value.trim());

export function formFromState(state: EditorState): ProductForm {
  const isSaree = state.categorySlug === "sarees";
  const attributes = Object.fromEntries(Object.entries(state.attributes).filter(([, values]) => values.length > 0));
  return {
    name: state.name.trim(),
    slug: orNull(state.slug),
    subtitle: orNull(state.subtitle),
    description: orNull(state.description),
    categorySlug: state.categorySlug,
    badge: state.badge === "" ? null : state.badge,
    careInstructions: orNull(state.careInstructions),
    sareeLengthM: isSaree ? toDecimal(state.sareeLengthM) : null,
    blouseIncluded: isSaree && state.blouseIncluded,
    blouseLengthM: isSaree && state.blouseIncluded ? toDecimal(state.blouseLengthM) : null,
    attributes,
    variants: state.variants.map((v): VariantForm => ({
      sku: orNull(v.sku.toUpperCase()),
      colourName: v.colourName.trim(),
      colourHex: v.colourHex,
      colourFamily: v.colourFamily,
      size: orNull(v.size),
      pricePaise: toPaise(v.price),
      mrpPaise: toPaise(v.mrp === "" ? v.price : v.mrp),
      stockQty: toInt(v.stock),
      active: v.active,
    })),
    addons: state.addons.map((a): AddonForm => ({
      code: a.code.trim(),
      kind: "BLOUSE",
      name: a.name.trim(),
      description: orNull(a.description),
      pricePaise: toPaise(a.price),
      input: a.input,
      active: a.active,
    })),
  };
}

/** The three blouse choices most sarees offer; prices are a starting point to edit. */
export function standardBlouseAddons(): AddonRow[] {
  return [
    {
      key: newKey(),
      code: "blouse-unstitched",
      name: "Unstitched blouse piece",
      description: "Included with the saree.",
      price: "0",
      input: "NONE",
      active: true,
    },
    {
      key: newKey(),
      code: "blouse-standard",
      name: "Stitched blouse, standard size",
      description: "Sizes 32 to 44. Ships in 5–7 extra days.",
      price: "1200",
      input: "SIZE",
      active: true,
    },
    {
      key: newKey(),
      code: "blouse-custom",
      name: "Stitched blouse, made to measure",
      description: "We call you for measurements. Ships in 7–10 extra days.",
      price: "1800",
      input: "MEASUREMENTS",
      active: true,
    },
  ];
}

export type NewColour = { colourName: string; colourHex: string; colourFamily: string; price: string; mrp: string };

/** One row per size (or a single one-size row) for a colour. */
export function variantsForColour(colour: NewColour, sizes: string[]): VariantRow[] {
  const forSizes = sizes.length ? sizes : [""];
  return forSizes.map((size) => ({
    key: newKey(),
    sku: "",
    ...colour,
    size,
    stock: "0",
    active: true,
  }));
}

/** Server field errors, e.g. "variants[2].mrpPaise", grouped for display. */
export function errorsFor(errors: FieldError[], prefix: string): string[] {
  return errors
    .filter((e) => e.field === prefix || e.field.startsWith(`${prefix}.`) || e.field.startsWith(`${prefix}[`))
    .map((e) => e.message);
}
