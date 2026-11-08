import type { Metadata } from "next";
import { WishlistView } from "@/components/wishlist-view";

export const metadata: Metadata = { title: "Your wishlist", robots: { index: false } };

/** The pieces the shopper saved with the heart (kept in this browser). */
export default function WishlistPage() {
  return (
    <section className="mx-auto w-full max-w-6xl px-4 pb-16 pt-8">
      <h1 className="mb-4 font-serif text-[32px] font-semibold leading-10 text-text-primary">Your wishlist</h1>
      <WishlistView />
    </section>
  );
}
