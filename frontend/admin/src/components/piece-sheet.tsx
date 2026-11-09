"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import type { StageWork, WorkPiece } from "@vaabhi/api-client";
import { api } from "@/lib/api";
import { qty, unitLabel } from "@/lib/extras";
import { fetchShareFiles, sharePiece, type ShareFile } from "@/lib/piece-share";
import { parseRupees } from "@/lib/settings";
import { uploadKeyedPhoto, useAction } from "@/lib/tailoring";
import { PhotoStrip } from "./photo-strip";
import { ErrorNote, Fact } from "./tailoring";
import { Sheet, TextAreaField, TextField, buttonPrimary, buttonSecondary } from "./ui";

/** "Share on WhatsApp": photos are fetched when the button appears so the share sheet opens right on the tap. */
export function ShareButton({ piece, label = "Share on WhatsApp" }: { piece: WorkPiece; label?: string }) {
  // Files are kept with the photo set they were fetched for, so a tap right after adding a photo never shares the
  // old set (it shares links until the new files arrive).
  const [prepared, setPrepared] = useState<{ keys: string; files: ShareFile[] } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const photoKeys = `${piece.pieceId}:${[...piece.photos.map((p) => p.key), piece.photoKey].join(",")}`;
  useEffect(() => {
    let cancelled = false;
    void fetchShareFiles(piece).then((files) => !cancelled && setPrepared({ keys: photoKeys, files }));
    return () => {
      cancelled = true;
    };
    // refetch only when the photos change, not on every update of the piece
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [photoKeys]);
  return (
    <>
      <button
        type="button"
        className={buttonSecondary}
        onClick={() => {
          setError(null);
          const files = prepared?.keys === photoKeys ? prepared.files : undefined;
          sharePiece(piece, files).catch((e: unknown) =>
            setError(e instanceof Error && e.message ? e.message : "Could not open WhatsApp"),
          );
        }}
      >
        {label}
      </button>
      {error && (
        <p role="alert" className="text-sm text-bad">
          {error}
        </p>
      )}
    </>
  );
}

/**
 * A piece on the board: design photos and note for the tailor, fabric and measurements, and sharing. Render it
 * with `key={piece.pieceId}` so the note field starts fresh for each piece.
 */
export function PieceSheet({
  piece,
  onChange,
  onClose,
}: {
  piece: WorkPiece;
  onChange: (piece: WorkPiece) => void;
  onClose: () => void;
}) {
  const [note, setNote] = useState(piece.note ?? "");
  const save = useAction();
  const fabric = piece.fabric;
  return (
    <Sheet open title={piece.productName} onClose={onClose}>
      <div className="space-y-4">
        <p className="text-sm text-ink-soft">
          {piece.stage.name}
          {piece.assignedTo && ` · with ${piece.assignedTo.name}`}
        </p>
        <div>
          <p className="mb-2 text-sm font-medium text-ink-soft">Design photos</p>
          <PhotoStrip
            label="Design photo"
            photos={piece.photos}
            onAdd={async (file) => {
              const key = await uploadKeyedPhoto(file, () => api.admin.workshop.startPiecePhotoUpload(piece.pieceId));
              onChange(await api.admin.workshop.addPiecePhoto(piece.pieceId, key));
            }}
            onRemove={async (photoId) => onChange(await api.admin.workshop.removePiecePhoto(piece.pieceId, photoId))}
          />
        </div>
        <div>
          <TextAreaField
            label="Note for the tailor"
            value={note}
            maxLength={200}
            onChange={(event) => setNote(event.target.value)}
          />
          {note !== (piece.note ?? "") && (
            <button
              type="button"
              className={`${buttonSecondary} mt-2`}
              disabled={save.busy}
              onClick={async () => {
                const updated = await save.run(() => api.admin.workshop.setPieceNote(piece.pieceId, note || null));
                if (updated) onChange(updated);
              }}
            >
              {save.busy ? "Saving…" : "Save note"}
            </button>
          )}
          <ErrorNote>{save.error}</ErrorNote>
        </div>
        <dl className="grid grid-cols-2 gap-3 rounded-2xl border border-line p-4 text-sm">
          <Fact label="Fabric">
            <Link href={`/stock/view/?id=${piece.stockItemId}`} className="text-accent-ink underline">
              {piece.stockItemName}
            </Link>
          </Fact>
          <Fact label="Used">
            {qty(piece.quantity)} {unitLabel(piece.unit)}
          </Fact>
          {(fabric.material || fabric.colour) && (
            <Fact label="Material">{[fabric.material, fabric.colour].filter(Boolean).join(", ")}</Fact>
          )}
          {fabric.widthIn && <Fact label="Width">{qty(fabric.widthIn)} in</Fact>}
          {fabric.gsm && <Fact label="GSM">{fabric.gsm}</Fact>}
          {piece.dimensions.map((d) => (
            <Fact key={d.label} label={d.label}>
              {qty(d.value)} {d.unit.toLowerCase()}
            </Fact>
          ))}
        </dl>
        <div className="flex flex-wrap gap-3">
          <ShareButton piece={piece} />
          <Link href={`/products/edit/?id=${piece.productId}`} className={buttonSecondary}>
            Open product
          </Link>
        </div>
      </div>
    </Sheet>
  );
}

/**
 * Asked when one piece leaves a work stage: who did it and what it cost (logged on the product), or skip. Render it
 * with `key={piece.pieceId}` so it starts prefilled with that piece's assignee.
 */
export function StageWorkPrompt({
  piece,
  busy,
  onSave,
  onSkip,
  onClose,
}: {
  piece: WorkPiece;
  busy: boolean;
  onSave: (work: StageWork) => void;
  onSkip: () => void;
  onClose: () => void;
}) {
  const [worker, setWorker] = useState(piece.assignedTo?.name ?? "");
  const [cost, setCost] = useState("");
  const rupees = parseRupees(cost);
  return (
    <Sheet open title={`Who did ${piece.stage.name.toLowerCase()}?`} onClose={onClose}>
      <div className="space-y-4">
        <p className="text-sm text-ink-soft">Logged on {piece.productName} as part of its making cost.</p>
        <TextField label="Done by" value={worker} maxLength={120} onChange={(event) => setWorker(event.target.value)} />
        <TextField
          label="Cost (₹)"
          inputMode="decimal"
          value={cost}
          onChange={(event) => setCost(event.target.value)}
        />
        <div className="flex flex-wrap gap-3">
          <button
            type="button"
            className={buttonPrimary}
            disabled={busy || !worker.trim() || rupees === null}
            onClick={() =>
              onSave({
                staffId: piece.assignedTo && piece.assignedTo.name === worker ? piece.assignedTo.id : null,
                workerName: worker.trim(),
                costRupees: rupees ?? 0,
              })
            }
          >
            Save and move
          </button>
          <button type="button" className={buttonSecondary} disabled={busy} onClick={onSkip}>
            Skip
          </button>
        </div>
      </div>
    </Sheet>
  );
}
