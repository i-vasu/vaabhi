"use client";

import Link from "next/link";
import { useEffect, useId, useRef, type InputHTMLAttributes, type ReactNode, type TextareaHTMLAttributes } from "react";
import { useRouter } from "next/navigation";
import { createPortal } from "react-dom";
import { cn } from "@vaabhi/ui";
import { canGoBack } from "@/lib/history";
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
  const router = useRouter();
  return (
    <div className="mb-6 flex flex-wrap items-center gap-3">
      {backHref && (
        <Link
          href={backHref}
          onClick={(event) => {
            // Return to the page the person came from; open on its own (a shared link), go to the parent.
            if (canGoBack() && !event.metaKey && !event.ctrlKey) {
              event.preventDefault();
              router.back();
            }
          }}
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
        <span className="grid size-11 shrink-0 place-items-center rounded-xl bg-gold-soft text-gold-ink">
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
  // Other sites (dashboards) open in a new tab so the app stays where it was.
  if (href && /^https?:\/\//.test(href)) {
    return (
      <a href={href} target="_blank" rel="noreferrer" className={cn(classes, "hover:bg-panel-2")}>
        {body}
      </a>
    );
  }
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

function FieldShell({
  id,
  label,
  hint,
  error,
  className,
  children,
}: {
  id: string;
  label: string;
  hint?: ReactNode;
  error?: string;
  className?: string;
  children: ReactNode;
}) {
  return (
    <div className={className}>
      <label htmlFor={id} className="mb-1.5 block text-sm font-medium text-ink-soft">
        {label}
      </label>
      {children}
      {(error || hint) && (
        <p id={`${id}-note`} className={cn("mt-1.5 text-sm", error ? "text-bad" : "text-ink-faint")}>
          {error ?? hint}
        </p>
      )}
    </div>
  );
}

export function TextAreaField({
  label,
  hint,
  error,
  className,
  ...props
}: TextareaHTMLAttributes<HTMLTextAreaElement> & { label: string; hint?: ReactNode; error?: string }) {
  const id = useId();
  return (
    <FieldShell id={id} label={label} hint={hint} error={error} className={className}>
      <textarea
        id={id}
        rows={3}
        aria-invalid={error ? true : undefined}
        aria-describedby={error || hint ? `${id}-note` : undefined}
        className={cn(inputClasses, "resize-y", error && "border-bad")}
        {...props}
      />
    </FieldShell>
  );
}

export function SelectField<T extends string>({
  label,
  hint,
  error,
  className,
  options,
  value,
  onChange,
  disabled,
}: {
  label: string;
  hint?: ReactNode;
  error?: string;
  className?: string;
  options: { value: T; label: string }[];
  value: T;
  onChange: (value: T) => void;
  disabled?: boolean;
}) {
  const id = useId();
  return (
    <FieldShell id={id} label={label} hint={hint} error={error} className={className}>
      <select
        id={id}
        value={value}
        disabled={disabled}
        aria-invalid={error ? true : undefined}
        aria-describedby={error || hint ? `${id}-note` : undefined}
        onChange={(event) => onChange(event.target.value as T)}
        className={cn(inputClasses, "appearance-auto", error && "border-bad")}
      >
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </FieldShell>
  );
}

/** A list row whose trailing control is a switch; the whole row explains what the switch does. */
export function ToggleRow({
  title,
  subtitle,
  checked,
  onChange,
  disabled,
}: {
  title: string;
  subtitle?: ReactNode;
  checked: boolean;
  onChange: (checked: boolean) => void;
  disabled?: boolean;
}) {
  return (
    <div className="flex items-center gap-4 px-4 py-4">
      <span className="min-w-0 flex-1">
        <span className="block font-medium">{title}</span>
        {subtitle && <span className="mt-0.5 block text-sm text-ink-soft">{subtitle}</span>}
      </span>
      <Toggle checked={checked} onChange={onChange} label={title} disabled={disabled} />
    </div>
  );
}

export const buttonPrimary =
  "inline-flex items-center justify-center gap-2 rounded-xl bg-accent px-5 py-3 font-medium text-white transition-colors hover:bg-accent-strong disabled:opacity-50";
export const buttonSecondary =
  "inline-flex items-center justify-center gap-2 rounded-xl border border-line-strong px-4 py-2.5 font-medium text-accent-ink transition-colors hover:bg-panel-2 disabled:opacity-50";

/** Save button with its outcome: "Saved" after success, the reason after failure. */
export function SaveBar({
  saving,
  saved,
  error,
  onSave,
  label = "Save",
}: {
  saving: boolean;
  saved: boolean;
  error: string | null;
  onSave: () => void;
  label?: string;
}) {
  return (
    <div className="sticky bottom-20 z-20 mt-6 flex flex-wrap items-center gap-3 rounded-2xl border border-line bg-panel/95 p-3 backdrop-blur md:bottom-4">
      <button type="button" className={cn(buttonPrimary, "min-w-32")} disabled={saving} onClick={onSave}>
        {saving ? "Saving…" : label}
      </button>
      <span role="status" className={cn("text-sm", error ? "text-bad" : "text-ok")}>
        {error ?? (saved ? "Saved" : "")}
      </span>
    </div>
  );
}

/** Loading and load-failure placeholder for a page that fetches before it can show a form. */
export function LoadState({ loading, error }: { loading: boolean; error: string | null }) {
  if (error) {
    return (
      <p role="alert" className="rounded-xl bg-bad-soft px-4 py-3 text-bad">
        {error}
      </p>
    );
  }
  return loading ? <p className="text-ink-soft">Loading…</p> : null;
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
  // Callers often pass a fresh onClose each render; keeping it in a ref means the effect below runs only when the
  // sheet opens, so focusing the panel never steals focus from a field being typed in (it closed phone keyboards).
  const closeRef = useRef(onClose);
  useEffect(() => {
    closeRef.current = onClose;
  });
  useEffect(() => {
    if (!open) return;
    const onKey = (event: KeyboardEvent) => event.key === "Escape" && closeRef.current();
    document.addEventListener("keydown", onKey);
    panel.current?.focus();
    return () => document.removeEventListener("keydown", onKey);
  }, [open]);

  if (!open) return null;
  // Portalled to <body>: a parent with backdrop-filter (the sticky header) would otherwise trap this fixed
  // overlay inside itself, squashing the sheet into the header bar.
  return createPortal(
    <div className="fixed inset-0 z-50 flex items-end justify-center sm:items-center">
      <button type="button" aria-label="Close" className="absolute inset-0 bg-ink/40" onClick={onClose} />
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
    </div>,
    document.body,
  );
}
