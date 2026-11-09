"use client";

import Link from "next/link";
import { useState } from "react";
import type { MadePiece, StockItem } from "@vaabhi/api-client";
import { api } from "@/lib/api";
import { qty, unitLabel } from "@/lib/extras";
import { useAction, useLoad } from "@/lib/tailoring";
import { ErrorNote } from "./tailoring";
import { SelectField, Sheet, TextField, buttonPrimary, buttonSecondary } from "./ui";

type Row = { name: string; categorySlug: string; quantity: string };

const emptyRow = (categorySlug = ""): Row => ({ name: "", categorySlug, quantity: "" });

/** What is left in stock after cutting these pieces (rows without a quantity count as nothing). */
export function leftAfter(onHand: number, rows: { quantity: number | null }[]): number {
  const used = rows.reduce((sum, row) => sum + (row.quantity ?? 0), 0);
  return Math.round((onHand - used) * 100) / 100;
}

const parse = (text: string): number | null => {
  const n = Number(text);
  return text.trim() === "" || !Number.isFinite(n) || n <= 0 ? null : n;
};

/**
 * "Make products": cut a stock item into pieces. Each piece becomes a draft product and a card on the workshop
 * board, and its quantity comes out of stock.
 */
export function MakeProducts({ item, onMade }: { item: StockItem; onMade: (made: MadePiece[]) => void }) {
  const [open, setOpen] = useState(false);
  const [rows, setRows] = useState<Row[]>([emptyRow()]);
  const [made, setMade] = useState<MadePiece[] | null>(null);
  const options = useLoad(() => api.admin.catalogOptions(), []);
  const save = useAction();
  const categories = options.value?.categories ?? [];
  const defaultCategory = categories[0]?.slug ?? "";
  const unit = unitLabel(item.unit);

  const left = leftAfter(
    item.onHand,
    rows.map((row) => ({ quantity: parse(row.quantity) })),
  );
  const complete = rows.every((row) => row.name.trim() && (row.categorySlug || defaultCategory) && parse(row.quantity));
  const update = (index: number, change: Partial<Row>) =>
    setRows((current) => current.map((row, i) => (i === index ? { ...row, ...change } : row)));

  const close = () => {
    setOpen(false);
    setMade(null);
    setRows([emptyRow()]);
    save.setError(null);
  };

  const submit = async () => {
    const result = await save.run(() =>
      api.admin.workshop.makePieces({
        stockItemId: item.id,
        pieces: rows.map((row) => ({
          name: row.name.trim(),
          categorySlug: row.categorySlug || defaultCategory,
          quantity: parse(row.quantity)!,
          note: null,
        })),
      }),
    );
    if (result) {
      setMade(result);
      onMade(result);
    }
  };

  return (
    <>
      <button
        type="button"
        className={buttonPrimary}
        disabled={item.onHand <= 0}
        title={item.onHand <= 0 ? "Nothing left in stock" : undefined}
        onClick={() => setOpen(true)}
      >
        Make products
      </button>
      <Sheet open={open} title="Make products from this fabric" onClose={close}>
        {made ? (
          <div className="space-y-4">
            <p>
              {made.length === 1 ? "1 draft product is" : `${made.length} draft products are`} on the workshop board.
            </p>
            <ul className="divide-y divide-line rounded-2xl border border-line">
              {made.map((piece) => (
                <li key={piece.pieceId} className="px-4 py-3">
                  <Link
                    href={`/products/edit/?id=${piece.productId}`}
                    className="font-medium text-accent-ink underline"
                  >
                    {piece.productName}
                  </Link>
                </li>
              ))}
            </ul>
            <Link href="/workshop/board/" className={buttonPrimary}>
              Open workshop board
            </Link>
          </div>
        ) : (
          <div className="space-y-4">
            <p className="text-sm text-ink-soft">
              Each piece becomes a draft product and goes to the first work stage. {qty(item.onHand)} {unit} in stock.
            </p>
            <ErrorNote>{options.error}</ErrorNote>
            {rows.map((row, index) => (
              <fieldset key={index} className="grid gap-3 rounded-2xl border border-line p-3 sm:grid-cols-[1fr_8rem]">
                <legend className="px-1 text-sm font-medium text-ink-soft">Piece {index + 1}</legend>
                <TextField
                  label="Product name"
                  className="sm:col-span-2"
                  value={row.name}
                  maxLength={160}
                  onChange={(event) => update(index, { name: event.target.value })}
                />
                <SelectField
                  label="Category"
                  value={row.categorySlug || defaultCategory}
                  options={categories.map((c) => ({ value: c.slug, label: c.name }))}
                  onChange={(categorySlug) => update(index, { categorySlug })}
                />
                <TextField
                  label={`Quantity (${unit})`}
                  inputMode="decimal"
                  value={row.quantity}
                  onChange={(event) => update(index, { quantity: event.target.value })}
                />
                {rows.length > 1 && (
                  <button
                    type="button"
                    className="justify-self-start text-sm text-ink-faint underline hover:text-bad"
                    onClick={() => setRows((current) => current.filter((_, i) => i !== index))}
                  >
                    Remove piece
                  </button>
                )}
              </fieldset>
            ))}
            <div className="flex flex-wrap items-center gap-3">
              <button
                type="button"
                className={buttonSecondary}
                disabled={rows.length >= 20}
                onClick={() => setRows((current) => [...current, emptyRow(current.at(-1)?.categorySlug)])}
              >
                + piece
              </button>
              <span role="status" className={left < 0 ? "text-bad" : "text-ink-soft"}>
                Left after: {qty(left)} {unit}
              </span>
            </div>
            <ErrorNote>{save.error}</ErrorNote>
            <button
              type="button"
              className={buttonPrimary}
              disabled={save.busy || !complete || left < 0 || categories.length === 0}
              onClick={submit}
            >
              {save.busy ? "Making…" : "Make products"}
            </button>
          </div>
        )}
      </Sheet>
    </>
  );
}
