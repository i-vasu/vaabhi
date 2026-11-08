import type { MetadataRoute } from "next";
import { api } from "@/lib/api";
import { CUSTOMISE_PAGE, pageHref } from "@/lib/site-links";
import { siteUrl } from "@/lib/site";

// Built on request from the API (builds have no API); the API answers are cached for an hour.
export const dynamic = "force-dynamic";

const cached = { next: { revalidate: 3600 } };
const PAGE_SIZE = 48;
/** Stops a runaway loop; 48 × 50 products is far beyond the catalogue. */
const MAX_PAGES = 50;

async function productSlugs(): Promise<string[]> {
  const slugs: string[] = [];
  for (let page = 1; page <= MAX_PAGES; page++) {
    const result = await api.store.listNewArrivals({ page, pageSize: PAGE_SIZE }, cached);
    slugs.push(...result.items.map((p) => p.slug));
    if (page * PAGE_SIZE >= result.total || result.items.length === 0) break;
  }
  return slugs;
}

async function safely<T>(load: () => Promise<T[]>): Promise<T[]> {
  try {
    return await load();
  } catch {
    return [];
  }
}

/** Every page worth indexing: home, listings, categories, published website pages and published products. */
export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const base = siteUrl();
  const [categories, pages, products] = await Promise.all([
    safely(() => api.store.listCategories(cached)),
    safely(() => api.store.listPages(cached)),
    safely(productSlugs),
  ]);
  const url = (path: string) => `${base}${path}`;
  return [
    { url: url("/"), changeFrequency: "daily", priority: 1 },
    { url: url("/new"), changeFrequency: "daily", priority: 0.8 },
    { url: url("/bestsellers"), changeFrequency: "weekly", priority: 0.8 },
    { url: url("/collections"), changeFrequency: "weekly", priority: 0.8 },
    { url: url("/customise"), changeFrequency: "monthly", priority: 0.5 },
    { url: url("/blog"), changeFrequency: "weekly", priority: 0.5 },
    { url: url("/contact"), changeFrequency: "monthly", priority: 0.4 },
    ...categories.map((c) => ({ url: url(`/c/${c.slug}`), changeFrequency: "daily" as const, priority: 0.9 })),
    ...products.map((slug) => ({ url: url(`/p/${slug}`), changeFrequency: "weekly" as const, priority: 0.7 })),
    // The customise page is already listed above as /customise.
    ...pages
      .filter((p) => p.slug !== CUSTOMISE_PAGE)
      .map((p) => ({ url: url(pageHref(p)), changeFrequency: "monthly" as const, priority: 0.3 })),
  ];
}
