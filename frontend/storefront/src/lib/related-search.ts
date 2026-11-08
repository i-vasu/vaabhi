import type { ListingParams, SearchResults } from "@vaabhi/api-client";
import { api } from "@/lib/api";

/** What a search found, and how: an exact match, a looser match on part of the words, or popular pieces. */
export type SearchOutcome = {
  results: SearchResults;
  /** "exact": matched the whole text; "related": matched `term`; "popular": nothing matched, so popular pieces. */
  match: "exact" | "related" | "popular";
  /** The word or word start that found the related results. */
  term?: string;
};

type Init = { next?: { revalidate?: number } };

const WORD = /[\p{L}\p{N}]+/gu;
const MIN = 3;

/**
 * Searches for `q`; when nothing matches, looks for related pieces instead of an empty page:
 * 1. each word on its own, longest first ("red banarasi saree" → "banarasi"),
 * 2. each word's start, for spelling slips ("sari" → "sar", "lehnga" → "leh"),
 * 3. otherwise the shop's popular pieces.
 * The filters in `params` apply to every try.
 */
export async function searchWithRelated(q: string, params: ListingParams = {}, init?: Init): Promise<SearchOutcome> {
  const exact = await api.store.search({ ...params, q }, init);
  if (exact.total > 0 || !q.trim()) return { results: exact, match: "exact" };

  const words = [...new Set(q.toLowerCase().match(WORD) ?? [])]
    .filter((w) => w.length >= MIN)
    .sort((a, b) => b.length - a.length);
  const tries = [
    ...(words.length > 1 ? words : []),
    ...words.map((w) => w.slice(0, Math.max(MIN, Math.ceil(w.length / 2)))).filter((p) => !words.includes(p)),
    ...words.map((w) => w.slice(0, MIN)),
  ];
  for (const term of [...new Set(tries)]) {
    const results = await api.store.search({ ...params, q: term }, init);
    if (results.total > 0) return { results, match: "related", term };
  }

  const popular = await api.store.search({ ...params, sort: "popular" }, init);
  return { results: popular, match: "popular" };
}
