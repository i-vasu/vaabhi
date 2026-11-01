"use client";

import Link from "next/link";
import { useSyncExternalStore } from "react";
import { BagIcon } from "@vaabhi/ui";
import { bagCount, bagStore } from "@/lib/bag";

const label = (count: number) => {
  if (!count) return "Shopping bag";
  return `Shopping bag, ${count} ${count === 1 ? "item" : "items"}`;
};

/** Header bag icon with the number of items, kept in sync across tabs. */
export function BagLink({ className }: Readonly<{ className?: string }>) {
  const lines = useSyncExternalStore(bagStore.subscribe, bagStore.read, bagStore.serverValue);
  const count = bagCount(lines);
  return (
    <Link href="/bag" aria-label={label(count)} className={`relative ${className ?? ""}`}>
      <BagIcon size={22} />
      {count > 0 && (
        <span
          aria-hidden="true"
          className="absolute -right-1.5 -top-1 grid h-4 min-w-4 place-items-center rounded-full bg-brand-primary px-1 text-[10px] text-text-on-brand"
        >
          {count}
        </span>
      )}
    </Link>
  );
}
