"use client";

import { CheckIcon, cn } from "@vaabhi/ui";
import { SORTS, type SortValue } from "@/lib/listing";
import { Sheet } from "@/components/sheet";

export function SortSheet({
  open,
  onClose,
  value,
  onChange,
}: Readonly<{
  open: boolean;
  onClose: () => void;
  value: SortValue;
  onChange: (sort: SortValue) => void;
}>) {
  return (
    <Sheet open={open} onClose={onClose} title="Sort by" size="short">
      <fieldset className="px-4 pt-1 pb-4">
        <legend className="sr-only">Sort products</legend>
        {SORTS.map((sort) => {
          const selected = sort.value === value;
          return (
            <label
              key={sort.value}
              className="flex cursor-pointer items-center justify-between border-b border-border-default py-3.5 has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-brand-primary"
            >
              <input
                type="radio"
                name="sort"
                value={sort.value}
                checked={selected}
                onChange={() => {
                  onChange(sort.value);
                  onClose();
                }}
                className="peer sr-only"
              />
              <span className={cn("text-base", selected ? "font-semibold text-brand-primary" : "text-text-primary")}>
                {sort.label}
              </span>
              {selected && <CheckIcon size={20} className="text-brand-primary" />}
            </label>
          );
        })}
      </fieldset>
    </Sheet>
  );
}
