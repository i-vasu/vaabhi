"use client";

import { useState, type ReactNode } from "react";
import type { ClientSummary, OrderStatus, TailoringImagePurpose } from "@vaabhi/api-client";
import { cn } from "@vaabhi/ui";
import { api } from "@/lib/api";
import { mediaUrl } from "@/lib/photo-upload";
import { describeError } from "@/lib/settings";
import { STATUS_LABELS, STATUS_TONES, formatPhone, uploadTailoringPhoto, useLoad } from "@/lib/tailoring";
import { CloseIcon, ImageIcon, SearchIcon } from "./icons";
import { inputClasses, type Tone } from "./ui";

/*
 * Pieces shared by the customer, order and alteration pages.
 */

const pillTones: Record<Tone, string> = {
  default: "border-line-strong text-ink-soft",
  accent: "border-accent/50 bg-accent-soft text-accent-ink",
  ok: "border-ok/40 bg-ok-soft text-ok",
  warn: "border-warn/40 bg-warn-soft text-warn",
  bad: "border-bad/40 bg-bad-soft text-bad",
};

export function Pill({ tone = "default", children }: { tone?: Tone; children: ReactNode }) {
  return (
    <span className={cn("inline-block rounded-full border px-2.5 py-0.5 text-xs font-medium", pillTones[tone])}>
      {children}
    </span>
  );
}

/** Order or alteration status; an open job past its date shows Overdue instead. */
export function StatusPill({ status, overdue = false }: { status: OrderStatus; overdue?: boolean }) {
  return overdue ? <Pill tone="warn">Overdue</Pill> : <Pill tone={STATUS_TONES[status]}>{STATUS_LABELS[status]}</Pill>;
}

/** Search box that reports on submit (Enter), so each keystroke does not hit the API. */
export function SearchBox({
  label,
  placeholder,
  initial = "",
  onSearch,
}: {
  label: string;
  placeholder: string;
  initial?: string;
  onSearch: (query: string) => void;
}) {
  const [text, setText] = useState(initial);
  return (
    <form
      role="search"
      className="relative min-w-0 flex-1"
      onSubmit={(event) => {
        event.preventDefault();
        onSearch(text.trim());
      }}
    >
      <SearchIcon size={18} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-ink-faint" />
      <input
        type="search"
        aria-label={label}
        placeholder={placeholder}
        value={text}
        onChange={(event) => setText(event.target.value)}
        className={cn(inputClasses, "pl-10")}
      />
    </form>
  );
}

/** "12 orders · Page 1 of 3" with previous / next. */
export function Pager({
  total,
  page,
  pageSize,
  noun,
  onPage,
}: {
  total: number;
  page: number;
  pageSize: number;
  /** Singular and plural, e.g. ["order", "orders"]. */
  noun: [string, string];
  onPage: (page: number) => void;
}) {
  const last = Math.max(1, Math.ceil(total / pageSize));
  return (
    <footer className="mt-4 flex flex-wrap items-center gap-3 text-sm text-ink-soft">
      <span>
        {total} {total === 1 ? noun[0] : noun[1]}
      </span>
      {last > 1 && (
        <>
          <button
            type="button"
            disabled={page <= 1}
            onClick={() => onPage(page - 1)}
            className="ml-auto underline disabled:opacity-40"
          >
            Previous
          </button>
          <span>
            Page {page} of {last}
          </span>
          <button
            type="button"
            disabled={page >= last}
            onClick={() => onPage(page + 1)}
            className="underline disabled:opacity-40"
          >
            Next
          </button>
        </>
      )}
    </footer>
  );
}

