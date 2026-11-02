"use client";

import Link from "next/link";
import { useEffect, useId, useRef, type InputHTMLAttributes, type ReactNode } from "react";
import { cn } from "@vaabhi/ui";
import { ChevronLeftIcon, ChevronRightIcon, CloseIcon, type Icon } from "./icons";

/*
 * Building blocks shared by every dashboard page, so lists, settings and reports look the same everywhere.
 */

// --- page header ----------------------------------------------------------------------------------------------

export function PageHeader({
  title,
  backHref,
  badge,
  actions,
}: {
  title: string;
  /** Shows a back button linking here. */
  backHref?: string;
  /** Small pill next to the title, e.g. the next order number. */
  badge?: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <div className="mb-6 flex flex-wrap items-center gap-3">
      {backHref && (
        <Link
          href={backHref}
          aria-label="Back"
          className="grid size-10 place-items-center rounded-lg border border-line bg-panel text-ink-soft hover:text-ink"
        >
          <ChevronLeftIcon />
        </Link>
      )}
      <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
      {badge && <span className="rounded-full border border-line-strong px-3 py-1 text-sm text-ink-soft">{badge}</span>}
      {actions && <div className="ml-auto flex items-center gap-2">{actions}</div>}
    </div>
  );
}

/** Small uppercase label above a group, with an optional action on the right (e.g. Save, + Add date). */
export function SectionLabel({ children, action }: { children: ReactNode; action?: ReactNode }) {
  return (
    <div className="mb-2 mt-6 flex items-center justify-between first:mt-0">
      <h2 className="text-xs font-semibold uppercase tracking-[0.14em] text-ink-faint">{children}</h2>
      {action}
    </div>
  );
}

// --- numbers ----------------------------------------------------------------------------------------------------

export type Tone = "default" | "accent" | "ok" | "warn" | "bad";

const toneText: Record<Tone, string> = {
  default: "text-ink",
  accent: "text-accent-ink",
  ok: "text-ok",
  warn: "text-warn",
  bad: "text-bad",
};

const toneSurface: Record<Tone, string> = {
  default: "border-line bg-panel",
  accent: "border-accent/40 bg-accent-soft",
  ok: "border-ok/30 bg-ok-soft",
  warn: "border-warn/30 bg-warn-soft",
  bad: "border-bad/30 bg-bad-soft",
};

/** A KPI card: label, big value and an optional line underneath. */
export function StatTile({
  label,
  value,
  hint,
  tone = "default",
  surface = false,
  icon: TileIcon,
}: {
  label: string;
  value: ReactNode;
  hint?: ReactNode;
  /** Colours the value. */
  tone?: Tone;
  /** Also tints the card itself (for the few figures that need to stand out). */
  surface?: boolean;
  icon?: Icon;
}) {
  return (
    <div className={cn("rounded-2xl border p-4", surface ? toneSurface[tone] : toneSurface.default)}>
      {TileIcon && <TileIcon className={cn("mb-3", toneText[tone])} />}
      <p className="text-xs font-semibold uppercase tracking-[0.12em] text-ink-faint">{label}</p>
      <p className={cn("mt-1 text-2xl font-bold tabular-nums", toneText[tone])}>{value}</p>
      {hint && <p className="mt-1 text-sm text-ink-soft">{hint}</p>}
    </div>
  );
}

// --- empty and error states -------------------------------------------------------------------------------------

export function EmptyState({
  icon: StateIcon,
  title,
  children,
  action,
  tone = "accent",
}: {
  icon: Icon;
  title: string;
  children?: ReactNode;
  action?: ReactNode;
  tone?: "accent" | "warn";
}) {
  return (
    <div className="flex flex-col items-center px-6 py-16 text-center">
      <div
        className={cn(
          "grid size-28 place-items-center rounded-full border",
          tone === "warn" ? "border-warn/30" : "border-line-strong",
        )}
      >
        <div
          className={cn(
            "grid size-20 place-items-center rounded-full border",
            tone === "warn" ? "border-warn/20 text-warn" : "border-line text-accent-ink",
          )}
        >
          <StateIcon size={32} />
        </div>
      </div>
      <h2 className="mt-6 text-xl font-semibold">{title}</h2>
      {children && <p className="mt-2 max-w-sm text-ink-soft">{children}</p>}
      {action && <div className="mt-6">{action}</div>}
    </div>
  );
}

// --- choices ----------------------------------------------------------------------------------------------------

export type Option<T extends string> = { value: T; label: ReactNode };

/** One-of-many pill filters (date ranges, statuses). Scrolls sideways on narrow screens. */
export function ChipGroup<T extends string>({
  label,
  options,
  value,
  onChange,
}: {
  /** Accessible name of the group. */
  label: string;
  options: Option<T>[];
  value: T;
  onChange: (value: T) => void;
}) {
  return (
    <div role="radiogroup" aria-label={label} className="-mx-1 flex gap-2 overflow-x-auto px-1 pb-1">
      {options.map((option) => {
        const selected = option.value === value;
        return (
          <button
            key={option.value}
            type="button"
            role="radio"
            aria-checked={selected}
            onClick={() => onChange(option.value)}
            className={cn(
              "shrink-0 rounded-full border px-4 py-1.5 text-sm transition-colors",
              selected
                ? "border-accent/60 bg-accent-soft text-accent-ink"
                : "border-line-strong text-ink-soft hover:text-ink",
            )}
          >
            {option.label}
          </button>
        );
      })}
    </div>
  );
}

