import type { Media, ProductAddon, ProductDetail, ProductVariant } from "@vaabhi/api-client";

/** Pure rules for the product page: colours, sizes, photos, add-on choices and totals. */

export type ColourOption = { name: string; hex: string; inStock: boolean };

export const BLOUSE_SIZES = ["32", "34", "36", "38", "40", "42", "44"] as const;

/** Standard blouse measurements (inches) for the size guide. */
export const BLOUSE_SIZE_GUIDE = [
  { size: "32", bust: 32, waist: 26, length: 14 },
  { size: "34", bust: 34, waist: 28, length: 14.5 },
  { size: "36", bust: 36, waist: 30, length: 14.5 },
  { size: "38", bust: 38, waist: 32, length: 15 },
  { size: "40", bust: 40, waist: 34, length: 15 },
  { size: "42", bust: 42, waist: 36, length: 15.5 },
  { size: "44", bust: 44, waist: 38, length: 15.5 },
] as const;

/** Colours in the order the shop set them, each in stock when any of its sizes is. */
export function colourOptions(product: ProductDetail): ColourOption[] {
  const colours = new Map<string, ColourOption>();
  for (const v of product.variants) {
    const existing = colours.get(v.colourName);
    if (existing) existing.inStock ||= v.inStock;
    else colours.set(v.colourName, { name: v.colourName, hex: v.colourHex, inStock: v.inStock });
  }
  return [...colours.values()];
}

export function isOneSize(product: ProductDetail): boolean {
  return product.variants.every((v) => v.size === null);
}

export function variantsOf(product: ProductDetail, colour: string): ProductVariant[] {
  return product.variants.filter((v) => v.colourName === colour);
}

/** The colour to show first: the one asked for (e.g. from the listing), else the first in stock. */
export function initialColour(product: ProductDetail, requested?: string): string {
  const colours = colourOptions(product);
  return (
    colours.find((c) => c.name.toLowerCase() === requested?.toLowerCase())?.name ??
    colours.find((c) => c.inStock)?.name ??
    colours[0]!.name
  );
}

/** The variant to buy: one-size products need only a colour; sized ones need a size too. */
export function selectedVariant(
  product: ProductDetail,
  colour: string,
  size: string | null,
): ProductVariant | undefined {
  const variants = variantsOf(product, colour);
  if (isOneSize(product)) return variants[0];
  return variants.find((v) => v.size === size);
}

/** Photos for a colour: those tagged with it plus untagged ones; all photos when none match. */
export function photosFor(product: ProductDetail, colour: string): Media[] {
  const images = product.media.filter((m) => m.kind === "IMAGE");
  const matching = images.filter((m) => m.colourName === null || m.colourName === colour);
  return matching.length ? matching : images;
}

export type AddonChoice = {
  code: string | null;
  name: string;
  description: string | null;
  pricePaise: number;
  input: ProductAddon["input"];
};

/**
 * The choices shown as radio cards. When no add-on is free (e.g. a lehenga offering only custom stitching),
 * a free "without" choice comes first so stitching stays optional.
 */
export function addonChoices(product: ProductDetail): AddonChoice[] {
  const choices: AddonChoice[] = product.addons.map((a) => ({
    code: a.code,
    name: a.name,
    description: a.description,
    pricePaise: a.pricePaise,
    input: a.input,
  }));
  if (choices.length && !choices.some((c) => c.pricePaise === 0)) {
    choices.unshift({
      code: null,
      name: "Without blouse stitching",
      description: "Blouse fabric as supplied",
      pricePaise: 0,
      input: "NONE",
    });
  }
  return choices;
}

export function defaultChoice(choices: AddonChoice[]): AddonChoice | undefined {
  return choices.find((c) => c.pricePaise === 0) ?? choices[0];
}

/** "One size · 5.5 m saree with a 0.8 m blouse piece" for sarees; "One size" otherwise. */
export function oneSizeLine(product: ProductDetail): string {
  if (product.sareeLengthM === null) return "One size";
  const blouse =
    product.blouseIncluded && product.blouseLengthM !== null ? ` with a ${product.blouseLengthM} m blouse piece` : "";
  return `One size · ${product.sareeLengthM} m saree${blouse}`;
}

/** Stock message for the chosen variant. */
export type StockTone = "low" | "ok" | "neutral";

export function stockLine(variant: ProductVariant | undefined, colour: string): { text: string; tone: StockTone } {
  const what = variant?.size ? `${colour}, size ${variant.size}` : colour;
  if (!variant) return { text: "Choose a size to see availability", tone: "neutral" };
  if (!variant.inStock) return { text: `Out of stock in ${what}`, tone: "low" };
  if (variant.stockLeft) return { text: `Only ${variant.stockLeft} left in ${what}`, tone: "low" };
  return { text: `In stock in ${what}`, tone: "ok" };
}
