import type { Metadata } from "next";
import type { ProductSummary } from "@vaabhi/api-client";
import { CollectionView } from "@/components/listing/collection-view";
import { api } from "@/lib/api";

export const dynamic = "force-dynamic";

export const metadata: Metadata = { title: "Bestsellers | Vaabhi", alternates: { canonical: "/bestsellers" } };

/** The most the API returns for one badge. */
const LIMIT = 24;

async function load(): Promise<ProductSummary[] | null> {
  try {
    return await api.store.listFeatured({ badge: "BESTSELLER", limit: LIMIT }, { next: { revalidate: 60 } });
  } catch {
    return null;
  }
}

/** Products marked Bestseller in the admin, most popular first. */
export default async function BestsellersPage() {
  return (
    <CollectionView
      title="Bestsellers"
      intro="Our most-loved styles"
      products={await load()}
      empty="Bestsellers are coming soon"
    />
  );
}
