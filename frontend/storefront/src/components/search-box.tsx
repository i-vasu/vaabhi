"use client";

import Link from "next/link";
import { useEffect, useId, useRef, useState } from "react";
import { CloseIcon, SearchIcon } from "@vaabhi/ui";
import type { SearchSuggestion, SuggestionsResponse } from "@/app/api/search/route";
import { useMediaBase } from "@/components/media-base";
import { formatPrice } from "@/lib/format";
import { mediaUrl } from "@/lib/listing";

type Results =
  | { state: "idle" }
  | { state: "loading"; query: string }
  | { state: "done"; query: string; total: number; items: SearchSuggestion[]; match: SuggestionsResponse["match"] }
  | { state: "error"; query: string };

/** Wait this long after the last key before asking, so typing "kanjeevaram" is one request, not eleven. */
const DEBOUNCE_MS = 250;
const MIN_LENGTH = 2;

const searchHref = (q: string) => `/search?q=${encodeURIComponent(q)}`;

/**
 * The header's search: the icon opens a search bar across the top that suggests products as the shopper types
 * (name, weave, category or occasion). Enter, or "See all", opens the full results page. Without JavaScript the
 * icon is a plain link to /search.
 */
export function SearchBox({ className }: Readonly<{ className?: string }>) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<Results>({ state: "idle" });
  const dialog = useRef<HTMLDialogElement>(null);
  const input = useRef<HTMLInputElement>(null);
  const base = useMediaBase();
  const listId = useId();

  useEffect(() => {
    const el = dialog.current;
    if (!el) return;
    if (open && !el.open) {
      el.showModal();
      input.current?.focus();
    }
    if (!open && el.open) el.close();
  }, [open]);

  // A click on the dialog itself (not its content) is a click on the backdrop, which closes it; keyboard users
  // close it with Escape, which the dialog handles natively.
  useEffect(() => {
    const el = dialog.current;
    if (!el) return;
    const onBackdrop = (event: MouseEvent) => {
      if (event.target === el) setOpen(false);
    };
    el.addEventListener("click", onBackdrop);
    return () => el.removeEventListener("click", onBackdrop);
  }, []);

  useEffect(() => {
    const q = query.trim();
    // Too short to search: the render shows nothing (see `shown`), so there is nothing to ask for.
    if (q.length < MIN_LENGTH) return;
    const controller = new AbortController();
    const timer = setTimeout(async () => {
      setResults({ state: "loading", query: q });
      try {
        const response = await fetch(`/api/search?q=${encodeURIComponent(q)}`, { signal: controller.signal });
        if (!response.ok) throw new Error(String(response.status));
        const body = (await response.json()) as SuggestionsResponse;
        setResults({ state: "done", query: q, total: body.total, items: body.items, match: body.match });
      } catch {
        // A newer keystroke cancelled this request; only real failures are shown.
        if (!controller.signal.aborted) setResults({ state: "error", query: q });
      }
    }, DEBOUNCE_MS);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [query]);

  const close = () => setOpen(false);
  // Results belong to the current text only; a box cleared back to one letter shows nothing.
  const shown: Results = query.trim().length < MIN_LENGTH ? { state: "idle" } : results;

  return (
    <>
      <Link
        href="/search"
        aria-label="Search"
        aria-haspopup="dialog"
        className={className}
        onClick={(event) => {
          event.preventDefault();
          setOpen(true);
        }}
      >
        <SearchIcon size={22} />
      </Link>
      <dialog
        ref={dialog}
        aria-label="Search the shop"
        onClose={close}
        className="fixed inset-x-0 top-0 m-0 w-full max-w-none bg-transparent p-0 backdrop:bg-[#2B2622]/45 [&:not([open])]:hidden"
      >
        <div className="bg-bg-surface shadow-lg">
          <form
            role="search"
            action="/search"
            className="mx-auto flex max-w-3xl items-center gap-3 px-4 py-3"
            // A plain GET to /search?q=…; an empty box goes nowhere.
            onSubmit={(event) => {
              if (!query.trim()) event.preventDefault();
            }}
          >
            <SearchIcon size={20} className="shrink-0 text-text-muted" />
            <label htmlFor={`${listId}-q`} className="sr-only">
              Search products
            </label>
            <input
              ref={input}
              id={`${listId}-q`}
              name="q"
              type="search"
              autoComplete="off"
              enterKeyHint="search"
              maxLength={100}
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Search sarees, lehengas, Banarasi…"
              aria-controls={listId}
              // The browser's own clear button would sit beside our close button; one × is enough.
              className="min-w-0 flex-1 bg-transparent py-2 text-base text-text-primary outline-none placeholder:text-text-muted [&::-webkit-search-cancel-button]:hidden"
            />
            <button type="button" onClick={close} aria-label="Close search" className="p-0.5 hover:text-brand-primary">
              <CloseIcon size={22} />
            </button>
          </form>
          <div id={listId} aria-live="polite" className="mx-auto max-w-3xl">
            <SearchResults results={shown} base={base} onPick={close} />
          </div>
        </div>
      </dialog>
    </>
  );
}

