import { afterEach, describe, expect, it, vi } from "vitest";
import { listing, product } from "@/test/listing-fixture";
import {
  activeFilterCount,
  apiParams,
  appliedFilters,
  clearFilters,
  listingHref,
  mediaUrl,
  parseListingState,
  priceLabel,
  productCardProps,
  shade,
  toggleValue,
  withInStock,
  withPage,
  withPrice,
  withSort,
} from "./listing";

describe("parseListingState", () => {
  it("reads filters, sort and page from the URL", () => {
    const state = parseListingState({
      fabric: "silk, cotton",
      colour: ["red", "maroon"],
      inStock: "true",
      priceMin: "2000",
      priceMax: "5000",
      sort: "price_asc",
      page: "3",
    });
    expect(state).toEqual({
      selected: { fabric: ["silk", "cotton"], colour: ["red", "maroon"] },
      inStock: true,
      priceMin: 2000,
      priceMax: 5000,
      sort: "price_asc",
      page: 3,
    });
  });

  it("ignores junk and falls back to defaults", () => {
    const state = parseListingState({
      "bad key!": "x",
      fabric: "<script>,silk",
      empty: "",
      sort: "cheapest",
      page: "999",
      priceMin: "-5",
      priceMax: "abc",
      inStock: "yes",
      skipped: undefined,
    });
    expect(state).toEqual({ selected: { fabric: ["silk"] }, inStock: false, sort: "popular", page: 10 });
  });

  it("swaps an inverted price range", () => {
    expect(parseListingState({ priceMin: "9000", priceMax: "1000" })).toMatchObject({ priceMin: 1000, priceMax: 9000 });
  });
});

describe("listingHref", () => {
  const base = parseListingState({});

  it("omits defaults", () => {
    expect(listingHref("/c/sarees", base)).toBe("/c/sarees");
  });

  it("is canonical: sorted keys and values", () => {
    const state = { ...base, selected: { occasion: ["wedding"], fabric: ["silk", "cotton"] }, inStock: true, page: 2 };
    expect(listingHref("/c/sarees", withSort(state, "new"))).toBe(
      "/c/sarees?fabric=cotton%2Csilk&occasion=wedding&inStock=true&sort=new",
    );
    expect(listingHref("/c/sarees", withPage(withPrice(base, 2000, 5000), 2))).toBe(
      "/c/sarees?priceMin=2000&priceMax=5000&page=2",
    );
  });
});

describe("state changes", () => {
  const base = { ...parseListingState({ fabric: "silk", page: "4" }) };

  it("toggles values and resets to the first page", () => {
    const added = toggleValue(base, "fabric", "cotton");
    expect(added.selected.fabric).toEqual(["silk", "cotton"]);
    expect(added.page).toBe(1);
    expect(toggleValue(base, "fabric", "silk").selected).toEqual({});
  });

  it("clears filters but keeps the sort", () => {
    const state = withInStock(withPrice(withSort(base, "discount"), 1, 2), true);
    expect(activeFilterCount(state)).toBe(3);
    const cleared = clearFilters(state);
    expect(cleared).toMatchObject({ selected: {}, inStock: false, sort: "discount" });
    expect(activeFilterCount(cleared)).toBe(0);
  });

  it("caps pages", () => {
    expect(withPage(base, 50).page).toBe(10);
  });
});

describe("apiParams", () => {
  it("converts rupees to paise and joins codes", () => {
    const state = withInStock(withPrice(parseListingState({ fabric: "silk,cotton" }), 2000, 5000), true);
    expect(apiParams(state, 2)).toEqual({
      sort: "popular",
      page: 2,
      pageSize: 24,
      inStock: true,
      fabric: "silk,cotton",
      priceMin: 200000,
      priceMax: 500000,
    });
  });
});

describe("priceLabel", () => {
  it.each([
    [undefined, 2000, "Under ₹2,000"],
    [2000, 5000, "₹2,000 – ₹5,000"],
    [20000, undefined, "Above ₹20,000"],
    [1500, 2500, "₹1,500 – ₹2,500"],
    [undefined, 3000, "Under ₹3,000"],
    [3000, undefined, "Above ₹3,000"],
    [undefined, undefined, undefined],
  ])("labels %s–%s", (min, max, expected) => {
    expect(priceLabel(min, max)).toBe(expected);
  });
});

describe("appliedFilters", () => {
  it("labels chips with API names and builds removal states", () => {
    const state = withInStock(withPrice(parseListingState({ fabric: "silk,unknown" }), undefined, 2000), true);
    const chips = appliedFilters(state, listing());
    expect(chips.map((c) => c.label)).toEqual(["Silk", "Under ₹2,000", "In stock"]);
    expect(chips[0]!.remove.selected).toEqual({ fabric: ["unknown"] });
    expect(chips[1]!.remove.priceMax).toBeUndefined();
    expect(chips[2]!.remove.inStock).toBe(false);
  });
});

describe("product cards", () => {
  afterEach(() => vi.unstubAllEnvs());

  it("darkens colours for the placeholder", () => {
    expect(shade("#C2185B")).toBe("#6B0D32");
  });

  it("maps a product to card props", () => {
    expect(productCardProps(product())).toEqual({
      name: "Kanjeevaram silk saree",
      detail: "Temple border, pure zari",
      price: "₹18,500",
      mrp: "₹22,000",
      discount: "16% off",
      href: "/p/kanjeevaram-silk-saree",
      badge: { label: "handloom", tone: "olive" },
      image: { placeholder: ["#C2185B", "#6B0D32"] },
    });
  });

  it("shows stock state and handles missing data", () => {
    const low = productCardProps(product({ stockLeft: 2, badge: "NEW", mrpPaise: 1850000 }));
    expect(low.detail).toBe("Temple border, pure zari · Only 2 left");
    expect(low.mrp).toBeUndefined();
    expect(low.badge?.tone).toBe("festive");
    const out = productCardProps(product({ inStock: false, subtitle: null, colours: [], badge: null }));
    expect(out.detail).toBe("Out of stock");
    expect(out.badge).toBeUndefined();
    expect(out.image).toEqual({ placeholder: ["#E4DCD0", "#A88B5A"] });
  });

  it("uses the R2 photo when the media URL is configured", () => {
    const image = {
      kind: "IMAGE" as const,
      key: "products/k/1",
      alt: "Saree",
      colourName: null,
      width: 1600,
      height: 2133,
    };
    expect(productCardProps(product({ image })).image).toHaveProperty("placeholder");
    vi.stubEnv("NEXT_PUBLIC_MEDIA_URL", "https://media.vaabhi.com/");
    expect(mediaUrl("products/k/1", 400)).toBe("https://media.vaabhi.com/products/k/1/400.webp");
    expect(productCardProps(product({ image })).image).toEqual({
      src: "https://media.vaabhi.com/products/k/1/800.webp",
      alt: "Saree",
      srcSet:
        "https://media.vaabhi.com/products/k/1/400.webp 400w, https://media.vaabhi.com/products/k/1/800.webp 800w, https://media.vaabhi.com/products/k/1/1600.webp 1600w",
      sizes: "(min-width: 1024px) 25vw, (min-width: 768px) 33vw, 50vw",
    });
    vi.stubEnv("MEDIA_URL", "https://cdn.example");
    expect(mediaUrl("k")).toBe("https://cdn.example/k/800.webp");
  });
});
