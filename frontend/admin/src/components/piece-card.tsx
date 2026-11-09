"use client";

import Link from "next/link";
import type { WorkPiece, WorkshopStaff } from "@vaabhi/api-client";
import { cn } from "@vaabhi/ui";
import { qty, unitLabel } from "@/lib/extras";
import { mediaUrl } from "@/lib/photo-upload";
import { Pill } from "./tailoring";

/** A piece cut from stock on the work board: no customer or due date, a link to publish once it is ready. */
export function PieceCard({
  piece,
  staff,
  selected,
  busy,
  nextStageName,
  onSelect,
  onOpen,
  onAssign,
  onNext,
}: {
  piece: WorkPiece;
  staff: WorkshopStaff[] | undefined;
  selected: boolean;
  busy: boolean;
  nextStageName: string | undefined;
  onSelect: () => void;
  onOpen: () => void;
  onAssign: (staffId: number | null) => void;
  onNext: () => void;
}) {
  return (
    <li className={cn("rounded-2xl border bg-panel p-4", selected ? "border-accent" : "border-line")}>
      <div className="flex items-start gap-3">
        <input
          type="checkbox"
          className="mt-1 size-5 accent-accent"
          aria-label={`Select ${piece.productName}`}
          checked={selected}
          onChange={onSelect}
        />
        <button type="button" className="min-w-0 flex-1 text-left" onClick={onOpen}>
          <p className="font-medium">
            <Pill tone="accent">Stock</Pill> {piece.productName}
          </p>
          <p className="text-sm text-ink-soft">
            {piece.stockItemName} · {qty(piece.quantity)} {unitLabel(piece.unit)}
            {piece.photos.length > 0 && ` · ${piece.photos.length} design photo${piece.photos.length > 1 ? "s" : ""}`}
          </p>
        </button>
        {piece.photoKey && (
          // eslint-disable-next-line @next/next/no-img-element -- static export, R2 serves the resized file
          <img
            src={mediaUrl(piece.photoKey)}
            alt={`Fabric for ${piece.productName}`}
            className="size-14 rounded-xl border border-line object-cover"
          />
        )}
      </div>
      <div className="mt-3 flex flex-wrap items-center gap-2">
        {piece.stage.kind === "WORK" && staff && (
          <select
            aria-label={`Given to (${piece.productName})`}
            disabled={busy}
            value={piece.assignedTo ? String(piece.assignedTo.id) : ""}
            onChange={(event) => onAssign(event.target.value ? Number(event.target.value) : null)}
            className="min-w-0 flex-1 rounded-xl border border-line-strong bg-field px-3 py-2 text-sm"
          >
            <option value="">Nobody yet</option>
            {staff.map((s) => (
              <option key={s.id} value={String(s.id)}>
                {s.name} ({s.workingOn})
              </option>
            ))}
          </select>
        )}
        {piece.stage.kind === "READY" ? (
          <Link
            href={`/products/edit/?id=${piece.productId}`}
            className="rounded-xl bg-accent px-3 py-2 text-sm font-medium text-white hover:bg-accent-strong"
          >
            Add photos &amp; publish
          </Link>
        ) : (
          nextStageName && (
            <button
              type="button"
              className="rounded-xl border border-line-strong px-3 py-2 text-sm font-medium text-accent-ink hover:bg-panel-2"
              disabled={busy}
              onClick={onNext}
            >
              {nextStageName} →
            </button>
          )
        )}
      </div>
    </li>
  );
}
