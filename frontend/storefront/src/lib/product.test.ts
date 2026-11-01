import { describe, expect, it } from "vitest";
import { lehenga, saree } from "@/test/product-fixture";
import {
  addonChoices,
  colourOptions,
  defaultChoice,
  initialColour,
  isOneSize,
  oneSizeLine,
  photosFor,
  selectedVariant,
  stockLine,
} from "./product";

describe("product rules", () => {
  it("lists colours with stock across sizes", () => {
    expect(colourOptions(saree())).toEqual([
      { name: "Rani pink", hex: "#C2185B", inStock: true },
      { name: "Mustard", hex: "#D4A017", inStock: false },
    ]);
    expect(colourOptions(lehenga()).map((c) => [c.name, c.inStock])).toEqual([
      ["Red", true],
      ["Wine", true],
    ]);
  });

  it("starts with the requested colour, else the first in stock", () => {
    expect(initialColour(saree(), "mustard")).toBe("Mustard");
    expect(initialColour(saree(), "teal")).toBe("Rani pink");
    const allOut = saree({ variants: saree().variants.map((v) => ({ ...v, inStock: false })) });
    expect(initialColour(allOut)).toBe("Rani pink");
  });

  it("selects the variant by colour, and by size for sized products", () => {
    expect(isOneSize(saree())).toBe(true);
    expect(selectedVariant(saree(), "Mustard", null)?.sku).toBe("VB-MUST");
    expect(isOneSize(lehenga())).toBe(false);
    expect(selectedVariant(lehenga(), "Red", null)).toBeUndefined();
    expect(selectedVariant(lehenga(), "Red", "M")?.sku).toBe("VB-RED-M");
  });

  it("shows photos for the colour plus untagged ones", () => {
    expect(photosFor(saree(), "Mustard").map((m) => m.key)).toEqual(["products/1/front", "products/1/mustard"]);
    expect(photosFor(saree(), "Rani pink").map((m) => m.key)).toEqual(["products/1/front"]);
    const tagged = saree({ media: [saree().media[1]!] });
    expect(photosFor(tagged, "Rani pink").map((m) => m.key)).toEqual(["products/1/mustard"]);
  });

  it("adds a free choice when no add-on is free", () => {
    expect(addonChoices(saree()).map((c) => c.code)).toEqual(["blouse-unstitched", "blouse-standard", "blouse-custom"]);
    const choices = addonChoices(lehenga());
    expect(choices.map((c) => [c.code, c.pricePaise])).toEqual([
      [null, 0],
      ["blouse-custom", 150000],
    ]);
    expect(defaultChoice(choices)?.code).toBeNull();
    expect(addonChoices(saree({ addons: [] }))).toEqual([]);
    expect(defaultChoice([])).toBeUndefined();
  });

  it("describes one-size products", () => {
    expect(oneSizeLine(saree())).toBe("One size · 5.5 m saree with a 0.8 m blouse piece");
    expect(oneSizeLine(saree({ blouseIncluded: false }))).toBe("One size · 5.5 m saree");
    expect(oneSizeLine(lehenga())).toBe("One size");
  });

  it("explains stock", () => {
    const [red, redM] = lehenga().variants;
    expect(stockLine(undefined, "Red")).toEqual({ text: "Choose a size to see availability", tone: "neutral" });
    expect(stockLine(redM, "Red")).toEqual({ text: "Out of stock in Red, size M", tone: "low" });
    expect(stockLine(saree().variants[0], "Rani pink")).toEqual({ text: "Only 2 left in Rani pink", tone: "low" });
    expect(stockLine(red, "Red")).toEqual({ text: "In stock in Red, size S", tone: "ok" });
  });
});