/** Pick or take a photo; it is resized and uploaded straight away and `onChange` gets the key. */
export function PhotoInput({
  label,
  purpose,
  value,
  onChange,
  upload = uploadTailoringPhoto,
}: {
  label: string;
  purpose: TailoringImagePurpose;
  value: string | null;
  onChange: (key: string | null) => void;
  upload?: typeof uploadTailoringPhoto;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  return (
    <div>
      <div className="flex items-center gap-3">
        {value ? (
          // eslint-disable-next-line @next/next/no-img-element -- static export, R2 serves the resized file
          <img src={mediaUrl(value)} alt={label} className="size-16 rounded-xl border border-line object-cover" />
        ) : (
          <span className="grid size-16 place-items-center rounded-xl border border-dashed border-line-strong text-ink-faint">
            <ImageIcon />
          </span>
        )}
        <label className="cursor-pointer text-sm font-medium text-accent-ink underline">
          {busy ? "Uploading…" : value ? "Change photo" : label}
          <input
            type="file"
            accept="image/*"
            capture="environment"
            className="sr-only"
            disabled={busy}
            aria-label={label}
            onChange={async (event) => {
              const file = event.target.files?.[0];
              event.target.value = "";
              if (!file) return;
              setBusy(true);
              setError(null);
              try {
                onChange(await upload(purpose, file));
              } catch (e) {
                setError(describeError(e).message);
              } finally {
                setBusy(false);
              }
            }}
          />
        </label>
        {value && !busy && (
          <button
            type="button"
            aria-label={`Remove ${label.toLowerCase()}`}
            className="text-ink-faint hover:text-bad"
            onClick={() => onChange(null)}
          >
            <CloseIcon size={18} />
          </button>
        )}
      </div>
      {error && (
        <p role="alert" className="mt-1.5 text-sm text-bad">
          {error}
        </p>
      )}
    </div>
  );
}

/** Find a customer by name or mobile number for a new order or alteration. */
export function ClientPicker({
  value,
  onChange,
}: {
  value: ClientSummary | null;
  onChange: (client: ClientSummary | null) => void;
}) {
  const [query, setQuery] = useState("");
  const results = useLoad(
    () =>
      query
        ? api.admin.clients.list({ q: query, pageSize: 8 })
        : Promise.resolve({ items: [], total: 0, page: 1, pageSize: 8 }),
    [query],
  );

  if (value) {
    return (
      <div className="flex items-center gap-3 rounded-xl border border-line bg-panel-2 px-4 py-3">
        <span className="min-w-0 flex-1">
          <span className="block font-medium">{value.name}</span>
          <span className="block text-sm text-ink-soft">{formatPhone(value.phone)}</span>
        </span>
        <button type="button" className="text-sm text-accent-ink underline" onClick={() => onChange(null)}>
          Change
        </button>
      </div>
    );
  }
  return (
    <div>
      <SearchBox label="Find customer" placeholder="Name or mobile number" onSearch={setQuery} />
      {results.error && (
        <p role="alert" className="mt-2 text-sm text-bad">
          {results.error}
        </p>
      )}
      {query && results.value && (
        <ul className="mt-2 divide-y divide-line overflow-hidden rounded-xl border border-line">
          {results.value.items.length === 0 ? (
            <li className="px-4 py-3 text-sm text-ink-soft">No customer matches “{query}”.</li>
          ) : (
            results.value.items.map((client) => (
              <li key={client.id}>
                <button
                  type="button"
                  className="block w-full px-4 py-3 text-left hover:bg-panel-2"
                  onClick={() => onChange(client)}
                >
                  <span className="block font-medium">{client.name}</span>
                  <span className="block text-sm text-ink-soft">{formatPhone(client.phone)}</span>
                </button>
              </li>
            ))
          )}
        </ul>
      )}
    </div>
  );
}

/** Label-value pair for detail pages. */
export function Fact({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <dt className="text-xs font-semibold uppercase tracking-[0.12em] text-ink-faint">{label}</dt>
      <dd className="mt-0.5">{children}</dd>
    </div>
  );
}

export function ErrorNote({ children }: { children: ReactNode }) {
  return children ? (
    <p role="alert" className="rounded-xl bg-bad-soft px-4 py-3 text-bad">
      {children}
    </p>
  ) : null;
}
