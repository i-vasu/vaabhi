import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ApiError, type ProductListing } from "@vaabhi/api-client";
import { buttonClasses, chipClasses, CloseIcon, ProductCard } from "@vaabhi/ui";
import { ListingToolbar } from "@/components/listing/listing-toolbar";
import { api } from "@/lib/api";
import {
  appliedFilters,
  apiParams,
  clearFilters,
  listingHref,
  MAX_PAGES,
  parseListingState,
  productCardProps,
  toggleValue,
  withInStock,
  withPage,
  withPrice,
  type ListingState,
  type SearchParams,
} from "@/lib/listing";

export const dynamic = "force-dynamic";

type Props = { params: Promise<{ slug: string }>; searchParams: Promise<SearchParams> };

const container = "mx-auto w-full max-w-6xl px-4";

/** Loads pages 1..n in parallel, so "load more" is a plain link that also works without JavaScript. */
async function loadListing(slug: string, state: ListingState): Promise<ProductListing | null> {
  try {
    const pages = await Promise.all(
      Array.from({ length: state.page }, (_, i) =>
        api.store.listProducts(slug, apiParams(state, i + 1), { next: { revalidate: 60 } }),
      ),
    );
    const first = pages[0]!;
    return { ...first, items: pages.flatMap((p) => p.items) };
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) notFound();
    return null;
  }
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { slug } = await params;
  const name = slug.replaceAll("-", " ").replace(/^\w/, (c) => c.toUpperCase());
  return { title: `${name} | Vaabhi`, alternates: { canonical: `/c/${slug}` } };
}

export default async function CategoryPage({ params, searchParams }: Readonly<Props>) {
  const { slug } = await params;
  const state = parseListingState(await searchParams);
  const pathname = `/c/${slug}`;
  const listing = await loadListing(slug, state);

  if (listing === null) {
    return (
      <div className={`${container} py-16 text-center`}>
        <p className="text-sm text-feedback-error">The catalogue is unavailable right now. Please try again shortly.</p>
      </div>
    );
  }

  const { category, total, items } = listing;
  const applied = appliedFilters(state, listing);
  const href = (next: ListingState) => listingHref(pathname, next);

  return (
    <>
      <div className={`${container} flex flex-col gap-1.5 pt-4 pb-1`}>
        <nav aria-label="Breadcrumb">
          <ol className="flex gap-1.5 text-xs text-text-muted">
            <li>
              <Link href="/" className="hover:underline">
                Home
              </Link>
            </li>
            <li aria-hidden="true">/</li>
            <li aria-current="page" className="text-text-primary">
              {category.name}
            </li>
          </ol>
        </nav>
        <h1 className="font-serif text-[32px] font-semibold leading-10 text-text-primary">{category.name}</h1>
        {category.description && (
          <p className="text-sm text-text-muted">
            {category.description.replace(/\.$/, "")} — {total} {total === 1 ? "style" : "styles"}
          </p>
        )}
      </div>

      <QuickFilters state={state} listing={listing} href={href} />

      <ListingToolbar state={state} groups={listing.filters} total={total} />

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
          {total} {total === 1 ? "result" : "results"}
        </p>

        {items.length === 0 ? (
          <div className="flex flex-col items-center gap-4 py-16 text-center">
            <p className="font-serif text-2xl text-text-primary">Nothing matches these filters</p>
            <Link href={href(clearFilters(state))} className={buttonClasses("secondary")}>
              Clear filters
            </Link>
          </div>
        ) : (
          <ul
            aria-label={`${category.name} products`}
            className="grid grid-cols-2 gap-x-4 gap-y-6 md:grid-cols-3 lg:grid-cols-4"
          >
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
            <div className="h-0.5 w-40 bg-border-default" aria-hidden="true">
              <div className="h-full bg-brand-primary" style={{ width: `${(items.length / total) * 100}%` }} />
            </div>
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

/** Shortcut chips: All, the two most common fabrics, ready to ship and an under-₹5,000 band. */
function QuickFilters({
  state,
  listing,
  href,
}: Readonly<{
  state: ListingState;
  listing: ProductListing;
  href: (state: ListingState) => string;
}>) {
  const fabrics = (listing.filters.find((g) => g.code === "fabric")?.options ?? [])
    .filter((o) => o.count > 0 || o.selected)
    .sort((a, b) => b.count - a.count)
    .slice(0, 2);
  const chips = [
    {
      label: "All",
      active:
        Object.keys(state.selected).length === 0 &&
        !state.inStock &&
        state.priceMax === undefined &&
        state.priceMin === undefined,
      next: clearFilters(state),
    },
    ...fabrics.map((o) => ({ label: o.name, active: o.selected, next: toggleValue(state, "fabric", o.code) })),
    { label: "Ready to ship", active: state.inStock, next: withInStock(state, !state.inStock) },
    {
      label: "Under ₹5,000",
      active: state.priceMin === undefined && state.priceMax === 5000,
      next:
        state.priceMax === 5000 && state.priceMin === undefined ? withPrice(state) : withPrice(state, undefined, 5000),
    },
  ];
  return (
    <nav aria-label="Quick filters" className="overflow-x-auto">
      <ul className={`${container} flex gap-2 pt-3 pb-4`}>
        {chips.map((chip) => (
          <li key={chip.label}>
            <Link
              href={href(chip.next)}
              scroll={false}
              aria-current={chip.active ? "true" : undefined}
              className={chipClasses(chip.active ? "selected" : "default")}
            >
              {chip.label}
            </Link>
          </li>
        ))}
      </ul>
    </nav>
  );
}