function SearchResults({
  results,
  base,
  onPick,
}: Readonly<{ results: Results; base: string | undefined; onPick: () => void }>) {
  if (results.state === "idle") return null;
  const note = "border-t border-border-default px-4 py-5 text-sm text-text-muted";
  if (results.state === "loading") return <p className={note}>Searching…</p>;
  if (results.state === "error") return <p className={note}>Search is unavailable right now. Please try again.</p>;
  if (results.items.length === 0) {
    return (
      <div className={note}>
        <p>No products match “{results.query}”.</p>
        <Link href="/new" onClick={onPick} className="mt-2 inline-block text-brand-primary underline">
          Browse new arrivals
        </Link>
      </div>
    );
  }
  const exact = results.match === "exact";
  return (
    <div className="max-h-[70dvh] overflow-y-auto border-t border-border-default">
      {!exact && (
        <p className="px-4 pb-1 pt-3 text-sm text-text-muted">
          No exact match for “{results.query}”.{" "}
          {results.match === "related" ? "Here are related pieces:" : "You may like these:"}
        </p>
      )}
      <ul aria-label={exact ? "Suggested products" : "Related products"}>
        {results.items.map((item) => {
          const src = item.imageKey ? mediaUrl(item.imageKey, 400, base) : undefined;
          return (
            <li key={item.slug}>
              <Link
                href={`/p/${item.slug}`}
                onClick={onPick}
                className="flex items-center gap-3 px-4 py-2.5 hover:bg-bg-muted focus-visible:bg-bg-muted"
              >
                {src && (
                  // eslint-disable-next-line @next/next/no-img-element -- R2 serves fixed WebP sizes
                  <img src={src} alt="" className="h-16 w-12 shrink-0 rounded-sm object-cover" />
                )}
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm text-text-primary">{item.name}</span>
                  {item.subtitle && <span className="block truncate text-xs text-text-muted">{item.subtitle}</span>}
                </span>
                <span className="shrink-0 text-right text-sm tabular-nums">
                  <span className="font-semibold text-text-primary">{formatPrice(item.priceRupees)}</span>
                  {item.mrpRupees > item.priceRupees && (
                    <>
                      {" "}
                      <span className="block text-xs text-text-muted line-through">
                        <span className="sr-only">MRP </span>
                        {formatPrice(item.mrpRupees)}
                      </span>
                    </>
                  )}
                </span>
              </Link>
            </li>
          );
        })}
      </ul>
      <Link
        href={searchHref(results.query)}
        onClick={onPick}
        className="block border-t border-border-default px-4 py-3.5 text-center text-sm font-medium text-brand-primary hover:underline"
      >
        {exact
          ? `See all ${results.total} ${results.total === 1 ? "result" : "results"} for “${results.query}”`
          : `See all ${results.total} related ${results.total === 1 ? "piece" : "pieces"}`}
      </Link>
    </div>
  );
}
