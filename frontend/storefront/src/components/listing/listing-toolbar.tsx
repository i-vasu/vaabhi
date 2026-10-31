"use client";

import { useState } from "react";
import type { FilterGroup } from "@vaabhi/api-client";
import { ChevronDownIcon, FilterIcon, SortIcon } from "@vaabhi/ui";
import { activeFilterCount, SORTS, withSort, type ListingState } from "@/lib/listing";
import { FiltersSheet } from "./filters-sheet";
import { SortSheet } from "./sort-sheet";
import { useListingNavigation } from "./use-listing-navigation";

const half =
  "flex grow basis-0 items-center justify-center gap-2 py-3.5 text-sm font-medium uppercase tracking-[0.02em] text-text-primary hover:text-brand-primary";

/** Sticky FILTER | SORT bar under the header; both open sheets that update the URL as you choose. */
export function ListingToolbar({
  state,
  groups,
  total,
}: Readonly<{
  state: ListingState;
  groups: FilterGroup[];
  total: number;
}>) {
  const [sheet, setSheet] = useState<"filters" | "sort" | null>(null);
  const { navigate, pending } = useListingNavigation();
  const count = activeFilterCount(state);
  const sortLabel = SORTS.find((s) => s.value === state.sort)!.short;

  return (
    <div className="sticky top-[53px] z-[5] border-y border-border-default bg-bg-surface">
      <div className="mx-auto flex max-w-6xl items-center">
        <button type="button" className={half} onClick={() => setSheet("filters")} aria-haspopup="dialog">
          <FilterIcon size={18} />
          Filter
          {count > 0 && (
            <span aria-hidden="true" className="rounded-full bg-brand-primary px-1.5 text-xs text-text-on-brand">
              {count}
            </span>
          )}
          {count > 0 && <span className="sr-only">{`, ${count} applied`}</span>}
        </button>
        <span aria-hidden="true" className="h-6 w-px bg-border-default" />
        <button type="button" className={half} onClick={() => setSheet("sort")} aria-haspopup="dialog">
          <SortIcon size={18} />
          Sort: {sortLabel}
          <ChevronDownIcon size={16} />
        </button>
      </div>
      <FiltersSheet
        open={sheet === "filters"}
        onClose={() => setSheet(null)}
        state={state}
        groups={groups}
        total={total}
        pending={pending}
        onChange={navigate}
      />
      <SortSheet
        open={sheet === "sort"}
        onClose={() => setSheet(null)}
        value={state.sort}
        onChange={(sort) => navigate(withSort(state, sort))}
      />
    </div>
  );
}
