"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ApiError, type ProductMaking, type StaffMember, type StockItem, type WorkPiece } from "@vaabhi/api-client";
import { Button } from "@vaabhi/ui";
import { api } from "@/lib/api";
import { mediaUrl } from "@/lib/photo-upload";
import { rupees } from "@/lib/settings";

const input =
  "w-full rounded-md border border-line-strong bg-panel px-2.5 py-1.5 text-sm focus:border-accent focus:outline-none";
const label = "flex flex-col gap-1 text-sm font-medium";

/** Common kinds of work; anything else can be typed. */
const WORK_TYPES = ["Cutting", "Stitching", "Embroidery", "Hand work", "Dyeing", "Finishing", "Ironing"];

function problem(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 403) return "You do not have access to this.";
    return error.detail ?? error.message;
  }
  return error instanceof Error ? error.message : "Something went wrong";
}

const today = () => new Date().toISOString().slice(0, 10);
const amount = (value: string) => {
  const n = Number.parseFloat(value.replaceAll(",", ""));
  return Number.isFinite(n) ? n : Number.NaN;
};

/**
 * Traces a saved product back to the stock it was cut from (e.g. 2.5 m of a 10 m lot bought from a supplier) and
 * the people who worked on it. Using stock takes it out of stock; removing the line puts it back. Picking a fabric
 * hands its GSM and thread count to the editor (`onFabric`).
 */
