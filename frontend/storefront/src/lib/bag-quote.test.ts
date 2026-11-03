import { describe, expect, it, vi } from "vitest";
import type { BagQuote, BagQuoteLine } from "@vaabhi/api-client";
import type { BagLine } from "./bag";
import { bagStitchingDays, fetchQuote, isBuyable, priceChanged } from "./bag-quote";

export const quoteLine = (o: Partial<BagQuoteLine> = {}): BagQuoteLine => ({
  sku: "VB-RANI",
  addonCode: null,
  status: "AVAILABLE",
  quantity: 1,
  maxQuantity: 3,
  stockLeft: null,
  slug: "saree",
  name: "Saree",
  colourName: "Rani pink",
  colourHex: "#C2185B",
  size: null,
  priceRupees: 100000,
  mrpRupees: 120000,
  addonName: null,
  addonPriceRupees: 0,
  addonInput: null,
  imageKey: null,
  ...o,
});

const local = { unitPriceRupees: 100000, addonPriceRupees: 0 } as BagLine;

describe("bag quote helpers", () => {
  it("posts the bag to the storefront route", async () => {
    const quote: BagQuote = { lines: [], itemsRupees: 0, addonsRupees: 0, mrpRupees: 0, totalRupees: 0 };
    const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify(quote)));
    await expect(
      fetchQuote([{ ...local, sku: "A", addonCode: "x", quantity: 2 } as BagLine], fetcher),
    ).resolves.toEqual(quote);
    expect(fetcher).toHaveBeenCalledWith("/api/bag/quote", expect.objectContaining({ method: "POST" }));
    expect(JSON.parse(fetcher.mock.calls[0]![1].body)).toEqual({ lines: [{ sku: "A", addonCode: "x", quantity: 2 }] });

    fetcher.mockResolvedValue(new Response("", { status: 502 }));
    await expect(fetchQuote([], fetcher)).rejects.toThrow("502");
  });

  it("knows what can be bought and when prices moved", () => {
    expect(isBuyable(quoteLine())).toBe(true);
    expect(isBuyable(quoteLine({ status: "LIMITED" }))).toBe(true);
    expect(isBuyable(quoteLine({ status: "OUT_OF_STOCK" }))).toBe(false);
    expect(isBuyable(undefined)).toBe(false);
    expect(priceChanged(local, quoteLine())).toBe(false);
    expect(priceChanged(local, quoteLine({ priceRupees: 90000 }))).toBe(true);
    expect(priceChanged(local, quoteLine({ status: "UNAVAILABLE", priceRupees: 0 }))).toBe(false);
  });

  it("uses the longest stitching time in the bag", () => {
    expect(bagStitchingDays(null)).toEqual({ min: 0, max: 0 });
    const quote: BagQuote = {
      lines: [
        quoteLine({ addonInput: "SIZE" }),
        quoteLine({ addonInput: "MEASUREMENTS", status: "OUT_OF_STOCK" }),
        quoteLine(),
      ],
      itemsRupees: 0,
      addonsRupees: 0,
      mrpRupees: 0,
      totalRupees: 0,
    };
    expect(bagStitchingDays(quote)).toEqual({ min: 5, max: 7 });
  });
});
