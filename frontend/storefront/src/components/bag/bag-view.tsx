"use client";

import Link from "next/link";
import { useCallback, useEffect, useState, useSyncExternalStore } from "react";
import type { BagQuote } from "@vaabhi/api-client";
import { buttonClasses, chipClasses, cn } from "@vaabhi/ui";
import { DeliveryCheck } from "@/components/product/delivery-check";
import {
  addToWishlist,
  bagCount,
  bagStore,
  lineKey,
  MAX_QUANTITY,
  removeLine,
  restoreLine,
  setQuantity,
  type BagLine,
} from "@/lib/bag";
import { bagStitchingDays, fetchQuote, isBuyable, priceChanged } from "@/lib/bag-quote";
import { formatPrice } from "@/lib/format";
import { mediaUrl, shade } from "@/lib/listing";
import { SHIPPING_AND_RETURNS } from "@/lib/policies";

const rupees = (paise: number) => formatPrice(paise / 100);
const CATEGORIES = [
  ["sarees", "Sarees"],
  ["lehengas", "Lehengas"],
  ["kurta-sets", "Kurta sets"],
  ["salwar-suits", "Salwar suits"],
  ["dupattas", "Dupattas"],
] as const;

type Load = { state: "loading" } | { state: "ready"; quote: BagQuote } | { state: "error" };
type Undo = { text: string; restore: ReturnType<typeof removeLine> };

