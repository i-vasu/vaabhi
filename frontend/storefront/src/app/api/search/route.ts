import { searchWithRelated, type SearchOutcome } from "@/lib/related-search";

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

export type SuggestionsResponse = {
  query: string;
  total: number;
  items: SearchSuggestion[];
  /** "related"/"popular" when nothing matched the text exactly (see searchWithRelated). */
  match: SearchOutcome["match"];
  term?: string;
};

/**
 * Same-origin search for the header's search box: the browser asks here as the shopper types, and the storefront
 * asks the API over the internal network. With no exact match it answers with related or popular pieces rather
 * than nothing. Answers are cached briefly, since many shoppers type the same words.
 */
export async function GET(request: Request): Promise<Response> {
  const q = new URL(request.url).searchParams.get("q")?.trim().slice(0, MAX_QUERY) ?? "";
  if (q.length < 2)
    return Response.json({ query: q, total: 0, items: [], match: "exact" } satisfies SuggestionsResponse);
  try {
    const { results, match, term } = await searchWithRelated(q, { pageSize: LIMIT }, { next: { revalidate: 60 } });
    const body: SuggestionsResponse = {
      query: q,
      total: results.total,
      match,
      term,
      items: results.items.map((p) => ({
        slug: p.slug,
        name: p.name,
        subtitle: p.subtitle ?? null,
        priceRupees: p.priceRupees,
        mrpRupees: p.mrpRupees,
        imageKey: p.image?.key ?? null,
      })),
    };
    return Response.json(body, { headers: { "Cache-Control": "public, max-age=30" } });
  } catch {
    return Response.json({ detail: "Search is unavailable right now" }, { status: 502 });
  }
}