export function ProductMakingSection({
  productId,
  onFabric,
}: Readonly<{
  productId?: number;
  onFabric: (fabric: Pick<StockItem, "gsm" | "threadCount">) => void;
}>) {
  const [making, setMaking] = useState<ProductMaking | null>(null);
  const [piece, setPiece] = useState<WorkPiece | null>(null);
  const [stock, setStock] = useState<StockItem[] | null>(null);
  const [staff, setStaff] = useState<StaffMember[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const [itemId, setItemId] = useState("");
  const [quantity, setQuantity] = useState("");
  const [worker, setWorker] = useState("");
  const [workType, setWorkType] = useState("Stitching");
  const [cost, setCost] = useState("");
  const [doneOn, setDoneOn] = useState(today());

  useEffect(() => {
    if (productId === undefined) return;
    let cancelled = false;
    api.admin
      .productMaking(productId)
      .then((m) => !cancelled && setMaking(m))
      .catch((e: unknown) => !cancelled && setError(problem(e)));
    // Its card on the workshop board, while it is being made from stock (no workshop access: nothing shown).
    api.admin.workshop
      .pieceByProduct(productId)
      .then((p) => !cancelled && setPiece(p))
      .catch(() => {});
    api.admin.stock
      .list()
      .then((items) => !cancelled && setStock(items))
      .catch(() => !cancelled && setStock([]));
    // Staff names to pick from; without staff access, names are typed in.
    api.admin.staff
      .list()
      .then((people) => !cancelled && setStaff(people))
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [productId]);

  async function run(call: () => Promise<ProductMaking>): Promise<boolean> {
    setBusy(true);
    setError(null);
    try {
      setMaking(await call());
      return true;
    } catch (e) {
      setError(problem(e));
      return false;
    } finally {
      setBusy(false);
    }
  }

  if (productId === undefined) {
    return (
      <section className="rounded-lg border border-line p-5" aria-labelledby="making">
        <h2 id="making" className="mb-1 font-semibold">
          Made from &amp; work done
        </h2>
        <p className="text-sm text-ink-soft">
          Save the product first, then record which fabric it was cut from and who stitched it.
        </p>
      </section>
    );
  }

  const chosen = stock?.find((s) => String(s.id) === itemId);
  const staffMatch = staff.find((s) => s.name.toLowerCase() === worker.trim().toLowerCase());

  async function addMaterial() {
    const qty = amount(quantity);
    if (!chosen || !(qty > 0)) {
      setError("Pick the stock item and how much was used.");
      return;
    }
    const added = await run(() =>
      api.admin.addProductMaterial(productId!, { stockItemId: chosen.id, quantity: qty, note: null }),
    );
    if (added) {
      onFabric({ gsm: chosen.gsm, threadCount: chosen.threadCount });
      setItemId("");
      setQuantity("");
      setStock(
        (items) =>
          items?.map((s) => (s.id === chosen.id ? { ...s, onHand: Math.round((s.onHand - qty) * 100) / 100 } : s)) ??
          null,
      );
    }
  }

  async function addWork() {
    const price = cost.trim() === "" ? 0 : amount(cost);
    if (!worker.trim() || !workType.trim() || !(price >= 0)) {
      setError("Enter who did the work, what they did and what it cost.");
      return;
    }
    const added = await run(() =>
      api.admin.addProductWork(productId!, {
        staffId: staffMatch?.id ?? null,
        workerName: staffMatch?.name ?? worker.trim(),
        workType: workType.trim(),
        costRupees: price,
        doneOn: doneOn || null,
        note: null,
      }),
    );
    if (added) {
      setWorker("");
      setCost("");
    }
  }

  return (
    <section className="rounded-lg border border-line p-5" aria-labelledby="making">
      <h2 id="making" className="mb-1 font-semibold">
        Made from &amp; work done
      </h2>
      <p className="mb-4 text-sm text-ink-soft">
        Which fabric this piece was cut from and who worked on it.
        {making && ` Cost so far: ${rupees(making.costRupees)}.`}
      </p>
      {piece && (
        <p className="mb-4 text-sm">
          In workshop: <span className="font-medium">{piece.stage.name}</span>
          {piece.assignedTo && ` · with ${piece.assignedTo.name}`} ·{" "}
          <Link href="/workshop/board/" className="text-accent-ink underline">
            Work board
          </Link>
        </p>
      )}
      {error && (
        <p role="alert" className="mb-3 text-sm text-bad">
          {error}
        </p>
      )}

      <h3 className="mb-2 text-sm font-semibold">Made from</h3>
      {making && making.materials.length > 0 && (
        <ul className="mb-3 divide-y divide-line rounded-md border border-line text-sm">
          {making.materials.map((m) => {
            const photo = stock?.find((s) => s.id === m.stockItemId)?.photos[0];
            return (
              <li key={m.id} className="flex flex-wrap items-center gap-x-3 gap-y-1 px-3 py-2">
                {photo && (
                  // eslint-disable-next-line @next/next/no-img-element -- static export, R2 serves the resized file
                  <img
                    src={mediaUrl(photo.key)}
                    alt={`Fabric: ${m.stockItemName}`}
                    className="size-10 rounded-md border border-line object-cover"
                  />
                )}
                <span className="font-medium">Cut from {m.stockItemName}</span>
                <span>
                  {m.quantity} {m.unit.toLowerCase()}
                </span>
                <span className="text-ink-soft">
                  {[
                    m.supplierName && `from ${m.supplierName}`,
                    m.gsm && `${m.gsm} GSM`,
                    m.threadCount && `${m.threadCount} TC`,
                    rupees(m.costRupees),
                  ]
                    .filter(Boolean)
                    .join(" · ")}
                </span>
                <button
                  type="button"
                  className="ml-auto text-sm text-bad underline disabled:opacity-50"
                  disabled={busy}
                  onClick={() => void run(() => api.admin.removeProductMaterial(productId, m.id))}
                >
                  Remove
                </button>
              </li>
            );
          })}
        </ul>
      )}
      {stock && stock.length === 0 ? (
        <p className="mb-4 text-sm text-ink-soft">No stock items yet. Add the fabric you bought on the Stock page.</p>
      ) : (
        <div className="mb-5 grid gap-3 md:grid-cols-[2fr_1fr_auto] md:items-end">
          <label className={label}>
            Stock item
            <select className={input} value={itemId} onChange={(e) => setItemId(e.target.value)}>
              <option value="">Pick fabric or material…</option>
              {stock?.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                  {s.supplierName ? ` (${s.supplierName})` : ""} · {s.onHand} {s.unit.toLowerCase()} left
                </option>
              ))}
            </select>
          </label>
          <label className={label}>
            Used{chosen ? ` (${chosen.unit.toLowerCase()})` : ""}
            <input
              className={input}
              inputMode="decimal"
              placeholder="2.5"
              value={quantity}
              onChange={(e) => setQuantity(e.target.value)}
            />
          </label>
          <Button variant="secondary" disabled={busy} onClick={() => void addMaterial()}>
            Add
          </Button>
        </div>
      )}

      <h3 className="mb-2 text-sm font-semibold">Work done</h3>
      {making && making.work.length > 0 && (
        <ul className="mb-3 divide-y divide-line rounded-md border border-line text-sm">
          {making.work.map((w) => (
            <li key={w.id} className="flex flex-wrap items-center gap-x-3 gap-y-1 px-3 py-2">
              <span className="font-medium">{w.workerName}</span>
              <span>{w.workType}</span>
              <span className="text-ink-soft">{[rupees(w.costRupees), w.doneOn].filter(Boolean).join(" · ")}</span>
              <button
                type="button"
                className="ml-auto text-sm text-bad underline disabled:opacity-50"
                disabled={busy}
                onClick={() => void run(() => api.admin.removeProductWork(productId, w.id))}
              >
                Remove
              </button>
            </li>
          ))}
        </ul>
      )}
      <div className="grid gap-3 md:grid-cols-[1.5fr_1fr_1fr_1fr_auto] md:items-end">
        <label className={label}>
          Who
          <input
            className={input}
            list="making-staff"
            placeholder="Staff name or outside tailor"
            value={worker}
            onChange={(e) => setWorker(e.target.value)}
          />
          <datalist id="making-staff">
            {staff.map((s) => (
              <option key={s.id} value={s.name} />
            ))}
          </datalist>
        </label>
        <label className={label}>
          Work
          <input
            className={input}
            list="making-work-types"
            value={workType}
            onChange={(e) => setWorkType(e.target.value)}
          />
          <datalist id="making-work-types">
            {WORK_TYPES.map((t) => (
              <option key={t} value={t} />
            ))}
          </datalist>
        </label>
        <label className={label}>
          Cost (₹)
          <input
            className={input}
            inputMode="decimal"
            placeholder="0"
            value={cost}
            onChange={(e) => setCost(e.target.value)}
          />
        </label>
        <label className={label}>
          Done on
          <input className={input} type="date" value={doneOn} onChange={(e) => setDoneOn(e.target.value)} />
        </label>
        <Button variant="secondary" disabled={busy} onClick={() => void addWork()}>
          Add
        </Button>
      </div>
    </section>
  );
}
