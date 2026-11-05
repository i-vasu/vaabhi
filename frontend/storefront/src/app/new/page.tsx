import type { Metadata } from "next";
import type { NewArrivalsPage } from "@vaabhi/api-client";
import { CollectionView } from "@/components/listing/collection-view";
import { api } from "@/lib/api";
import { MAX_PAGES, type SearchParams } from "@/lib/listing";

export const dynamic = "force-dynamic";

export const metadata: Metadata = { title: "New arrivals | Vaabhi", alternates: { canonical: "/new" } };

const PAGE_SIZE = 24;

function pageOf(params: SearchParams): number {
  const raw = Array.isArray(params.page) ? params.page[0] : params.page;
  const page = Number(raw);
  return Number.isInteger(page) && page >= 1 ? Math.min(page, MAX_PAGES) : 1;
}

/** Loads pages 1..n in parallel, so "load more" is a plain link that also works without JavaScript. */
async function load(page: number): Promise<NewArrivalsPage | null> {
  try {
    const pages = await Promise.all(
      Array.from({ length: page }, (_, i) =>
        api.store.listNewArrivals({ page: i + 1, pageSize: PAGE_SIZE }, { next: { revalidate: 60 } }),
      ),
    );
    return { ...pages[0]!, items: pages.flatMap((p) => p.items) };
  } catch {
    return null;
  }
}

/** Every published product, most recently published first. */
export default async function NewArrivalsPage({ searchParams }: Readonly<{ searchParams: Promise<SearchParams> }>) {
  const page = pageOf(await searchParams);
  const result = await load(page);
  const hasMore = result !== null && result.items.length < result.total && page < MAX_PAGES;
  return (
    <CollectionView
      title="New arrivals"
      intro="Just in, newest first"
      products={result?.items ?? null}
      total={result?.total}
      more={hasMore ? `/new?page=${page + 1}` : undefined}
      empty="New styles are on their way"
    />
  );
}
