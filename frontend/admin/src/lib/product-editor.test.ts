import { describe, expect, it } from "vitest";
import { product } from "@/test/admin-fixtures";
import {
  emptyState,
  errorsFor,
  formFromState,
  standardBlouseAddons,
  stateFromProduct,
  toPaise,
  variantsForColour,
} from "./product-editor";

describe("product editor state", () => {
  it("starts sarees with the usual lengths and a blouse", () => {
    expect(emptyState("sarees")).toMatchObject({ sareeLengthM: "5.5", blouseIncluded: true, blouseLengthM: "0.8" });
    expect(emptyState()).toMatchObject({ categorySlug: "", sareeLengthM: "", blouseIncluded: false });
  });

  it("round-trips a product through the form", () => {
    const form = formFromState(stateFromProduct(product()));
    expect(form).toEqual({
      name: "Ruby silk saree",
      slug: "ruby-silk-saree",
      subtitle: "Zari border",
      description: null,
      categorySlug: "sarees",
      badge: "NEW",
      careInstructions: null,
      sareeLengthM: 5.5,
      blouseIncluded: true,
      blouseLengthM: 0.8,
      attributes: { fabric: ["silk"] },
      variants: [
        {
          sku: "VB-ABC123",
          colourName: "Ruby",
          colourHex: "#B3261E",
          colourFamily: "red",
          size: null,
          pricePaise: 1425000,
          mrpPaise: 1780000,
          stockQty: 4,
          active: true,
        },
      ],
      addons: [
        {
          code: "blouse-custom",
          kind: "BLOUSE",
          name: "Made to measure",
          description: null,
          pricePaise: 180000,
          input: "MEASUREMENTS",
          active: true,
        },
      ],
    });
  });

  it("drops saree fields for other categories, empty attributes and blank values", () => {
    const state = {
      ...stateFromProduct(product({ sareeLengthM: null, blouseLengthM: null, badge: null })),
      categorySlug: "lehengas",
      attributes: { fabric: [], occasion: ["festive"] },
      variants: [{ ...stateFromProduct(product()).variants[0]!, sku: "  ", mrp: "", price: "1,250.5", stock: "x" }],
    };
    const form = formFromState(state);
    expect(form).toMatchObject({ sareeLengthM: null, blouseIncluded: false, blouseLengthM: null, badge: null });
    expect(form.attributes).toEqual({ occasion: ["festive"] });
    expect(form.variants[0]).toMatchObject({ sku: null, pricePaise: 125050, mrpPaise: 125050, stockQty: 0 });
    expect(stateFromProduct(product({ sareeLengthM: null })).sareeLengthM).toBe("");
  });

  it("parses rupees", () => {
    expect(toPaise("12,499")).toBe(1249900);
    expect(toPaise(" 0.5 ")).toBe(50);
    expect(toPaise("abc")).toBe(0);
  });

  it("builds variant rows per size and standard blouse options", () => {
    const colour = { colourName: "Mint", colourHex: "#BFE3D0", colourFamily: "green", price: "100", mrp: "120" };
    expect(variantsForColour(colour, ["M", "L"]).map((v) => v.size)).toEqual(["M", "L"]);
    expect(variantsForColour(colour, [])).toHaveLength(1);
    expect(standardBlouseAddons().map((a) => [a.code, a.price, a.input])).toEqual([
      ["blouse-unstitched", "0", "NONE"],
      ["blouse-standard", "1200", "SIZE"],
      ["blouse-custom", "1800", "MEASUREMENTS"],
    ]);
  });

  it("finds errors for a field and its children", () => {
    const errors = [
      { field: "variants[1].mrpPaise", message: "MRP too low" },
      { field: "variants[10]", message: "duplicate" },
      { field: "name", message: "required" },
    ];
    expect(errorsFor(errors, "variants[1]")).toEqual(["MRP too low"]);
    expect(errorsFor(errors, "name")).toEqual(["required"]);
  });
});
