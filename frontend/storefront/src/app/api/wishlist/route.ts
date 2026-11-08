import { ApiError, type ProductDetail } from "@vaabhi/api-client";
import { api } from "@/lib/api";

/** Most products one request looks up; the wishlist page asks in one go. */
const MAX = 60;
const SLUG = /^[a-z0-9]+(-[a-z0-9]+)*$/;

export type WishlistItem = {
  slug: string;
  name: string;
  subtitle: string | null;
  priceRupees: number;
  mrpRupees: number;
  imageKey: string | null;
  imageAlt: string;
  inStock: boolean;
};

function item(product: ProductDetail): WishlistItem {
  const image = product.media.find((m) => m.kind === "IMAGE");
  return {
    slug: product.slug,
    name: product.name,
    subtitle: product.subtitle,
    priceRupees: product.priceRupees,
    mrpRupees: product.mrpRupees,
    imageKey: image?.key ?? null,
    imageAlt: image?.alt ?? product.name,
    inStock: product.variants.some((v) => v.inStock),
  };
}

/**
 * The wishlist kept in the browser is a list of product slugs; this looks them up for the wishlist page.
 * `missing` lists products that no longer exist or are unpublished, so the page can drop them.
 */
export async function GET(request: Request): Promise<Response> {
  const slugs = [
    ...new Set(
      (new URL(request.url).searchParams.get("slugs") ?? "")
        .split(",")
        .map((s) => s.trim())
        .filter((s) => SLUG.test(s)),
    ),
  ].slice(0, MAX);
  const results = await Promise.allSettled(
    slugs.map((slug) => api.store.getProduct(slug, { next: { revalidate: 60 } })),
  );
  const items: WishlistItem[] = [];
  const missing: string[] = [];
  let failed = false;
  results.forEach((result, i) => {
    if (result.status === "fulfilled") items.push(item(result.value));
    else if (result.reason instanceof ApiError && result.reason.status === 404) missing.push(slugs[i]!);
    else failed = true;
  });
  if (failed && items.length === 0) {
    return Response.json({ detail: "Your wishlist could not be loaded" }, { status: 502 });
  }
  return Response.json({ items, missing }, { headers: { "Cache-Control": "no-store" } });
}
