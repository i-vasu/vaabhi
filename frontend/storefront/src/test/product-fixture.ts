import type { ProductDetail, ProductVariant } from "@vaabhi/api-client";

const variant = (o: Partial<ProductVariant>): ProductVariant => ({
  sku: "VB-RANI",
  colourName: "Rani pink",
  colourHex: "#C2185B",
  size: null,
  priceRupees: 18500,
  mrpRupees: 22000,
  inStock: true,
  stockLeft: null,
  ...o,
});

export const saree = (o: Partial<ProductDetail> = {}): ProductDetail => ({
  slug: "kanjeevaram-silk-saree",
  name: "Kanjeevaram silk saree",
  subtitle: "Temple border, pure zari",
  description: "Woven on a pit loom.\n\nFor weddings.",
  badge: "HANDLOOM",
  category: { slug: "sarees", name: "Sarees" },
  priceRupees: 18500,
  mrpRupees: 22000,
  discountPercent: 16,
  careInstructions: "Dry clean only.",
  sareeLengthM: 5.5,
  blouseIncluded: true,
  blouseLengthM: 0.8,
  attributes: [{ code: "fabric", name: "Fabric", values: [{ code: "silk", name: "Silk" }] }],
  variants: [
    variant({ stockLeft: 2 }),
    variant({ sku: "VB-MUST", colourName: "Mustard", colourHex: "#D4A017", inStock: false }),
  ],
  media: [
    { kind: "IMAGE", key: "products/1/front", alt: "Front", colourName: null, width: 1200, height: 1600 },
    {
      kind: "IMAGE",
      key: "products/1/mustard",
      alt: "Mustard drape",
      colourName: "Mustard",
      width: 1200,
      height: 1600,
    },
  ],
  addons: [
    {
      code: "blouse-unstitched",
      kind: "BLOUSE",
      name: "Unstitched blouse piece",
      description: "Included.",
      priceRupees: 0,
      input: "NONE",
    },
    {
      code: "blouse-standard",
      kind: "BLOUSE",
      name: "Stitched blouse, standard size",
      description: null,
      priceRupees: 1200,
      input: "SIZE",
    },
    {
      code: "blouse-custom",
      kind: "BLOUSE",
      name: "Stitched blouse, made to measure",
      description: null,
      priceRupees: 1800,
      input: "MEASUREMENTS",
    },
  ],
  ...o,
});

export const lehenga = (): ProductDetail =>
  saree({
    slug: "velvet-lehenga",
    name: "Velvet lehenga",
    category: { slug: "lehengas", name: "Lehengas" },
    sareeLengthM: null,
    blouseIncluded: false,
    blouseLengthM: null,
    media: [],
    variants: [
      variant({
        sku: "VB-RED-S",
        colourName: "Red",
        colourHex: "#B3261E",
        size: "S",
        priceRupees: 58000,
        mrpRupees: 65000,
      }),
      variant({
        sku: "VB-RED-M",
        colourName: "Red",
        colourHex: "#B3261E",
        size: "M",
        priceRupees: 58000,
        mrpRupees: 65000,
        inStock: false,
      }),
      variant({
        sku: "VB-WINE-S",
        colourName: "Wine",
        colourHex: "#4E1A24",
        size: "S",
        priceRupees: 58000,
        mrpRupees: 58000,
      }),
    ],
    addons: [
      {
        code: "blouse-custom",
        kind: "BLOUSE",
        name: "Made-to-measure blouse",
        description: null,
        priceRupees: 1500,
        input: "MEASUREMENTS",
      },
    ],
  });
