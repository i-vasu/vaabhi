import type { FilterGroup, ProductListing, ProductSummary } from "@vaabhi/api-client";

export const product = (overrides: Partial<ProductSummary> = {}): ProductSummary => ({
  slug: "kanjeevaram-silk-saree",
  name: "Kanjeevaram silk saree",
  subtitle: "Temple border, pure zari",
  badge: "HANDLOOM",
  pricePaise: 1850000,
  mrpPaise: 2200000,
  discountPercent: 16,
  colours: [{ name: "Rani pink", hex: "#C2185B" }],
  inStock: true,
  stockLeft: null,
  image: null,
  ...overrides,
});

export const filters: FilterGroup[] = [
  {
    code: "colour",
    name: "Colour",
    type: "COLOUR",
    options: [
      { code: "maroon", name: "Maroon", hex: "#6E2A35", count: 2, selected: false },
      { code: "gold", name: "Gold", hex: "#A88B5A", count: 0, selected: false },
    ],
  },
  {
    code: "fabric",
    name: "Fabric",
    type: "FACET",
    options: [
      { code: "silk", name: "Silk", hex: null, count: 4, selected: true },
      { code: "cotton", name: "Cotton", hex: null, count: 2, selected: false },
      { code: "linen", name: "Linen", hex: null, count: 0, selected: false },
    ],
  },
  {
    code: "occasion",
    name: "Occasion",
    type: "FACET",
    options: [{ code: "wedding", name: "Wedding", hex: null, count: 3, selected: false }],
  },
];

export const listing = (overrides: Partial<ProductListing> = {}): ProductListing => ({
  category: { id: 1, slug: "sarees", name: "Sarees", description: "Handloom silks and cottons." },
  items: [product()],
  total: 1,
  page: 1,
  pageSize: 24,
  sort: "popular",
  filters,
  priceRange: { minPaise: 160000, maxPaise: 2490000 },
  ...overrides,
});
