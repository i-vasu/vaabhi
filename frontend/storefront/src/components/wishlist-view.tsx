"use client";

import Link from "next/link";
import { useEffect, useState, useSyncExternalStore } from "react";
import { buttonClasses, ProductCard } from "@vaabhi/ui";
import type { WishlistItem } from "@/app/api/wishlist/route";
import { CardWishlist } from "@/components/card-wishlist";
import { useMediaBase } from "@/components/media-base";
import { wishlistStore } from "@/lib/bag";
import { discountLabel, formatPrice } from "@/lib/format";
import { mediaUrl, photoSrcSet } from "@/lib/listing";

type Loaded = { state: "loading" } | { state: "error" } | { state: "done"; items: WishlistItem[] };

/**
 * The products saved with the heart (kept in this browser). Tapping a card's heart again removes it here at once;
 * products that were taken off the shop are dropped from the list.
 */
export function WishlistView() {
  const slugs = useSyncExternalStore(wishlistStore.subscribe, wishlistStore.read, wishlistStore.serverValue);
  const [loaded, setLoaded] = useState<Loaded>({ state: "loading" });
  const base = useMediaBase();
  // Ask again only when a saved product is not loaded yet; removing one just hides its card.
  const loadedSlugs = loaded.state === "done" ? loaded.items.map((item) => item.slug) : [];
  const fetchKey = slugs.some((slug) => !loadedSlugs.includes(slug)) ? slugs.join(",") : "";

  useEffect(() => {
    if (!fetchKey) return;
    const controller = new AbortController();
    fetch(`/api/wishlist?slugs=${encodeURIComponent(fetchKey)}`, { signal: controller.signal })
      .then(async (response) => {
        if (!response.ok) throw new Error(String(response.status));
        const body = (await response.json()) as { items: WishlistItem[]; missing: string[] };
        // Products taken off the shop leave the wishlist.
        if (body.missing.length) wishlistStore.write(wishlistStore.read().filter((s) => !body.missing.includes(s)));
        setLoaded({ state: "done", items: body.items });
      })
      .catch(() => {
        if (!controller.signal.aborted) setLoaded({ state: "error" });
      });
    return () => controller.abort();
  }, [fetchKey]);

  if (slugs.length === 0) {
    return (
      <div className="flex flex-col items-center gap-4 py-16 text-center">
        <p className="font-serif text-2xl text-text-primary">Your wishlist is empty</p>
        <p className="max-w-[40ch] text-sm text-text-muted">
          Tap the heart on any saree, lehenga or kurta set to save it here. Saved pieces stay on this device.
        </p>
        <Link href="/new" className={buttonClasses("secondary")}>
          Browse new arrivals
        </Link>
      </div>
    );
  }
  if (loaded.state === "error") {
    return (
      <p className="py-16 text-center text-sm text-feedback-error">
        Your wishlist could not be loaded. Please try again shortly.
      </p>
    );
  }
  if (loaded.state === "loading") {
    return <p className="py-16 text-center text-sm text-text-muted">Loading your wishlist…</p>;
  }

  // In wishlist order (most recently saved last in storage → show newest first).
  const items = [...slugs]
    .reverse()
    .map((slug) => loaded.items.find((item) => item.slug === slug))
    .filter((item): item is WishlistItem => Boolean(item));

  return (
    <>
      <p className="pb-3 text-xs tracking-[0.02em] text-text-muted" aria-live="polite">
        {items.length} {items.length === 1 ? "piece" : "pieces"} saved
      </p>
      <ul aria-label="Wishlist" className="grid grid-cols-2 gap-x-4 gap-y-6 md:grid-cols-3 lg:grid-cols-4">
        {items.map((item) => {
          const src = item.imageKey ? mediaUrl(item.imageKey, 800, base) : undefined;
          return (
            <li key={item.slug}>
              <ProductCard
                name={item.name}
                detail={[item.subtitle, item.inStock ? null : "Out of stock"].filter(Boolean).join(" · ")}
                price={formatPrice(item.priceRupees)}
                mrp={item.mrpRupees > item.priceRupees ? formatPrice(item.mrpRupees) : undefined}
                discount={discountLabel(item.priceRupees, item.mrpRupees)}
                href={`/p/${item.slug}`}
                image={
                  src && item.imageKey
                    ? {
                        src,
                        alt: item.imageAlt,
                        srcSet: photoSrcSet(item.imageKey, base),
                        sizes: "(min-width: 1024px) 25vw, (min-width: 768px) 33vw, 50vw",
                      }
                    : null
                }
                wishlist={<CardWishlist slug={item.slug} name={item.name} />}
              />
            </li>
          );
        })}
      </ul>
    </>
  );
}
