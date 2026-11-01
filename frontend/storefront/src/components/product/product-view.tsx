"use client";

import Link from "next/link";
import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import type { ProductDetail } from "@vaabhi/api-client";
import { Badge, BagIcon, cn, ColourSwatch, HeartIcon } from "@vaabhi/ui";
import { Sheet } from "@/components/sheet";
import { addToBag, toggleWishlist, wishlistStore } from "@/lib/bag";
import { stitchingDays } from "@/lib/delivery";
import { formatPrice, discountLabel } from "@/lib/format";
import { badgeTones } from "@/lib/listing";
import { REASSURANCE } from "@/lib/policies";
import {
  addonChoices,
  BLOUSE_SIZE_GUIDE,
  BLOUSE_SIZES,
  colourOptions,
  defaultChoice,
  initialColour,
  isOneSize,
  oneSizeLine,
  photosFor,
  selectedVariant,
  stockLine,
  variantsOf,
} from "@/lib/product";
import { DeliveryCheck } from "./delivery-check";
import { ProductGallery } from "./product-gallery";

const rupees = (paise: number) => formatPrice(paise / 100);
const chip = (checked: boolean, disabled = false) =>
  cn(
    "inline-flex min-w-11 cursor-pointer items-center justify-center rounded-full border px-3.5 py-1.5 text-sm tabular-nums",
    "has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-brand-primary",
    checked ? "border-brand-primary bg-brand-primary text-text-on-brand" : "border-border-strong bg-bg-surface",
    disabled && "cursor-not-allowed text-text-muted line-through opacity-60",
  );