/** The bag: lines kept in this browser, priced and checked against the shop on every visit. */
export function BagView() {
  const lines = useSyncExternalStore(bagStore.subscribe, bagStore.read, bagStore.serverValue);
  const [load, setLoad] = useState<Load>({ state: "loading" });
  const [undo, setUndo] = useState<Undo | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const request = JSON.stringify(lines.map((l) => [l.sku, l.addonCode, l.quantity]));

  const refresh = useCallback(() => {
    if (!lines.length) return;
    let cancelled = false;
    fetchQuote(lines)
      .then((quote) => !cancelled && setLoad({ state: "ready", quote }))
      .catch(() => !cancelled && setLoad({ state: "error" }));
    return () => {
      cancelled = true;
    };
    // `request` captures what is priced; prices do not depend on other line fields.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [request]);

  useEffect(() => refresh(), [refresh]);

  // When fewer are left than asked for, lower the quantity once and say so.
  useEffect(() => {
    if (load.state !== "ready") return;
    load.quote.lines.forEach((q, i) => {
      const line = lines[i];
      if (q.status === "LIMITED" && q.maxQuantity > 0 && line) {
        setQuantity(lineKey(line), q.maxQuantity);
        setNotice(`Only ${q.maxQuantity} left of ${q.name ?? line.name}, so we lowered the quantity.`);
      }
    });
    // Runs once per quote; `lines` is read at that moment.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [load]);

  if (!lines.length) return <EmptyBag />;

  const quote = load.state === "ready" && load.quote.lines.length === lines.length ? load.quote : null;
  const count = bagCount(lines);
  // Every line must be buyable at its quantity: LIMITED means more were asked for than are left.
  const blocked = quote?.lines.some((l) => l.status !== "AVAILABLE") ?? true;

  const remove = (line: BagLine, toWishlist: boolean) => {
    if (toWishlist) addToWishlist(line.slug);
    setUndo({
      text: toWishlist ? `Moved ${line.name} to your wishlist` : `Removed ${line.name}`,
      restore: removeLine(lineKey(line)),
    });
  };

  return (
    <div className="mx-auto w-full max-w-6xl px-4 pb-32 md:pb-12">
      <h1 className="mb-1 mt-5 font-serif text-[32px] font-semibold leading-tight">Your bag</h1>
      <p className="mb-4 text-sm text-text-muted">
        {count} {count === 1 ? "item" : "items"} · saved on this device
      </p>

      <div className="grid grid-cols-[minmax(0,1fr)] gap-5 md:grid-cols-[minmax(0,1fr)_360px] md:items-start md:gap-10">
        <section aria-label="Items in your bag">
          {load.state === "error" && (
            <p role="alert" className="mb-3 rounded-md bg-bg-muted px-3.5 py-3 text-sm">
              We could not check current prices.{" "}
              <button type="button" className="underline" onClick={() => refresh()}>
                Try again
              </button>
            </p>
          )}
          <ul className="flex flex-col">
            {lines.map((line, i) => {
              const q = quote?.lines[i];
              const buyable = !quote || isBuyable(q);
              const unit = q && buyable ? q.pricePaise + q.addonPricePaise : line.unitPricePaise + line.addonPricePaise;
              const mrp = q && buyable ? q.mrpPaise + q.addonPricePaise : unit;
              const max = q ? Math.min(MAX_QUANTITY, q.maxQuantity) : MAX_QUANTITY;
              const addonPaise = q && buyable ? q.addonPricePaise : line.addonPricePaise;
              const image = (q?.imageKey ?? line.imageKey) && mediaUrl((q?.imageKey ?? line.imageKey)!, 400);
              return (
                <li
                  key={lineKey(line)}
                  className="grid grid-cols-[96px_minmax(0,1fr)] gap-3.5 border-b border-border-default py-4 first:border-t"
                >
                  <Link href={`/p/${line.slug}`} className={cn(!buyable && "opacity-55")} aria-label={line.name}>
                    {image ? (
                      // eslint-disable-next-line @next/next/no-img-element -- R2 rendition
                      <img src={image} alt="" className="aspect-[3/4] w-24 rounded-sm object-cover" />
                    ) : (
                      <span
                        className="block aspect-[3/4] w-24 rounded-sm"
                        style={{
                          backgroundImage: `linear-gradient(180deg, ${line.colourHex}, ${shade(line.colourHex)})`,
                        }}
                      />
                    )}
                  </Link>
                  <div>
                    <div className={cn(!buyable && "opacity-55")}>
                      <h2 className="text-[15px] font-medium">
                        <Link href={`/p/${line.slug}`} className="hover:underline">
                          {q?.name ?? line.name}
                        </Link>
                      </h2>
                      <ul className="mt-1 flex flex-col gap-0.5 text-[13px] text-text-secondary">
                        <li>
                          <span
                            aria-hidden="true"
                            className="mr-1.5 inline-block size-2.5 rounded-full border border-border-default align-[-1px]"
                            style={{ backgroundColor: line.colourHex }}
                          />
                          {line.colourName}
                        </li>
                        <li>{line.size ? `Size ${line.size}` : "One size"}</li>
                        {line.addonName && (
                          <li>
                            {line.addonName}
                            {line.blouseSize ? `, size ${line.blouseSize}` : ""}{" "}
                            <span className="text-text-muted">
                              {addonPaise ? `(+${rupees(addonPaise)})` : "(included)"}
                            </span>
                          </li>
                        )}
                      </ul>
                    </div>
                    <LineNote line={line} quote={q} />
                    <div className="mt-2.5 flex flex-wrap items-center justify-between gap-3">
                      <div
                        role="group"
                        aria-label={`Quantity of ${line.name}`}
                        className="inline-flex items-center rounded-sm border border-border-strong bg-bg-surface"
                      >
                        <button
                          type="button"
                          aria-label="Decrease quantity"
                          disabled={line.quantity <= 1 || !buyable}
                          onClick={() => setQuantity(lineKey(line), line.quantity - 1)}
                          className="size-9 text-lg disabled:text-border-strong"
                        >
                          −
                        </button>
                        <output className="min-w-7 text-center tabular-nums">{line.quantity}</output>
                        <button
                          type="button"
                          aria-label="Increase quantity"
                          disabled={line.quantity >= max || !buyable}
                          onClick={() => setQuantity(lineKey(line), line.quantity + 1)}
                          className="size-9 text-lg disabled:text-border-strong"
                        >
                          +
                        </button>
                      </div>
                      <p className={cn("text-right tabular-nums", !buyable && "opacity-55")}>
                        <b className="block text-base font-semibold">{rupees(unit * line.quantity)}</b>
                        {line.quantity > 1 && (
                          <span className="block text-xs text-text-muted">{rupees(unit)} each</span>
                        )}
                        {mrp > unit && (
                          <s className="text-xs text-text-muted">
                            <span className="sr-only">MRP </span>
                            {rupees(mrp * line.quantity)}
                          </s>
                        )}
                      </p>
                    </div>
                    <div className="mt-2 flex gap-4 text-[13px] text-text-secondary">
                      <button
                        type="button"
                        className="underline hover:text-brand-primary"
                        onClick={() => remove(line, false)}
                      >
                        Remove
                      </button>
                      <button
                        type="button"
                        className="underline hover:text-brand-primary"
                        onClick={() => remove(line, true)}
                      >
                        Move to wishlist
                      </button>
                    </div>
                  </div>
                </li>
              );
            })}
          </ul>
        </section>

        <aside aria-labelledby="summary" className="md:sticky md:top-20">
          <div className="rounded-md border border-border-default bg-bg-surface p-4">
            <h2 id="summary" className="mb-3 font-serif text-[22px] font-semibold">
              Order summary
            </h2>
            {quote ? (
              <dl className="grid grid-cols-[1fr_auto] gap-x-3 gap-y-2 text-sm tabular-nums">
                <dt className="text-text-secondary">Items</dt>
                <dd className="text-right">{rupees(quote.itemsPaise)}</dd>
                {quote.addonsPaise > 0 && (
                  <>
                    <dt className="text-text-secondary">Blouse stitching</dt>
                    <dd className="text-right">{rupees(quote.addonsPaise)}</dd>
                  </>
                )}
                <dt className="text-text-secondary">Shipping</dt>
                <dd className="text-right text-text-secondary">Calculated at checkout</dd>
                {quote.mrpPaise > quote.totalPaise && (
                  <>
                    <dt className="text-feedback-success">You save on MRP</dt>
                    <dd className="text-right text-feedback-success">−{rupees(quote.mrpPaise - quote.totalPaise)}</dd>
                  </>
                )}
                <dt className="mt-1 border-t border-border-default pt-2.5 text-[17px] font-semibold">Total</dt>
                <dd className="mt-1 border-t border-border-default pt-2.5 text-right text-[17px] font-semibold">
                  {rupees(quote.totalPaise)}
                </dd>
              </dl>
            ) : (
              <p className="text-sm text-text-muted" aria-live="polite">
                {load.state === "error" ? "Prices could not be checked." : "Checking prices and stock…"}
              </p>
            )}
            <p className="mb-3.5 mt-1.5 text-xs text-text-muted">Prices include GST. Shipping is added at checkout.</p>
            {quote && blocked && (
              <p className="mb-2.5 text-[13px] text-feedback-error">Fix the items marked above to continue.</p>
            )}
            <CheckoutButton
              disabled={blocked}
              onClick={() => setNotice("Checkout opens with the next update. Your bag is saved on this device.")}
            />
            {notice && (
              <p role="status" className="mt-2 text-[13px] text-text-secondary">
                {notice}
              </p>
            )}
            <p className="mt-3 text-center text-xs text-text-muted">
              UPI · Cards · Netbanking · Cash on delivery
              <br />
              Payments by Razorpay
            </p>
            <div className="mt-3.5">
              <DeliveryCheck extraDays={bagStitchingDays(quote)} />
            </div>
            <ul className="mt-2 list-disc pl-4.5 text-[13px] text-text-secondary">
              {SHIPPING_AND_RETURNS.slice(1).map((l) => (
                <li key={l}>{l}</li>
              ))}
            </ul>
          </div>
        </aside>
      </div>

      <div className="fixed inset-x-0 bottom-0 z-30 flex items-center gap-3 border-t border-border-default bg-bg-surface px-4 py-3 md:hidden">
        <span className="flex flex-col tabular-nums">
          <strong className="text-[17px]">{quote ? rupees(quote.totalPaise) : "—"}</strong>
          <span className="text-xs text-text-muted">
            {blocked && quote ? "Fix marked items" : "Shipping at checkout"}
          </span>
        </span>
        <CheckoutButton
          compact
          disabled={blocked}
          onClick={() => setNotice("Checkout opens with the next update. Your bag is saved on this device.")}
        />
      </div>

      {undo && (
        <div
          role="status"
          className="fixed bottom-24 left-1/2 z-40 flex max-w-[calc(100%-32px)] -translate-x-1/2 items-center gap-3 rounded-md bg-text-primary py-2.5 pl-4 pr-3 text-sm text-text-on-brand md:bottom-8"
        >
          {undo.text}
          <button
            type="button"
            className="font-semibold text-brand-accent underline"
            onClick={() => {
              if (undo.restore) restoreLine(undo.restore);
              setUndo(null);
            }}
          >
            Undo
          </button>
        </div>
      )}
    </div>
  );
}

function LineNote({ line, quote }: Readonly<{ line: BagLine; quote: BagQuote["lines"][number] | undefined }>) {
  if (!quote) return null;
  if (!isBuyable(quote))
    return (
      <p className="mt-1.5 text-[13px] font-medium text-feedback-error">
        {quote.status === "OUT_OF_STOCK" ? "Out of stock." : "No longer available."} Remove it to check out.
      </p>
    );
  const notes: Array<[string, string]> = [];
  if (quote.status === "LIMITED")
    notes.push([
      `Only ${quote.maxQuantity} left. Lower the quantity to check out.`,
      "text-brand-festive font-semibold",
    ]);
  else if (quote.stockLeft) notes.push([`Only ${quote.stockLeft} left`, "text-brand-festive font-semibold"]);
  if (priceChanged(line, quote))
    notes.push([`Price updated from ${rupees(line.unitPricePaise + line.addonPricePaise)}`, "text-text-secondary"]);
  if (quote.addonInput === "SIZE" || quote.addonInput === "MEASUREMENTS")
    notes.push([
      `Stitching adds ${quote.addonInput === "SIZE" ? "5–7" : "7–10"} days · stitched blouses cannot be returned`,
      "text-text-muted",
    ]);
  return (
    <>
      {notes.map(([text, cls]) => (
        <p key={text} className={`mt-1.5 text-xs ${cls}`}>
          {text}
        </p>
      ))}
    </>
  );
}

function CheckoutButton({
  disabled,
  compact,
  onClick,
}: Readonly<{ disabled: boolean; compact?: boolean; onClick: () => void }>) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={onClick}
      className={buttonClasses("primary", compact ? "flex-1" : "w-full")}
    >
      {compact ? "Checkout" : "Checkout securely"}
    </button>
  );
}

function EmptyBag() {
  return (
    <section className="mx-auto max-w-6xl px-4 pb-20 pt-12 text-center">
      <h1 className="font-serif text-[32px] font-semibold">Your bag is empty</h1>
      <p className="mx-auto mb-5 mt-2 max-w-[40ch] text-text-muted">
        Handwoven sarees, lehengas and kurta sets are waiting. Anything you add stays here on this device.
      </p>
      <ul className="mb-6 flex flex-wrap justify-center gap-2">
        {CATEGORIES.map(([slug, name]) => (
          <li key={slug}>
            <Link href={`/c/${slug}`} className={chipClasses("default")}>
              {name}
            </Link>
          </li>
        ))}
      </ul>
      <Link href="/" className={buttonClasses("primary")}>
        Continue shopping
      </Link>
    </section>
  );
}
