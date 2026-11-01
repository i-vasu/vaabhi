import type { BagQuote, BagQuoteLine } from "@vaabhi/api-client";
import type { BagLine } from "./bag";
import { stitchingDays } from "./delivery";

/** Asks the storefront's own /api/bag/quote for current prices and stock. */
export async function fetchQuote(lines: BagLine[], fetcher: typeof fetch = fetch): Promise<BagQuote> {
  const response = await fetcher("/api/bag/quote", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      lines: lines.map((l) => ({ sku: l.sku, addonCode: l.addonCode, quantity: l.quantity })),
    }),
  });
  if (!response.ok) throw new Error(`Bag quote failed with ${response.status}`);
  return (await response.json()) as BagQuote;
}

export const isBuyable = (q: BagQuoteLine | undefined) => q?.status === "AVAILABLE" || q?.status === "LIMITED";

/** The longest stitching time among buyable lines, for the delivery estimate. */
export function bagStitchingDays(quote: BagQuote | null): { min: number; max: number } {
  return (quote?.lines ?? [])
    .filter(isBuyable)
    .map((l) => stitchingDays(l.addonInput ?? undefined))
    .reduce((a, b) => ({ min: Math.max(a.min, b.min), max: Math.max(a.max, b.max) }), { min: 0, max: 0 });
}

/** True when the shop's price for the line moved since it was added. */
export function priceChanged(local: BagLine, quoted: BagQuoteLine | undefined): boolean {
  return (
    isBuyable(quoted) && quoted!.pricePaise + quoted!.addonPricePaise !== local.unitPricePaise + local.addonPricePaise
  );
}
