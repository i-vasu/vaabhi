"use client";

import type { ReactNode } from "react";
import type { PaperFormat, PrintSettings, ShopProfile } from "@vaabhi/api-client";
import { cn } from "@vaabhi/ui";
import { buttonPrimary } from "./ui";

/** Page width per paper format; thermal rolls print in small type. */
export const PAPER: Record<PaperFormat, string> = {
  A4: "max-w-[190mm] text-sm",
  A5: "max-w-[130mm] text-sm",
  THERMAL_80MM: "max-w-[72mm] text-xs",
  THERMAL_58MM: "max-w-[48mm] text-[11px]",
};

export const PAPER_LABELS: Record<PaperFormat, string> = {
  A4: "A4",
  A5: "A5",
  THERMAL_80MM: "80 mm roll",
  THERMAL_58MM: "58 mm roll",
};

/**
 * A printable document: a paper-size picker and Print button on screen, then the document on white, which is all
 * that prints (the app frame hides itself when printing).
 */
export function PrintDocument({
  printing,
  format,
  onFormat,
  shop,
  title,
  children,
}: {
  printing: PrintSettings;
  format: PaperFormat;
  onFormat: (format: PaperFormat) => void;
  shop: ShopProfile;
  title: string;
  children: ReactNode;
}) {
  return (
    <section>
      <div className="mb-4 flex flex-wrap items-center gap-3 print:hidden">
        <label className="text-sm text-ink-soft">
          Paper{" "}
          <select
            aria-label="Paper"
            value={format}
            onChange={(e) => onFormat(e.target.value as PaperFormat)}
            className="ml-1 rounded-lg border border-line-strong bg-field px-2 py-1"
          >
            {printing.formats.map((f) => (
              <option key={f} value={f}>
                {PAPER_LABELS[f]}
              </option>
            ))}
          </select>
        </label>
        <button type="button" className={buttonPrimary} onClick={() => window.print()}>
          Print
        </button>
      </div>
      <article
        aria-label={title}
        className={cn("mx-auto rounded-xl bg-white p-6 text-black print:rounded-none print:p-0", PAPER[format])}
      >
        <header className="mb-3 border-b border-black/20 pb-3 text-center">
          <p className="text-lg font-bold">{shop.shopName}</p>
          {[shop.address, shop.phone].filter(Boolean).length > 0 && (
            <p className="text-black/70">{[shop.address, shop.phone].filter(Boolean).join(" · ")}</p>
          )}
          <p className="mt-1 font-semibold uppercase tracking-wide">{title}</p>
        </header>
        {children}
      </article>
    </section>
  );
}

/** A label-value row on paper. */
export function PaperLine({ label, value, strong = false }: { label: string; value: ReactNode; strong?: boolean }) {
  return (
    <div className={cn("flex justify-between gap-3", strong && "font-bold")}>
      <span>{label}</span>
      <span className="tabular-nums">{value}</span>
    </div>
  );
}
