import type { Metadata } from "next";
import Link from "next/link";
import type { SearchResults } from "@vaabhi/api-client";
import { buttonClasses, chipClasses, CloseIcon, ProductCard } from "@vaabhi/ui";
import { ListingToolbar } from "@/components/listing/listing-toolbar";
import { api } from "@/lib/api";
import { searchWithRelated, type SearchOutcome } from "@/lib/related-search";
import {
  appliedFilters,
  apiParams,
  clearFilters,
  listingHref,
  MAX_PAGES,
  parseListingState,
  productCardProps,
  withPage,
  type ListingState,
  type SearchParams,
} from "@/lib/listing";

export const dynamic = "force-dynamic";

type Props = { searchParams: Promise<SearchParams> };

const container = "mx-auto w-full max-w-6xl px-4";
const PATH = "/search";

export async function generateMetadata({ searchParams }: Props): Promise<Metadata> {
  const { q } = parseListingState(await searchParams);
  // Result pages change with the stock; only the plain page is worth indexing.
  return { title: q ? `“${q}”` : "Search", robots: q ? { index: false } : undefined, alternates: { canonical: PATH } };
}

const cached = { next: { revalidate: 60 } };

/**
 * Page 1 decides how the text matched (exactly, a related word, or nothing: popular pieces); pages 2..n follow the
 * same match, so "load more" is a plain link that also works without JavaScript.
 */
async function loadResults(state: ListingState): Promise<SearchOutcome | null> {
  try {
    const first = await searchWithRelated(state.q ?? "", apiParams({ ...state, q: undefined }, 1), cached);
    const q = first.match === "exact" ? state.q : first.match === "related" ? first.term : undefined;
    const rest = await Promise.all(
      Array.from({ length: state.page - 1 }, (_, i) => api.store.search(apiParams({ ...state, q }, i + 2), cached)),
    );
    return { ...first, results: { ...first.results, items: [first.results, ...rest].flatMap((p) => p.items) } };
  } catch {
    return null;
  }
}

/**
 * Search the whole shop by name, weave, category or occasion. Without text it lists everything, so a link such as
 * /search?occasion=wedding is one occasion across every category.
 */
export default async function SearchPage({ searchParams }: Readonly<Props>) {
  const state = parseListingState(await searchParams);
  const outcome = await loadResults(state);
  const href = (next: ListingState) => listingHref(PATH, next);
  const filtered = Object.keys(state.selected).length > 0 || state.inStock || state.priceMin || state.priceMax;
  const title = state.q ? `Results for “${state.q}”` : filtered ? "Shop the edit" : "Search";

  return (
    <>
      <h1 className="sr-only">{title}</h1>

      {outcome === null ? (
        <div className={`${container} py-16 text-center`}>
          <p className="text-sm text-feedback-error">Search is unavailable right now. Please try again shortly.</p>
        </div>
      ) : (
        <>
          {outcome.match !== "exact" && state.q && (
            <p className={`${container} pt-2 text-sm text-text-secondary`}>
              No exact match for “{state.q}”.{" "}
              {outcome.match === "related"
                ? `Showing pieces related to “${outcome.term}”.`
                : "Here are some of our most-loved pieces."}
            </p>
          )}
          <SearchResultsView state={state} results={outcome.results} href={href} />
        </>
      )}
    </>
  );
}

function SearchResultsView({
  state,
  results,
  href,
}: Readonly<{ state: ListingState; results: SearchResults; href: (state: ListingState) => string }>) {
  const { total, items } = results;
  const applied = appliedFilters(state, results);

  return (
    <>
      <ListingToolbar state={state} groups={results.filters} total={total} />
      <div className={container}>
        {applied.length > 0 && (
          <ul aria-label="Applied filters" className="flex flex-wrap items-center gap-2 pt-3">
            {applied.map((chip) => (
              <li key={chip.key}>
                <Link
                  href={href(chip.remove)}
                  scroll={false}
                  className={chipClasses("removable")}
                  aria-label={`Remove ${chip.label}`}
                >
                  {chip.label}
                  <CloseIcon size={14} />
                </Link>
              </li>
            ))}
            <li>
              <Link href={href(clearFilters(state))} scroll={false} className="text-sm text-brand-primary underline">
                Clear all
              </Link>
            </li>
          </ul>
        )}
        <p className="py-3 text-xs tracking-[0.02em] text-text-muted" aria-live="polite">
          {total} {total === 1 ? "result" : "results"} · prices include all taxes
        </p>

        {items.length === 0 ? (
          <div className="flex flex-col items-center gap-4 py-16 text-center">
            <p className="font-serif text-2xl text-text-primary">
              {state.q ? `Nothing matches “${state.q}”` : "Nothing matches these filters"}
            </p>
            <p className="text-sm text-text-muted">Try another word, or browse a category.</p>
            <div className="flex flex-wrap justify-center gap-3">
              {applied.length > 0 && (
                <Link href={href(clearFilters(state))} className={buttonClasses("secondary")}>
                  Clear filters
                </Link>
              )}
              <Link href="/new" className={buttonClasses("secondary")}>
                New arrivals
              </Link>
            </div>
          </div>
        ) : (
          <ul aria-label="Results" className="grid grid-cols-2 gap-x-4 gap-y-6 md:grid-cols-3 lg:grid-cols-4">
            {items.map((product) => (
              <li key={product.slug}>
                <ProductCard {...productCardProps(product)} />
              </li>
            ))}
          </ul>
        )}

        {items.length > 0 && (
          <div className="flex flex-col items-center gap-2.5 px-4 py-8">
            <p className="text-xs tracking-[0.02em] text-text-muted">
              Showing {items.length} of {total}
            </p>
            {items.length < total && state.page < MAX_PAGES && (
              <Link href={href(withPage(state, state.page + 1))} scroll={false} className={buttonClasses("secondary")}>
                Load more
              </Link>
            )}
          </div>
        )}
      </div>
    </>
  );
}
