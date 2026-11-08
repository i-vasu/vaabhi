import { api } from "@/lib/api";

/** How many products the search box shows; "See all" opens /search for the rest. */
const LIMIT = 6;
const MAX_QUERY = 100;

export type SearchSuggestion = {
  slug: string;
  name: string;
  subtitle: string | null;
  priceRupees: number;
  mrpRupees: number;
  imageKey: string | null;
};

/**
 * Same-origin search for the header's search box: the browser asks here as the shopper types, and the storefront
 * asks the API over the internal network. Answers are cached briefly, since many shoppers type the same words.
 */
export async function GET(request: Request): Promise<Response> {
  const q = new URL(request.url).searchParams.get("q")?.trim().slice(0, MAX_QUERY) ?? "";
  if (q.length < 2) return Response.json({ query: q, total: 0, items: [] });
  try {
    const results = await api.store.search({ q, pageSize: LIMIT }, { next: { revalidate: 60 } });
    const items: SearchSuggestion[] = results.items.map((p) => ({
      slug: p.slug,
      name: p.name,
      subtitle: p.subtitle ?? null,
      priceRupees: p.priceRupees,
      mrpRupees: p.mrpRupees,
      imageKey: p.image?.key ?? null,
    }));
    return Response.json(
      { query: q, total: results.total, items },
      { headers: { "Cache-Control": "public, max-age=30" } },
    );
  } catch {
    return Response.json({ detail: "Search is unavailable right now" }, { status: 502 });
  }
}
