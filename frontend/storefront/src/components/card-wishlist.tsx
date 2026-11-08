"use client";

import { useSyncExternalStore } from "react";
import { WishlistHeart } from "@vaabhi/ui";
import { toggleWishlist, wishlistStore } from "@/lib/bag";

/** A product card's heart: saves the product to the wishlist kept in this browser, like the product page's heart. */
export function CardWishlist({ slug, name }: Readonly<{ slug: string; name: string }>) {
  const wishlist = useSyncExternalStore(wishlistStore.subscribe, wishlistStore.read, wishlistStore.serverValue);
  return <WishlistHeart name={name} pressed={wishlist.includes(slug)} onToggle={() => toggleWishlist(slug)} />;
}
