import type { Metadata } from "next";
import { BagView } from "@/components/bag/bag-view";
import { loadCategoryTiles } from "@/lib/shop-sections";

export const metadata: Metadata = { title: "Your bag", robots: { index: false } };

// Per request, so the layout reads MEDIA_URL at runtime (it is not set when the Docker image is built).
export const dynamic = "force-dynamic";

export default async function BagPage() {
  // Suggested when the bag is empty: the shop's categories that have products.
  const tiles = (await loadCategoryTiles()) ?? [];
  return <BagView categories={tiles.map(({ label, href }) => ({ label, href }))} />;
}
