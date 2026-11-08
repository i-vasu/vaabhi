import { api } from "@/lib/api";

/**
 * "Shop by category" and "Shop by occasion", worked out from the products in the shop: only categories that have
 * products and occasions that products are tagged with, each pictured with a real photo (the category's own, else
 * its first product's). Nothing here is made up, so a shop with no products shows neither section.
 */
export type ShopTile = {
  label: string;
  href: string;
  /** Number of products behind the tile. */
  count: number;
  /** R2 key of the photo; null when neither the category nor its products have one yet. */
  imageKey: string | null;
};

const cached = { next: { revalidate: 60 } };
/** The facet that holds occasions (Wedding, Festive…), as set on products in the dashboard. */
const OCCASION = "occasion";

/** Categories with at least one published product, in the shop's order; null when the catalogue is down. */
export async function loadCategoryTiles(): Promise<ShopTile[] | null> {
  try {
    const categories = await api.store.listCategories(cached);
    const firsts = await Promise.all(
      categories.map((c) => api.store.listProducts(c.slug, { pageSize: 1 }, cached).catch(() => null)),
    );
    return categories.flatMap((category, i) => {
      const listing = firsts[i];
      if (!listing || listing.total === 0) return [];
      return [
        {
          label: category.name,
          href: `/c/${category.slug}`,
          count: listing.total,
          imageKey: category.imageKey ?? listing.items[0]?.image?.key ?? null,
        },
      ];
    });
  } catch {
    return null;
  }
}

/** Occasions products are tagged with, most products first; each opens that occasion across every category. */
export async function loadOccasionTiles(): Promise<ShopTile[]> {
  try {
    const all = await api.store.search({ pageSize: 1 }, cached);
    const occasions = (all.filters.find((g) => g.code === OCCASION)?.options ?? [])
      .filter((o) => o.count > 0)
      .sort((a, b) => b.count - a.count);
    const firsts = await Promise.all(
      occasions.map((o) => api.store.search({ [OCCASION]: o.code, pageSize: 1 }, cached).catch(() => null)),
    );
    return occasions.map((occasion, i) => ({
      label: occasion.name,
      href: `/search?${OCCASION}=${encodeURIComponent(occasion.code)}`,
      count: occasion.count,
      imageKey: firsts[i]?.items[0]?.image?.key ?? null,
    }));
  } catch {
    return [];
  }
}