/** Everything the shopper chooses on the product page: colour, size, blouse option, then add to bag. */
export function ProductView({ product, colour: requested }: Readonly<{ product: ProductDetail; colour?: string }>) {
  const colours = colourOptions(product);
  const oneSize = isOneSize(product);
  const choices = addonChoices(product);

  const [colour, setColour] = useState(() => initialColour(product, requested));
  const [size, setSize] = useState<string | null>(null);
  const [choiceCode, setChoiceCode] = useState(() => defaultChoice(choices)?.code ?? null);
  const [blouseSize, setBlouseSize] = useState<string | null>(null);
  const [guideOpen, setGuideOpen] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const [added, setAdded] = useState(false);
  const [showSticky, setShowSticky] = useState(false);
  const actions = useRef<HTMLDivElement>(null);
  const wishlist = useSyncExternalStore(wishlistStore.subscribe, wishlistStore.read, wishlistStore.serverValue);
  const wished = wishlist.includes(product.slug);

  const variant = selectedVariant(product, colour, size);
  const shown = variant ?? variantsOf(product, colour)[0]!;
  const choice = choices.find((c) => c.code === choiceCode);
  const addonPrice = choice?.pricePaise ?? 0;
  const total = shown.pricePaise + addonPrice;
  const stock = stockLine(variant, colour);
  const allBlouse = product.addons.every((a) => a.kind === "BLOUSE");

  useEffect(() => {
    const el = actions.current;
    if (!el || typeof IntersectionObserver === "undefined") return;
    const observer = new IntersectionObserver(([entry]) =>
      setShowSticky(!entry!.isIntersecting && entry!.boundingClientRect.top < 0),
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  function add() {
    setAdded(false);
    if (!variant) return setProblem("Choose a size first.");
    if (!variant.inStock) return setProblem("This colour and size is out of stock.");
    if (choice?.input === "SIZE" && !blouseSize) return setProblem("Choose a blouse size.");
    setProblem(null);
    const photo = photosFor(product, colour)[0];
    addToBag({
      sku: variant.sku,
      slug: product.slug,
      name: product.name,
      colourName: colour,
      colourHex: variant.colourHex,
      size: variant.size,
      addonCode: choice?.code ?? null,
      addonName: choice?.code ? choice.name : null,
      blouseSize: choice?.input === "SIZE" ? blouseSize : null,
      unitPricePaise: variant.pricePaise,
      addonPricePaise: addonPrice,
      imageKey: photo?.key ?? null,
    });
    setAdded(true);
  }

  const wishButton = (extra: string) => (
    <button
      type="button"
      aria-pressed={wished}
      aria-label={wished ? "Remove from wishlist" : "Add to wishlist"}
      onClick={() => toggleWishlist(product.slug)}
      className={extra}
    >
      <HeartIcon size={20} className={wished ? "fill-brand-primary text-brand-primary" : ""} />
    </button>
  );

  return (
    <div className="grid md:grid-cols-[minmax(0,1.1fr)_minmax(0,1fr)] md:gap-10 md:px-4">
      <ProductGallery
        photos={photosFor(product, colour)}
        name={`${product.name}, ${colour}`}
        hex={shown.colourHex}
        overlay={
          <>
            {product.badge && (
              <Badge tone={badgeTones[product.badge]} className="absolute left-4 top-4">
                {product.badge.toLowerCase()}
              </Badge>
            )}
            {wishButton("absolute right-4 top-4 grid size-10 place-items-center rounded-full bg-bg-surface")}
          </>
        }
      />

      <section aria-labelledby="product-name" className="flex flex-col gap-5 px-4 py-4 md:px-0 md:pb-6 md:pt-0">
        <div>
          <h1
            id="product-name"
            className="text-balance font-serif text-[30px] font-semibold leading-tight md:text-[40px]"
          >
            {product.name}
          </h1>
          {product.subtitle && <p className="mt-1.5 text-sm text-text-muted">{product.subtitle}</p>}
          <p className="mt-3 flex flex-wrap items-baseline gap-2 tabular-nums">
            <span className="text-[26px] font-semibold">{rupees(shown.pricePaise)}</span>
            {shown.mrpPaise > shown.pricePaise && (
              <>
                <span className="text-text-muted line-through">
                  <span className="sr-only">MRP </span>
                  {rupees(shown.mrpPaise)}
                </span>
                <span className="font-semibold text-brand-primary">
                  {discountLabel(shown.pricePaise / 100, shown.mrpPaise / 100)}
                </span>
              </>
            )}
          </p>
          <p className="mt-1 text-xs text-text-muted">MRP inclusive of all taxes · free shipping above ₹1,999</p>
        </div>

        <hr className="border-border-default" />

        <fieldset>
          <legend className="mb-2.5 text-xs uppercase tracking-[0.12em] text-text-muted">
            Colour <span className="ml-1.5 normal-case tracking-normal font-semibold text-text-primary">{colour}</span>
          </legend>
          <div className="flex flex-wrap gap-3">
            {colours.map((c) => (
              <ColourSwatch
                key={c.name}
                kind="radio"
                group="colour"
                name={c.inStock ? c.name : `${c.name} (out of stock)`}
                hex={c.hex}
                checked={c.name === colour}
                onChange={() => {
                  setColour(c.name);
                  setAdded(false);
                }}
              />
            ))}
          </div>
          {oneSize && (
            <p className="mt-3 text-sm text-text-secondary">
              <span className="font-semibold">Size:</span> {oneSizeLine(product).replace(/^One size/, "one size")}
            </p>
          )}
        </fieldset>

        {!oneSize && (
          <fieldset>
            <legend className="mb-2.5 text-xs uppercase tracking-[0.12em] text-text-muted">
              Size{" "}
              {size && (
                <span className="ml-1.5 normal-case tracking-normal font-semibold text-text-primary">{size}</span>
              )}
            </legend>
            <div className="flex flex-wrap gap-2">
              {variantsOf(product, colour).map((v) => (
                <label key={v.sku} className={chip(v.size === size, !v.inStock)}>
                  <input
                    type="radio"
                    name="size"
                    className="sr-only"
                    checked={v.size === size}
                    disabled={!v.inStock}
                    onChange={() => {
                      setSize(v.size);
                      setProblem(null);
                      setAdded(false);
                    }}
                  />
                  {v.size}
                  {!v.inStock && <span className="sr-only"> (out of stock)</span>}
                </label>
              ))}
            </div>
          </fieldset>
        )}

        {choices.length > 0 && (
          <>
            <hr className="border-border-default" />
            <fieldset>
              <legend className="font-serif text-[22px] font-semibold">{allBlouse ? "Blouse" : "Options"}</legend>
              <p className="mb-3 mt-1 text-sm text-text-muted">
                {allBlouse ? "Choose how you’d like the blouse piece" : "Choose an option"}
              </p>
              <div className="flex flex-col gap-2.5">
                {choices.map((c) => {
                  const on = c.code === choiceCode;
                  return (
                    <div key={c.code ?? "none"}>
                      <label
                        className={cn(
                          "grid cursor-pointer grid-cols-[20px_1fr_auto] items-start gap-3 rounded-md border p-3.5",
                          "has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-brand-primary",
                          on
                            ? "border-[1.5px] border-brand-primary bg-brand-primary-soft"
                            : "border-border-strong bg-bg-surface",
                        )}
                      >
                        <input
                          type="radio"
                          name="addon"
                          className="peer sr-only"
                          checked={on}
                          onChange={() => {
                            setChoiceCode(c.code);
                            setProblem(null);
                            setAdded(false);
                          }}
                        />
                        <span
                          aria-hidden="true"
                          className={cn(
                            "mt-0.5 size-[18px] rounded-full bg-bg-surface",
                            on ? "border-[5px] border-brand-primary" : "border-[1.5px] border-border-strong",
                          )}
                        />
                        <span>
                          <span className={cn("block", on && "font-semibold")}>{c.name}</span>
                          {c.description && (
                            <span className={cn("block text-[13px]", on ? "text-text-secondary" : "text-text-muted")}>
                              {c.description}
                            </span>
                          )}
                        </span>
                        <span
                          className={cn(
                            "whitespace-nowrap text-sm font-medium tabular-nums",
                            on && "text-brand-primary",
                          )}
                        >
                          {c.pricePaise ? `+${rupees(c.pricePaise)}` : "Included"}
                        </span>
                      </label>
                      {on && c.input === "SIZE" && (
                        <fieldset className="pl-8 pt-2.5">
                          <legend className="sr-only">Blouse size</legend>
                          <div className="flex flex-wrap items-center gap-2">
                            {BLOUSE_SIZES.map((s) => (
                              <label key={s} className={chip(s === blouseSize)}>
                                <input
                                  type="radio"
                                  name="blouse-size"
                                  className="sr-only"
                                  checked={s === blouseSize}
                                  onChange={() => {
                                    setBlouseSize(s);
                                    setProblem(null);
                                  }}
                                />
                                {s}
                              </label>
                            ))}
                            <button
                              type="button"
                              onClick={() => setGuideOpen(true)}
                              className="text-sm text-brand-primary underline"
                            >
                              Size guide
                            </button>
                          </div>
                        </fieldset>
                      )}
                      {on && c.input === "MEASUREMENTS" && (
                        <p className="pl-8 pt-2 text-[13px] text-text-secondary">
                          After you order, we call you within a day to take your measurements.
                        </p>
                      )}
                    </div>
                  );
                })}
              </div>
            </fieldset>
          </>
        )}

        <p
          className={cn(
            "flex items-center gap-2 text-sm",
            stock.tone !== "neutral" && "font-semibold before:size-2 before:rounded-full before:bg-current",
            stock.tone === "low" && "text-brand-festive",
            stock.tone === "ok" && "text-feedback-success",
            stock.tone === "neutral" && "text-text-muted",
          )}
        >
          {stock.text}
        </p>

        <DeliveryCheck extraDays={stitchingDays(choice?.input)} />

        <p className="flex justify-between gap-3 text-sm text-text-secondary tabular-nums">
          <span>
            {product.category.name.replace(/s$/, "")} {rupees(shown.pricePaise)}
            {addonPrice > 0 && choice ? ` + ${choice.name.toLowerCase()} ${rupees(addonPrice)}` : ""}
          </span>
          <strong className="text-base text-text-primary">{rupees(total)}</strong>
        </p>

        <div ref={actions} className="flex gap-3">
          {wishButton(
            "grid place-items-center rounded-sm border border-brand-primary px-3.5 text-brand-primary hover:bg-brand-primary-soft",
          )}
          <button
            type="button"
            onClick={add}
            disabled={variant ? !variant.inStock : false}
            className="inline-flex flex-1 items-center justify-center gap-2 rounded-sm bg-brand-primary px-6 py-3.5 text-sm font-medium uppercase tracking-[0.04em] text-text-on-brand hover:bg-brand-primary-hover disabled:opacity-50"
          >
            <BagIcon size={20} />
            {variant && !variant.inStock ? "Out of stock" : "Add to bag"}
          </button>
        </div>
        <div aria-live="polite" className="-mt-2 min-h-5 text-sm">
          {problem && <p className="text-feedback-error">{problem}</p>}
          {added && (
            <p className="text-feedback-success">
              Added to bag.{" "}
              <Link href="/bag" className="underline">
                View bag
              </Link>
            </p>
          )}
        </div>
        <p className="-mt-3 flex flex-wrap gap-x-3.5 gap-y-1 text-xs text-text-muted">
          {REASSURANCE.map((r) => (
            <span key={r}>{r}</span>
          ))}
        </p>
      </section>

      <div
        aria-hidden={!showSticky}
        className={cn(
          "fixed inset-x-0 bottom-0 z-30 flex items-center gap-3 border-t border-border-default bg-bg-surface px-4 py-3 transition-transform md:hidden",
          showSticky ? "translate-y-0" : "translate-y-full",
        )}
      >
        <span className="flex flex-col tabular-nums">
          <strong>{rupees(total)}</strong>
          <span className="text-xs text-text-muted">{addonPrice ? "incl. blouse stitching" : "incl. all taxes"}</span>
        </span>
        <button
          type="button"
          tabIndex={showSticky ? 0 : -1}
          onClick={add}
          className="flex-1 rounded-sm bg-brand-primary px-6 py-3.5 text-sm font-medium uppercase tracking-[0.04em] text-text-on-brand"
        >
          Add to bag
        </button>
      </div>

      <Sheet open={guideOpen} onClose={() => setGuideOpen(false)} title="Blouse size guide" size="short">
        <div className="px-4 pb-6 pt-3">
          <p className="mb-3 text-sm text-text-muted">
            Measure around the fullest part of the bust. Between sizes? Choose the larger one.
          </p>
          <table className="w-full border-collapse text-sm tabular-nums">
            <thead>
              <tr className="text-left text-xs uppercase tracking-[0.06em] text-text-muted">
                <th className="border-b border-border-default px-2 py-2.5 font-medium">Size</th>
                <th className="border-b border-border-default px-2 py-2.5 font-medium">Bust (in)</th>
                <th className="border-b border-border-default px-2 py-2.5 font-medium">Waist (in)</th>
                <th className="border-b border-border-default px-2 py-2.5 font-medium">Length (in)</th>
              </tr>
            </thead>
            <tbody>
              {BLOUSE_SIZE_GUIDE.map((r) => (
                <tr key={r.size}>
                  <td className="border-b border-border-default px-2 py-2.5">{r.size}</td>
                  <td className="border-b border-border-default px-2 py-2.5">{r.bust}</td>
                  <td className="border-b border-border-default px-2 py-2.5">{r.waist}</td>
                  <td className="border-b border-border-default px-2 py-2.5">{r.length}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Sheet>
    </div>
  );
}
