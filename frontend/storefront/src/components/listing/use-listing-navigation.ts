"use client";

import { usePathname, useRouter } from "next/navigation";
import { useTransition } from "react";
import { listingHref, type ListingState } from "@/lib/listing";

/** Applies a new listing state by replacing the URL; the server page re-renders with fresh results and counts. */
export function useListingNavigation() {
  const router = useRouter();
  const pathname = usePathname();
  const [pending, startTransition] = useTransition();
  const navigate = (state: ListingState) =>
    startTransition(() => router.replace(listingHref(pathname, state), { scroll: false }));
  return { navigate, pending };
}
