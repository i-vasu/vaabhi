import type { AdminProduct, CatalogOptions } from "@vaabhi/api-client";

export const options: CatalogOptions = {
  categories: [
    { id: 1, slug: "sarees", name: "Sarees", description: null },
    { id: 2, slug: "lehengas", name: "Lehengas", description: null },
  ],
  facets: [
    {
      code: "fabric",
      name: "Fabric",
      values: [
        { code: "silk", name: "Silk" },
        { code: "cotton", name: "Cotton" },
      ],
    },
  ],
  colourFamilies: [
    { code: "red", name: "Red", hex: "#B3261E" },
    { code: "green", name: "Green", hex: "#3F7D4E" },
  ],
  sizes: ["S", "M", "L"],
};

export const product = (overrides: Partial<AdminProduct> = {}): AdminProduct => ({
  id: 7,
  slug: "ruby-silk-saree",
  name: "Ruby silk saree",
  subtitle: "Zari border",
  description: null,
  categorySlug: "sarees",
  status: "DRAFT",
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
  media: [],
  publishedAt: null,
  updatedAt: "2026-10-31T09:00:00Z",
  ...overrides,
});