/** Two or three equal segments, e.g. Day / Month or Outstanding / Collections. */
export function SegmentedControl<T extends string>({
  label,
  options,
  value,
  onChange,
}: {
  label: string;
  options: Option<T>[];
  value: T;
  onChange: (value: T) => void;
}) {
  return (
    <div role="radiogroup" aria-label={label} className="flex rounded-xl border border-line bg-panel p-1">
      {options.map((option) => {
        const selected = option.value === value;
        return (
          <button
            key={option.value}
            type="button"
            role="radio"
            aria-checked={selected}
            onClick={() => onChange(option.value)}
            className={cn(
              "flex-1 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
              selected ? "bg-accent text-white" : "text-ink-soft hover:text-ink",
            )}
          >
            {option.label}
          </button>
        );
      })}
    </div>
  );
}

export function Toggle({
  checked,
  onChange,
  label,
  disabled,
}: {
  checked: boolean;
  onChange: (checked: boolean) => void;
  /** Accessible name; the visible label is usually the surrounding row. */
  label: string;
  disabled?: boolean;
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={cn(
        "relative h-7 w-12 shrink-0 rounded-full transition-colors disabled:opacity-50",
        checked ? "bg-accent" : "bg-line-strong",
      )}
    >
      <span
        className={cn(
          "absolute top-1 size-5 rounded-full bg-white shadow transition-[left]",
          checked ? "left-6" : "left-1",
        )}
      />
    </button>
  );
}

// --- settings-style lists ---------------------------------------------------------------------------------------

/** A rounded card holding rows separated by lines. */
export function ListCard({ children }: { children: ReactNode }) {
  return <div className="divide-y divide-line overflow-hidden rounded-2xl border border-line bg-panel">{children}</div>;
}

/**
 * One row of a list card: optional icon tile, title, subtitle and a trailing value or control.
 * With `href` the row is a link and shows a chevron.
 */
export function ListRow({
  icon: RowIcon,
  title,
  subtitle,
  trailing,
  href,
}: {
  icon?: Icon;
  title: ReactNode;
  subtitle?: ReactNode;
  trailing?: ReactNode;
  href?: string;
}) {
  const body = (
    <>
      {RowIcon && (
        <span className="grid size-11 shrink-0 place-items-center rounded-xl bg-accent-soft text-accent-ink">
          <RowIcon />
        </span>
      )}
      <span className="min-w-0 flex-1">
        <span className="block font-medium">{title}</span>
        {subtitle && <span className="mt-0.5 block text-sm text-ink-soft">{subtitle}</span>}
      </span>
      {trailing && <span className="shrink-0 text-sm text-ink-soft">{trailing}</span>}
      {href && <ChevronRightIcon className="shrink-0 text-ink-faint" size={18} />}
    </>
  );
  const classes = "flex items-center gap-4 px-4 py-4";
  return href ? (
    <Link href={href} className={cn(classes, "hover:bg-panel-2")}>
      {body}
    </Link>
  ) : (
    <div className={classes}>{body}</div>
  );
}

// --- forms ------------------------------------------------------------------------------------------------------

export const inputClasses =
  "w-full rounded-xl border border-line-strong bg-field px-4 py-3 text-ink outline-none transition-colors focus:border-accent disabled:opacity-60";

export function TextField({
  label,
  hint,
  error,
  className,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { label: string; hint?: ReactNode; error?: string }) {
  const id = useId();
  return (
    <div className={className}>
      <label htmlFor={id} className="mb-1.5 block text-sm font-medium text-ink-soft">
        {label}
      </label>
      <input
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={error || hint ? `${id}-note` : undefined}
        className={cn(inputClasses, error && "border-bad")}
        {...props}
      />
      {(error || hint) && (
        <p id={`${id}-note`} className={cn("mt-1.5 text-sm", error ? "text-bad" : "text-ink-faint")}>
          {error ?? hint}
        </p>
      )}
    </div>
  );
}

// --- sheet ------------------------------------------------------------------------------------------------------

/** Modal panel: a bottom sheet on phones, a centred dialog on wider screens. Escape or the backdrop closes it. */
export function Sheet({
  open,
  title,
  onClose,
  children,
}: {
  open: boolean;
  title: string;
  onClose: () => void;
  children: ReactNode;
}) {
  const panel = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const onKey = (event: KeyboardEvent) => event.key === "Escape" && onClose();
    document.addEventListener("keydown", onKey);
    panel.current?.focus();
    return () => document.removeEventListener("keydown", onKey);
  }, [open, onClose]);

  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center sm:items-center">
      <button type="button" aria-label="Close" className="absolute inset-0 bg-black/60" onClick={onClose} />
      <div
        ref={panel}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        tabIndex={-1}
        className="relative max-h-[85vh] w-full overflow-y-auto rounded-t-3xl border border-line bg-panel p-6 outline-none sm:max-w-lg sm:rounded-3xl"
      >
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-xl font-semibold">{title}</h2>
          <button type="button" onClick={onClose} aria-label="Close" className="text-ink-soft hover:text-ink">
            <CloseIcon />
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}
