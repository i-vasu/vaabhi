"use client";

import { useState } from "react";
import type { FilterGroup } from "@vaabhi/api-client";
import { Button, CheckboxRow, cn, ColourSwatch } from "@vaabhi/ui";
import { clearFilters, PRICE_BANDS, toggleValue, withInStock, withPrice, type ListingState } from "@/lib/listing";
import { Sheet } from "@/components/sheet";

const resultsLabel = (total: number) => `${total} ${total === 1 ? "result" : "results"}`;

/** Group order from the Figma filter sheet; anything new from the API goes after these. */
const ORDER = ["occasion", "fabric", "weave", "work", "colour", "size"];

type Tab = { code: string; name: string; selectedCount: number; group?: FilterGroup };

export function FiltersSheet({
  open,
  onClose,
  state,
  groups,
  total,
  pending,
  onChange,
}: Readonly<{
  open: boolean;
  onClose: () => void;
  state: ListingState;
  groups: FilterGroup[];
  total: number;
  pending: boolean;
  onChange: (state: ListingState) => void;
}>) {
  const rank = (code: string) => (ORDER.includes(code) ? ORDER.indexOf(code) : ORDER.length);
  const tabs: Tab[] = [
    ...[...groups]
      .sort((a, b) => rank(a.code) - rank(b.code))
      .map((group) => ({
        code: group.code,
        name: group.name,
        selectedCount: group.options.filter((o) => o.selected).length,
        group,
      })),
    {
      code: "price",
      name: "Price",
      selectedCount: state.priceMin !== undefined || state.priceMax !== undefined ? 1 : 0,
    },
    { code: "availability", name: "Availability", selectedCount: state.inStock ? 1 : 0 },
  ];
  const [activeCode, setActiveCode] = useState(tabs[0]?.code ?? "price");
  const active = tabs.find((t) => t.code === activeCode) ?? tabs[0]!;

  return (
    <Sheet
      open={open}
      onClose={onClose}
      title="Filters"
      action={{ label: "Clear all", onClick: () => onChange(clearFilters(state)) }}
      footer={
        <>
          <Button variant="secondary" onClick={() => onChange(clearFilters(state))}>
            Clear
          </Button>
          <Button className="grow" onClick={onClose} aria-live="polite" aria-busy={pending}>
            {pending ? "Updating…" : `Show ${resultsLabel(total)}`}
          </Button>
        </>
      }
    >
      <div className="flex h-full">
        <div
          role="tablist"
          aria-orientation="vertical"
          aria-label="Filter groups"
          className="w-[132px] shrink-0 bg-bg-muted"
        >
          {tabs.map((tab) => {
            const selected = tab.code === active.code;
            return (
              <button
                key={tab.code}
                type="button"
                role="tab"
                id={`filter-tab-${tab.code}`}
                aria-selected={selected}
                aria-controls="filter-panel"
                onClick={() => setActiveCode(tab.code)}
                className={cn(
                  "flex w-full items-center justify-between gap-1.5 py-3.5 pl-3.5 pr-2.5 text-left text-sm",
                  selected
                    ? "border-l-[3px] border-brand-primary bg-bg-surface font-semibold text-brand-primary"
                    : "text-text-secondary",
                )}
              >
                {tab.name}
                {tab.selectedCount > 0 && (
                  <span aria-hidden="true" className="rounded-full bg-brand-primary px-1.5 text-xs text-text-on-brand">
                    {tab.selectedCount}
                  </span>
                )}
                {tab.selectedCount > 0 && <span className="sr-only">{`, ${tab.selectedCount} selected`}</span>}
              </button>
            );
          })}
        </div>
        <div
          id="filter-panel"
          role="tabpanel"
          aria-labelledby={`filter-tab-${active.code}`}
          className="min-w-0 grow overflow-y-auto px-4 pt-2 pb-4"
        >
          <p className="pb-1 text-xs uppercase tracking-[0.12em] text-text-muted">{active.name}</p>
          <Panel tab={active} state={state} onChange={onChange} />
        </div>
      </div>
    </Sheet>
  );
}

function Panel({
  tab,
  state,
  onChange,
}: Readonly<{ tab: Tab; state: ListingState; onChange: (state: ListingState) => void }>) {
  if (tab.code === "price") {
    return (
      <fieldset>
        <legend className="sr-only">Price</legend>
        {PRICE_BANDS.map((band) => {
          const min = "min" in band ? band.min : undefined;
          const max = "max" in band ? band.max : undefined;
          return (
            <CheckboxRow
              key={band.label}
              kind="radio"
              name="price"
              label={band.label}
              checked={state.priceMin === min && state.priceMax === max}
              onChange={() => onChange(withPrice(state, min, max))}
            />
          );
        })}
        <CheckboxRow
          kind="radio"
          name="price"
          label="Any price"
          checked={state.priceMin === undefined && state.priceMax === undefined}
          onChange={() => onChange(withPrice(state))}
        />
      </fieldset>
    );
  }
  if (tab.code === "availability") {
    return (
      <CheckboxRow
        label="In stock only"
        checked={state.inStock}
        onChange={(event) => onChange(withInStock(state, event.target.checked))}
      />
    );
  }
  const group = tab.group!;
  if (group.type === "COLOUR") {
    return (
      <fieldset className="flex flex-wrap gap-2.5 pt-2.5">
        <legend className="sr-only">{group.name}</legend>
        {group.options.map((option) => (
          <ColourSwatch
            key={option.code}
            name={option.name}
            hex={option.hex ?? "#E4DCD0"}
            count={option.count}
            checked={option.selected}
            disabled={!option.selected && option.count === 0}
            onChange={() => onChange(toggleValue(state, group.code, option.code))}
          />
        ))}
      </fieldset>
    );
  }
  return (
    <fieldset>
      <legend className="sr-only">{group.name}</legend>
      {group.options.map((option) => (
        <CheckboxRow
          key={option.code}
          label={option.name}
          count={option.count}
          checked={option.selected}
          disabled={!option.selected && option.count === 0}
          onChange={() => onChange(toggleValue(state, group.code, option.code))}
        />
      ))}
    </fieldset>
  );
}
