"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import type { BoardColumn, StageWork, WorkItem, WorkPiece } from "@vaabhi/api-client";
import { BoardIcon } from "@/components/icons";
import { PieceCard } from "@/components/piece-card";
import { PieceSheet, ShareButton, StageWorkPrompt } from "@/components/piece-sheet";
import { ErrorNote, Pill, SearchBox } from "@/components/tailoring";
import { ChipGroup, EmptyState, LoadState, PageHeader, SelectField, buttonPrimary } from "@/components/ui";
import { cn } from "@vaabhi/ui";
import { api } from "@/lib/api";
import { mediaUrl } from "@/lib/photo-upload";
import { formatDate, idParam, useAction, useLoad } from "@/lib/tailoring";

/** The stage after `index` among the active columns, if any (Ready has none here: hand over from the order). */
export function nextColumn(columns: BoardColumn[], index: number): BoardColumn | undefined {
  const current = columns[index];
  if (!current || current.stage.kind === "READY") return undefined;
  if (current.removed) return columns.find((c) => !c.removed);
  return columns.slice(index + 1).find((c) => !c.removed);
}

function Board() {
  const params = useSearchParams();
  const staffId = idParam(params.get("staffId")) ?? undefined;
  const [unassigned, setUnassigned] = useState(params.get("unassigned") === "1");
  const [q, setQ] = useState("");
  const board = useLoad(() => api.admin.workshop.board({ staffId, q: q || undefined }), [staffId, q]);
  const staff = useLoad(() => api.admin.workshop.staff(), []);
  const [chosen, setChosen] = useState<number | null>(null);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [selectedPieces, setSelectedPieces] = useState<Set<number>>(new Set());
  const [target, setTarget] = useState("");
  const [openPiece, setOpenPiece] = useState<number | null>(null);
  const [leaving, setLeaving] = useState<{ piece: WorkPiece; stageId: number } | null>(null);
  const [justAssigned, setJustAssigned] = useState<WorkPiece | null>(null);
  const action = useAction();

  const columns = board.value ?? [];
  const index = Math.max(
    0,
    columns.findIndex((c) => c.stage.id === chosen),
  );
  const column = columns[index];
  const items = (column?.items ?? []).filter((item) => !unassigned || item.assignedTo === null);
  const pieces = (column?.pieces ?? []).filter((piece) => !unassigned || piece.assignedTo === null);
  const next = nextColumn(columns, index);
  const person = staffId ? staff.value?.find((s) => s.id === staffId) : undefined;
  const allPieces = columns.flatMap((c) => c.pieces);
  const shownPiece = allPieces.find((p) => p.pieceId === openPiece) ?? null;
  const selectedCount = selected.size + selectedPieces.size;

  const moveTo = async (stageId: number, itemIds: number[], pieceIds: number[] = [], work?: StageWork) => {
    const updated = await action.run(() => api.admin.workshop.move({ stageId, itemIds, pieceIds, work: work ?? null }));
    setLeaving(null);
    if (updated) {
      board.setValue(updated);
      setSelected(new Set());
      setSelectedPieces(new Set());
    } else {
      // The error shows on the page (the prompt is closed so it is not hidden behind it); reload so a piece that was
      // published or removed meanwhile ("That piece is no longer on the board") drops off.
      board.reload();
    }
  };
  /** Replaces one piece everywhere on the board after it changed (assignee, photos, note). */
  const replacePiece = (updated: WorkPiece) => {
    if (board.value) {
      board.setValue(
        board.value.map((c) => ({
          ...c,
          pieces: c.pieces.map((p) => (p.pieceId === updated.pieceId ? updated : p)),
        })),
      );
    }
  };
  const assignPiece = async (piece: WorkPiece, staffId: number | null) => {
    const updated = await action.run(() => api.admin.workshop.assignPiece(piece.pieceId, staffId));
    if (updated) {
      replacePiece(updated);
      setJustAssigned(updated.assignedTo ? updated : null);
    } else {
      board.reload();
    }
  };
  /** One piece to the next stage: leaving a work stage asks who did it and what it cost. */
  const nextPiece = (piece: WorkPiece, stageId: number) => {
    if (piece.stage.kind === "WORK") setLeaving({ piece, stageId });
    else void moveTo(stageId, [], [piece.pieceId]);
  };
  const togglePiece = (pieceId: number) => {
    const next = new Set(selectedPieces);
    if (next.has(pieceId)) next.delete(pieceId);
    else next.add(pieceId);
    setSelectedPieces(next);
  };
  const assign = async (item: WorkItem, value: string) => {
    const updated = await action.run(() => api.admin.workshop.assign(item.itemId, value ? Number(value) : null));
    if (updated && board.value) {
      board.setValue(
        board.value.map((c) => ({
          ...c,
          items: c.items.map((i) => (i.itemId === updated.itemId ? updated : i)),
        })),
      );
    }
  };
  const toggle = (itemId: number) => {
    const next = new Set(selected);
    if (next.has(itemId)) next.delete(itemId);
    else next.add(itemId);
    setSelected(next);
  };

  return (
    <section className="mx-auto max-w-4xl">
      <PageHeader
        title={person ? `${person.name}'s work` : "Work board"}
        backHref={person ? "/workshop/board/" : "/workshop/"}
      />
      <div className="mb-4 flex flex-col gap-3">
        <SearchBox label="Search work" placeholder="Order, customer, garment or fabric" onSearch={setQ} />
        {board.value && (
          <ChipGroup
            label="Stage"
            options={columns.map((c) => ({
              value: String(c.stage.id),
              label: `${c.stage.name}${c.removed ? " (removed)" : ""} · ${c.items.length + c.pieces.length}`,
            }))}
            value={String(column?.stage.id ?? "")}
            onChange={(value) => {
              setChosen(Number(value));
              setSelected(new Set());
              setSelectedPieces(new Set());
            }}
          />
        )}
        {!staffId && (
          <label className="flex items-center gap-2 text-sm text-ink-soft">
            <input
              type="checkbox"
              className="size-4 accent-accent"
              checked={unassigned}
              onChange={(event) => setUnassigned(event.target.checked)}
            />
            Only work nobody has been given
          </label>
        )}
      </div>
      <ErrorNote>{board.error ?? action.error}</ErrorNote>

      {justAssigned?.assignedTo && (
        <div
          role="status"
          className="mb-3 flex flex-wrap items-center gap-3 rounded-2xl border border-accent/40 bg-panel p-3 text-sm"
        >
          <span>
            {justAssigned.productName} is with {justAssigned.assignedTo.name}.
          </span>
          <ShareButton piece={justAssigned} label={`Share with ${justAssigned.assignedTo.name} on WhatsApp`} />
          <button type="button" className="ml-auto text-ink-faint underline" onClick={() => setJustAssigned(null)}>
            Dismiss
          </button>
        </div>
      )}

      {selectedCount > 0 && (
        <div className="sticky top-2 z-20 mb-3 flex flex-wrap items-end gap-3 rounded-2xl border border-accent/40 bg-panel p-3">
          <span className="text-sm font-medium">{selectedCount} selected</span>
          <SelectField
            className="min-w-48"
            label="Move to"
            options={[
              { value: "", label: "Choose stage…" },
              ...columns
                .filter((c) => !c.removed && c.stage.id !== column?.stage.id)
                .map((c) => ({ value: String(c.stage.id), label: c.stage.name })),
            ]}
            value={target}
            onChange={setTarget}
          />
          <button
            type="button"
            className={buttonPrimary}
            disabled={!target || action.busy}
            onClick={() => void moveTo(Number(target), [...selected], [...selectedPieces])}
          >
            Move
          </button>
        </div>
      )}

      {!board.value ? (
        <LoadState loading={board.loading} error={null} />
      ) : items.length === 0 && pieces.length === 0 ? (
        <EmptyState icon={BoardIcon} title={`Nothing in ${column?.stage.name ?? "this stage"}`}>
          {q ? `No work matches “${q}”.` : "Pick another stage above."}
        </EmptyState>
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2">
          {items.map((item) => (
            <li
              key={item.itemId}
              className={cn(
                "rounded-2xl border bg-panel p-4",
                selected.has(item.itemId) ? "border-accent" : item.overdue ? "border-warn/50" : "border-line",
              )}
            >
              <div className="flex items-start gap-3">
                <input
                  type="checkbox"
                  className="mt-1 size-5 accent-accent"
                  aria-label={`Select ${item.description} of ${item.orderNumber}`}
                  checked={selected.has(item.itemId)}
                  onChange={() => toggle(item.itemId)}
                />
                <div className="min-w-0 flex-1">
                  <p className="font-medium">
                    {item.quantity > 1 && `${item.quantity} × `}
                    {item.description}
                  </p>
                  <p className="text-sm text-ink-soft">
                    <Link href={`/orders/view/?id=${item.orderId}`} className="text-accent-ink underline">
                      {item.orderNumber}
                    </Link>{" "}
                    · {item.clientName}
                    {item.colour && ` · ${item.colour}`}
                  </p>
                  <p className="mt-1 text-sm">
                    {item.overdue ? (
                      <Pill tone="warn">Overdue · {formatDate(item.deliveryDate)}</Pill>
                    ) : item.dueToday ? (
                      <Pill tone="accent">Due today</Pill>
                    ) : (
                      <span className="text-ink-soft">Due {formatDate(item.deliveryDate)}</span>
                    )}
                    {item.trialDate && <span className="text-ink-faint"> · trial {formatDate(item.trialDate)}</span>}
                  </p>
                </div>
                {item.clothPhotoKey && (
                  // eslint-disable-next-line @next/next/no-img-element -- static export, R2 serves the resized file
                  <img
                    src={mediaUrl(item.clothPhotoKey)}
                    alt={`Cloth for ${item.description}`}
                    className="size-14 rounded-xl border border-line object-cover"
                  />
                )}
              </div>
              <div className="mt-3 flex flex-wrap items-center gap-2">
                {item.stage.kind === "WORK" && staff.value && (
                  <select
                    aria-label={`Given to (${item.description}, ${item.orderNumber})`}
                    disabled={action.busy}
                    value={item.assignedTo ? String(item.assignedTo.id) : ""}
                    onChange={(event) => void assign(item, event.target.value)}
                    className="min-w-0 flex-1 rounded-xl border border-line-strong bg-field px-3 py-2 text-sm"
                  >
                    <option value="">Nobody yet</option>
                    {staff.value.map((s) => (
                      <option key={s.id} value={String(s.id)}>
                        {s.name} ({s.workingOn})
                      </option>
                    ))}
                  </select>
                )}
                {next && (
                  <button
                    type="button"
                    className="rounded-xl border border-line-strong px-3 py-2 text-sm font-medium text-accent-ink hover:bg-panel-2"
                    disabled={action.busy}
                    onClick={() => void moveTo(next.stage.id!, [item.itemId])}
                  >
                    {next.stage.name} →
                  </button>
                )}
              </div>
            </li>
          ))}
          {pieces.map((piece) => (
            <PieceCard
              key={`piece-${piece.pieceId}`}
              piece={piece}
              staff={staff.value}
              selected={selectedPieces.has(piece.pieceId)}
              busy={action.busy}
              nextStageName={next?.stage.name}
              onSelect={() => togglePiece(piece.pieceId)}
              onOpen={() => setOpenPiece(piece.pieceId)}
              onAssign={(id) => void assignPiece(piece, id)}
              onNext={() => next && nextPiece(piece, next.stage.id!)}
            />
          ))}
        </ul>
      )}
      {shownPiece && (
        <PieceSheet
          key={shownPiece.pieceId}
          piece={shownPiece}
          onChange={replacePiece}
          onClose={() => setOpenPiece(null)}
        />
      )}
      {leaving && (
        <StageWorkPrompt
          key={leaving.piece.pieceId}
          piece={leaving.piece}
          busy={action.busy}
          onSave={(work) => void moveTo(leaving.stageId, [], [leaving.piece.pieceId], work)}
          onSkip={() => void moveTo(leaving.stageId, [], [leaving.piece.pieceId])}
          onClose={() => setLeaving(null)}
        />
      )}
    </section>
  );
}

export default function WorkBoardPage() {
  return (
    <Suspense fallback={<p className="text-ink-soft">Loading…</p>}>
      <Board />
    </Suspense>
  );
}
