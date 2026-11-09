"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { ApiError, type AdminProduct, type CatalogOptions, type FieldError } from "@vaabhi/api-client";
import { Button, cn } from "@vaabhi/ui";
import { api } from "@/lib/api";
import { ProductMakingSection } from "./product-making";
import {
  mediaReady,
  ProductPhotos,
  releasePreview,
  uploadProblem,
  uploadQueued,
  type QueuedMedia,
} from "./product-photos";
import {
  dimensionFields,
  UNIT_LABELS,
  emptyState,
  errorsFor,
  formFromState,
  newKey,
  standardBlouseAddons,
  stateFromProduct,
  STATUS_LABELS,
  variantsForColour,
  type AddonRow,
  type EditorState,
  type NewColour,
  type VariantRow,
} from "@/lib/product-editor";

const STOREFRONT_URL = process.env.NEXT_PUBLIC_STOREFRONT_URL || "https://vaabhi.com";

const input =
  "w-full rounded-md border border-line-strong bg-panel px-2.5 py-1.5 text-sm focus:border-accent focus:outline-none";
const label = "flex flex-col gap-1 text-sm font-medium";
const card = "rounded-lg border border-line p-5";

type LoadState = { kind: "loading" } | { kind: "error"; message: string } | { kind: "ready" };

function describe(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401 || error.status === 403)
      return "Your session has expired or lacks admin access. Sign in again.";
    return error.detail ?? error.message;
  }
  return "Could not reach the API. Check your connection and try again.";
}

/**
 * Create (no id) or edit a product. Saving always keeps the current status; publishing is a separate step and
 * needs at least one photo (a video is optional). Photos and videos picked before the first save upload right after it.
 */
export function ProductEditor({ id, notice, problem }: Readonly<{ id?: number; notice?: string; problem?: string }>) {
  const router = useRouter();
  const [options, setOptions] = useState<CatalogOptions | null>(null);
  const [product, setProduct] = useState<AdminProduct | null>(null);
  const [state, setState] = useState<EditorState>(emptyState());
  const [load, setLoad] = useState<LoadState>({ kind: "loading" });
  const [errors, setErrors] = useState<FieldError[]>([]);
  const [message, setMessage] = useState<{ tone: "ok" | "error"; text: string } | null>(() => {
    if (problem) return { tone: "error", text: problem };
    return notice ? { tone: "ok", text: notice } : null;
  });
  const [busy, setBusy] = useState(false);
  const [queued, setQueued] = useState<QueuedMedia[]>([]);

  useEffect(() => {
    let cancelled = false;
    Promise.all([api.admin.catalogOptions(), id === undefined ? null : api.admin.getProduct(id)])
      .then(([loadedOptions, loadedProduct]) => {
        if (cancelled) return;
        setOptions(loadedOptions);
        if (loadedProduct) {
          setProduct(loadedProduct);
          setState(stateFromProduct(loadedProduct));
        } else {
          setState(emptyState(loadedOptions.categories[0]?.slug));
        }
        setLoad({ kind: "ready" });
      })
      .catch((error: unknown) => !cancelled && setLoad({ kind: "error", message: describe(error) }));
    return () => {
      cancelled = true;
    };
  }, [id]);

  const update = (patch: Partial<EditorState>) => setState((s) => ({ ...s, ...patch }));

  async function save(): Promise<AdminProduct | null> {
    setBusy(true);
    setErrors([]);
    setMessage(null);
    try {
      const form = formFromState(state);
      const saved = product ? await api.admin.updateProduct(product.id, form) : await api.admin.createProduct(form);
      setProduct(saved);
      setState(stateFromProduct(saved));
      setMessage({ tone: "ok", text: "Saved." });
      return saved;
    } catch (error) {
      if (error instanceof ApiError) setErrors(error.errors);
      setMessage({ tone: "error", text: describe(error) });
      return null;
    } finally {
      setBusy(false);
    }
  }

  /**
   * Uploads the photos and video picked before the first save, one at a time. Returns the product's media
   * afterwards and a line per file that failed.
   */
  async function uploadWaiting(saved: AdminProduct): Promise<{ media: AdminProduct["media"]; failed: string[] }> {
    let media = saved.media;
    const failed: string[] = [];
    if (queued.length === 0) return { media, failed };
    setBusy(true);
    const colours = [...new Set(saved.variants.map((v) => v.colourName))];
    const left: QueuedMedia[] = [];
    // Photos first: the video goes after them, and the first photo is the listing photo.
    for (const item of [...queued].sort((a, b) => Number(a.kind === "VIDEO") - Number(b.kind === "VIDEO"))) {
      setMessage({ tone: "ok", text: `Uploading ${item.file.name}…` });
      try {
        media = await uploadQueued(saved.id, item, saved.name, colours);
        releasePreview(item);
      } catch (error) {
        failed.push(`${item.file.name}: ${uploadProblem(error)}`);
        left.push(item);
      }
    }
    setQueued(left);
    setProduct((p) => (p ? { ...p, media } : p));
    setBusy(false);
    return { media, failed };
  }

  /** A new product moves to its edit URL once the whole action is done (the page reloads it from the API). */
  function done(saved: AdminProduct, text: string, failed: string[] = []) {
    const problemText = failed.length ? `Some files did not upload. ${failed.join(" · ")}` : undefined;
    if (product) {
      setMessage(problemText ? { tone: "error", text: problemText } : { tone: "ok", text });
    } else {
      const extra = problemText ? `&problem=${encodeURIComponent(problemText)}` : "";
      router.replace(`/products/edit/?id=${saved.id}&notice=${encodeURIComponent(text)}${extra}`);
    }
  }

  async function submit() {
    const saved = await save();
    if (!saved) return;
    const { failed } = await uploadWaiting(saved);
    done(saved, "Saved.", failed);
  }

  async function changeStatus(action: "publish" | "unpublish" | "archive") {
    const saved = await save();
    if (!saved) return;
    const { media, failed } = await uploadWaiting(saved);
    if (action === "publish") {
      if (failed.length) {
        done(saved, "Saved as a draft.", failed);
        return;
      }
      const ready = mediaReady(media, []);
      if (!ready.hasPhoto) {
        done(saved, "Saved as a draft.", ["Add at least one photo before publishing."]);
        return;
      }
    }
    setBusy(true);
    try {
      const changed = await api.admin.setStatus(saved.id, action);
      setProduct(changed);
      done(changed, `${STATUS_LABELS[changed.status]}.`);
    } catch (error) {
      if (error instanceof ApiError) setErrors(error.errors);
      setMessage({ tone: "error", text: describe(error) });
    } finally {
      setBusy(false);
    }
  }

  if (load.kind === "loading") return <p className="text-sm text-ink-soft">Loading…</p>;
  if (load.kind === "error" || !options) {
    return (
      <div className="flex flex-col items-start gap-3">
        <p className="text-sm text-bad">{load.kind === "error" ? load.message : "Something went wrong."}</p>
        <Link href="/login/" className="text-sm underline">
          Sign in
        </Link>
      </div>
    );
  }

  const status = product?.status ?? "DRAFT";
  const fieldError = (field: string) => errorsFor(errors, field)[0];
  const ready = mediaReady(product?.media ?? [], queued);
  const canPublish = ready.hasPhoto;
  const publishHint = ready.hasPhoto ? "" : "Add a photo to publish.";

  return (
    <form
      className="flex max-w-5xl flex-col gap-6"
      onSubmit={(event) => {
        event.preventDefault();
        void submit();
      }}
    >
      <header className="flex flex-wrap items-center gap-3">
        <Link href="/products/" className="text-sm text-ink-soft hover:underline">
          ← Products
        </Link>
        <h1 className="text-2xl font-semibold">{product ? product.name : "New product"}</h1>
        <StatusBadge status={status} />
        {status === "PUBLISHED" && product && (
          <a
            href={`${STOREFRONT_URL}/p/${product.slug}`}
            target="_blank"
            rel="noreferrer"
            className="text-sm underline"
          >
            View in store
          </a>
        )}
      </header>

      {message?.tone === "error" && (
        <div role="alert" className="rounded-md bg-bad-soft px-4 py-3 text-sm text-bad">
          <p>{message.text}</p>
          {errors.length > 1 && (
            <ul className="mt-2 list-disc pl-5">
              {errors.map((e) => (
                <li key={`${e.field}:${e.message}`}>
                  <span className="font-mono text-xs">{e.field}</span>: {e.message}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
      {message?.tone === "ok" && (
        <output className="block rounded-md bg-ok-soft px-4 py-3 text-sm text-ok">{message.text}</output>
      )}

      <section className={card} aria-labelledby="basics">
        <h2 id="basics" className="mb-4 font-semibold">
          Basics
        </h2>
        <div className="grid gap-4 md:grid-cols-2">
          <Field label="Name" error={fieldError("name")}>
            <input className={input} required value={state.name} onChange={(e) => update({ name: e.target.value })} />
          </Field>
          <Field label="Category" error={fieldError("categorySlug")}>
            <select
              className={input}
              value={state.categorySlug}
              onChange={(e) => update({ categorySlug: e.target.value })}
            >
              {options.categories.map((c) => (
                <option key={c.slug} value={c.slug}>
                  {c.name}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Subtitle (shown on the card)" error={fieldError("subtitle")}>
            <input className={input} value={state.subtitle} onChange={(e) => update({ subtitle: e.target.value })} />
          </Field>
          <Field label="Parcel weight per piece (grams)" error={fieldError("shippingWeightGrams")}>
            <input
              className={input}
              inputMode="numeric"
              placeholder="Shop default"
              value={state.shippingWeightGrams}
              onChange={(e) => update({ shippingWeightGrams: e.target.value })}
            />
          </Field>
          <Field label="Description" error={fieldError("description")} className="md:col-span-2">
            <textarea
              className={cn(input, "h-28")}
              value={state.description}
              onChange={(e) => update({ description: e.target.value })}
            />
          </Field>
          <Field label="Care instructions" error={fieldError("careInstructions")} className="md:col-span-2">
            <input
              className={input}
              placeholder="e.g. Dry clean only"
              value={state.careInstructions}
              onChange={(e) => update({ careInstructions: e.target.value })}
            />
          </Field>
        </div>
        {/* Rarely changed: open by default only when it has a problem to fix. */}
        <details className="mt-4 border-t border-line pt-4" open={Boolean(fieldError("slug"))}>
          <summary className="cursor-pointer text-sm font-medium text-ink-soft hover:text-ink">
            More details: web address
          </summary>
          <div className="mt-4 grid gap-4 md:grid-cols-3">
            <Field label="URL name" hint="Leave empty to generate it from the name" error={fieldError("slug")}>
              <input
                className={input}
                value={state.slug}
                placeholder="kanjeevaram-silk-saree"
                onChange={(e) => update({ slug: e.target.value })}
              />
            </Field>
          </div>
        </details>
      </section>

      <section className={card} aria-labelledby="measurements">
        <h2 id="measurements" className="mb-1 font-semibold">
          Fabric &amp; measurements
        </h2>
        <p className="mb-4 text-sm text-ink-soft">
          Shown on the product page. GSM and thread count fill in from the fabric you pick under &quot;Made from&quot;.
        </p>
        <div className="grid gap-4 md:grid-cols-3">
          <Field label="Fabric weight (GSM)" error={fieldError("gsm")}>
            <input
              className={input}
              inputMode="numeric"
              value={state.gsm}
              onChange={(e) => update({ gsm: e.target.value })}
            />
          </Field>
          <Field label="Thread count" error={fieldError("threadCount")}>
            <input
              className={input}
              inputMode="numeric"
              value={state.threadCount}
              onChange={(e) => update({ threadCount: e.target.value })}
            />
          </Field>
          {dimensionFields(state.categorySlug).map((field, i) => (
            <Field
              key={field.code}
              label={`${field.label} (${UNIT_LABELS[field.unit]})`}
              error={fieldError(`dimensions[${i}].value`)}
            >
              <input
                className={input}
                inputMode="decimal"
                value={state.dimensions[field.code] ?? ""}
                onChange={(e) => update({ dimensions: { ...state.dimensions, [field.code]: e.target.value } })}
              />
            </Field>
          ))}
        </div>
      </section>

      {state.categorySlug === "sarees" && (
        <section className={card} aria-labelledby="saree">
          <h2 id="saree" className="mb-4 font-semibold">
            Saree details
          </h2>
          <div className="grid gap-4 md:grid-cols-3">
            <Field label="Saree length (m)" error={fieldError("sareeLengthM")}>
              <input
                className={input}
                inputMode="decimal"
                value={state.sareeLengthM}
                onChange={(e) => update({ sareeLengthM: e.target.value })}
              />
            </Field>
            <label className="flex items-center gap-2 self-end pb-2 text-sm font-medium">
              <input
                type="checkbox"
                checked={state.blouseIncluded}
                onChange={(e) => update({ blouseIncluded: e.target.checked })}
              />
              <span>Blouse piece included</span>
            </label>
            {state.blouseIncluded && (
              <Field label="Blouse length (m)" error={fieldError("blouseLengthM")}>
                <input
                  className={input}
                  inputMode="decimal"
                  value={state.blouseLengthM}
                  onChange={(e) => update({ blouseLengthM: e.target.value })}
                />
              </Field>
            )}
          </div>
        </section>
      )}

      <section className={card} aria-labelledby="attributes">
        <h2 id="attributes" className="mb-1 font-semibold">
          Attributes
        </h2>
        <p className="mb-4 text-sm text-ink-soft">Shoppers filter by these in the store.</p>
        <div className="flex flex-col gap-4">
          {options.facets.map((facet) => {
            const selected = state.attributes[facet.code] ?? [];
            return (
              <fieldset key={facet.code}>
                <legend className="mb-2 text-sm font-medium">{facet.name}</legend>
                <div className="flex flex-wrap gap-2">
                  {facet.values.map((value) => {
                    const on = selected.includes(value.code);
                    return (
                      <label
                        key={value.code}
                        className={cn(
                          "cursor-pointer rounded-full border px-3 py-1 text-sm has-[:focus-visible]:outline-2",
                          on ? "border-accent bg-accent text-white" : "border-line-strong",
                        )}
                      >
                        <input
                          type="checkbox"
                          className="sr-only"
                          checked={on}
                          onChange={() =>
                            update({
                              attributes: {
                                ...state.attributes,
                                [facet.code]: on ? selected.filter((c) => c !== value.code) : [...selected, value.code],
                              },
                            })
                          }
                        />
                        {value.name}
                      </label>
                    );
                  })}
                </div>
                <FieldMessage message={fieldError(`attributes.${facet.code}`)} />
              </fieldset>
            );
          })}
        </div>
      </section>

      <Variants
        rows={state.variants}
        options={options}
        errors={errors}
        oneSize={state.categorySlug === "sarees" || state.categorySlug === "dupattas"}
        onChange={(variants) => update({ variants })}
      />

      <Addons rows={state.addons} errors={errors} onChange={(addons) => update({ addons })} />

      <ProductPhotos
        productId={product?.id}
        productName={state.name}
        colours={[...new Set(state.variants.map((v) => v.colourName))]}
        media={product?.media ?? []}
        onChange={(media) => setProduct((p) => (p ? { ...p, media } : p))}
        queued={queued}
        onQueue={setQueued}
      />

      <ProductMakingSection
        productId={product?.id}
        onFabric={(fabric) =>
          setState((s) => ({
            ...s,
            gsm: s.gsm || (fabric.gsm == null ? "" : String(fabric.gsm)),
            threadCount: s.threadCount || (fabric.threadCount == null ? "" : String(fabric.threadCount)),
          }))
        }
      />

      {/* Kept in reach at the bottom of the screen; on phones it sits above the app's tab bar. */}
      <div
        role="group"
        aria-label="Product actions"
        className="sticky bottom-[calc(4.5rem+env(safe-area-inset-bottom))] z-20 -mx-4 flex flex-wrap items-center justify-end gap-2 border-t border-line bg-app/95 px-4 py-3 backdrop-blur md:bottom-0 md:-mx-8 md:px-8"
      >
        {status !== "PUBLISHED" && publishHint && <span className="mr-auto text-xs text-ink-soft">{publishHint}</span>}
        {status !== "ARCHIVED" && product && (
          <Button variant="secondary" disabled={busy} onClick={() => void changeStatus("archive")}>
            Archive
          </Button>
        )}
        {status === "PUBLISHED" ? (
          <Button variant="secondary" disabled={busy} onClick={() => void changeStatus("unpublish")}>
            Unpublish
          </Button>
        ) : (
          <Button variant="secondary" disabled={busy || !canPublish} onClick={() => void changeStatus("publish")}>
            Publish
          </Button>
        )}
        <Button type="submit" disabled={busy}>
          {busy ? "Saving…" : "Save"}
        </Button>
      </div>
    </form>
  );
}

function StatusBadge({ status }: Readonly<{ status: AdminProduct["status"] }>) {
  return (
    <span
      className={cn(
        "rounded-full px-2.5 py-0.5 text-xs font-medium",
        status === "PUBLISHED" && "bg-ok-soft text-ok",
        status === "DRAFT" && "bg-warn-soft text-warn",
        status === "ARCHIVED" && "bg-panel-2 text-ink",
      )}
    >
      {STATUS_LABELS[status]}
    </span>
  );
}

function Field({
  label: text,
  hint,
  error,
  className,
  children,
}: Readonly<{ label: string; hint?: string; error?: string; className?: string; children: React.ReactNode }>) {
  return (
    <label className={cn(label, className)}>
      <span>{text}</span>
      {children}
      {hint && !error && <span className="text-xs font-normal text-ink-soft">{hint}</span>}
      <FieldMessage message={error} />
    </label>
  );
}

function FieldMessage({ message }: Readonly<{ message?: string }>) {
  return message ? <span className="text-xs font-normal text-bad">{message}</span> : null;
}

function Variants({
  rows,
  options,
  errors,
  oneSize,
  onChange,
}: Readonly<{
  rows: VariantRow[];
  options: CatalogOptions;
  errors: FieldError[];
  oneSize: boolean;
  onChange: (rows: VariantRow[]) => void;
}>) {
  const [colour, setColour] = useState<NewColour>({
    colourName: "",
    colourHex: "#6E2A35",
    colourFamily: options.colourFamilies[0]?.code ?? "",
    price: "",
    mrp: "",
  });
  const [sizes, setSizes] = useState<string[]>([]);
  const set = (index: number, patch: Partial<VariantRow>) =>
    onChange(rows.map((row, i) => (i === index ? { ...row, ...patch } : row)));
  const general = errors.filter((e) => e.field === "variants").map((e) => e.message);

  return (
    <section className={card} aria-labelledby="variants">
      <h2 id="variants" className="mb-1 font-semibold">
        Variants
      </h2>
      <p className="mb-4 text-sm text-ink-soft">
        One row per colour{oneSize ? "" : " and size"}. Prices in rupees. SKUs are generated when left empty.
      </p>
      {general.map((m) => (
        <p key={m} className="mb-3 text-sm text-bad">
          {m}
        </p>
      ))}

      <fieldset className="mb-5 grid gap-3 rounded-md bg-panel p-4 md:grid-cols-6">
        <legend className="sr-only">Add a colour</legend>
        <label className={cn(label, "md:col-span-2")}>
          <span>Colour name</span>
          <input
            className={input}
            value={colour.colourName}
            placeholder="Rani pink"
            onChange={(e) => setColour({ ...colour, colourName: e.target.value })}
          />
        </label>
        <label className={label}>
          <span>Swatch</span>
          <input
            type="color"
            className="h-9 w-full rounded-md border border-line-strong"
            value={colour.colourHex}
            onChange={(e) => setColour({ ...colour, colourHex: e.target.value.toUpperCase() })}
          />
        </label>
        <label className={label}>
          <span>Colour group</span>
          <select
            className={input}
            value={colour.colourFamily}
            onChange={(e) => setColour({ ...colour, colourFamily: e.target.value })}
          >
            {options.colourFamilies.map((c) => (
              <option key={c.code} value={c.code}>
                {c.name}
              </option>
            ))}
          </select>
        </label>
        <label className={label}>
          <span>Price ₹</span>
          <input
            className={input}
            inputMode="decimal"
            value={colour.price}
            onChange={(e) => setColour({ ...colour, price: e.target.value })}
          />
        </label>
        <label className={label}>
          <span>MRP ₹</span>
          <input
            className={input}
            inputMode="decimal"
            value={colour.mrp}
            onChange={(e) => setColour({ ...colour, mrp: e.target.value })}
          />
        </label>
        {!oneSize && (
          <fieldset className="md:col-span-5">
            <legend className="mb-1 text-sm font-medium">Sizes</legend>
            <div className="flex flex-wrap gap-3 text-sm">
              {options.sizes.map((size) => (
                <label key={size} className="flex items-center gap-1">
                  <input
                    type="checkbox"
                    checked={sizes.includes(size)}
                    onChange={() => setSizes(sizes.includes(size) ? sizes.filter((s) => s !== size) : [...sizes, size])}
                  />
                  {size}
                </label>
              ))}
            </div>
          </fieldset>
        )}
        <Button
          variant="secondary"
          className="self-end md:col-start-6"
          disabled={colour.colourName.trim() === ""}
          onClick={() => {
            onChange([...rows, ...variantsForColour(colour, oneSize ? [] : sizes)]);
            setColour({ ...colour, colourName: "" });
          }}
        >
          Add colour
        </Button>
      </fieldset>

      {rows.length === 0 ? (
        <p className="text-sm text-ink-soft">No variants yet. Add a colour above.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[820px] text-left text-sm">
            <thead className="text-xs uppercase text-ink-soft">
              <tr>
                <th className="py-2 pr-2 font-medium">Colour</th>
                <th className="py-2 pr-2 font-medium">Size</th>
                <th className="py-2 pr-2 font-medium">Price ₹</th>
                <th className="py-2 pr-2 font-medium">MRP ₹</th>
                <th className="py-2 pr-2 font-medium">Stock</th>
                <th className="py-2 pr-2 font-medium">SKU</th>
                <th className="py-2 pr-2 font-medium">Active</th>
                <th className="py-2 font-medium">
                  <span className="sr-only">Remove</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row, i) => {
                const rowErrors = errorsFor(errors, `variants[${i}]`);
                const name = [row.colourName, row.size].filter(Boolean).join(" ");
                return (
                  <tr key={row.key} className="border-t border-line align-top">
                    <td className="py-2 pr-2">
                      <span className="flex items-center gap-2">
                        <span
                          aria-hidden="true"
                          className="size-4 shrink-0 rounded-full border border-line-strong"
                          style={{ backgroundColor: row.colourHex }}
                        />
                        {row.colourName}
                      </span>
                      {rowErrors.map((m) => (
                        <span key={m} className="block text-xs text-bad">
                          {m}
                        </span>
                      ))}
                    </td>
                    <td className="py-2 pr-2">{row.size || "One size"}</td>
                    <td className="py-2 pr-2">
                      <input
                        aria-label={`Price for ${name}`}
                        className={cn(input, "w-24")}
                        inputMode="decimal"
                        value={row.price}
                        onChange={(e) => set(i, { price: e.target.value })}
                      />
                    </td>
                    <td className="py-2 pr-2">
                      <input
                        aria-label={`MRP for ${name}`}
                        className={cn(input, "w-24")}
                        inputMode="decimal"
                        value={row.mrp}
                        onChange={(e) => set(i, { mrp: e.target.value })}
                      />
                    </td>
                    <td className="py-2 pr-2">
                      <input
                        aria-label={`Stock for ${name}`}
                        className={cn(input, "w-20")}
                        inputMode="numeric"
                        value={row.stock}
                        onChange={(e) => set(i, { stock: e.target.value })}
                      />
                    </td>
                    <td className="py-2 pr-2">
                      <input
                        aria-label={`SKU for ${name}`}
                        className={cn(input, "w-36 font-mono text-xs")}
                        placeholder="auto"
                        value={row.sku}
                        onChange={(e) => set(i, { sku: e.target.value })}
                      />
                    </td>
                    <td className="py-2 pr-2">
                      <input
                        type="checkbox"
                        aria-label={`${name} is for sale`}
                        checked={row.active}
                        onChange={(e) => set(i, { active: e.target.checked })}
                      />
                    </td>
                    <td className="py-2">
                      <button
                        type="button"
                        className="text-ink-soft hover:text-bad"
                        aria-label={`Remove ${name}`}
                        onClick={() => onChange(rows.filter((_, j) => j !== i))}
                      >
                        ✕
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

const blankAddon = (): AddonRow => ({
  key: newKey(),
  code: "",
  name: "",
  description: "",
  price: "0",
  input: "NONE",
  active: true,
});

/**
 * The add-ons shoppers pick from on the product page (shown there as "Add-ons", with a free "No add-on" first
 * when none is free). Codes are made from the name on save, so the admin only names and prices each one.
 */
function Addons({
  rows,
  errors,
  onChange,
}: Readonly<{ rows: AddonRow[]; errors: FieldError[]; onChange: (rows: AddonRow[]) => void }>) {
  const set = (index: number, patch: Partial<AddonRow>) =>
    onChange(rows.map((row, i) => (i === index ? { ...row, ...patch } : row)));
  const presets = standardBlouseAddons().filter((p) => !rows.some((r) => r.code === p.code || r.name === p.name));

  return (
    <section className={card} aria-labelledby="addons">
      <div className="mb-4 flex flex-wrap items-start justify-between gap-2">
        <div>
          <h2 id="addons" className="font-semibold">
            Add-ons
          </h2>
          <p className="text-sm text-ink-soft">
            Optional extras shoppers can choose on the product page. Their price is added to the item; 0 means included.
          </p>
        </div>
        <Button variant="secondary" onClick={() => onChange([...rows, blankAddon()])}>
          Add add-on
        </Button>
      </div>

      {presets.length > 0 && (
        <div className="mb-4 flex flex-wrap items-center gap-2">
          <span className="text-xs font-semibold uppercase tracking-[0.12em] text-ink-faint">Quick add</span>
          {presets.map((preset) => (
            <button
              key={preset.code}
              type="button"
              onClick={() => onChange([...rows, preset])}
              className="rounded-full border border-line-strong px-3 py-1 text-sm hover:border-accent hover:text-accent-ink"
            >
              + {preset.name}
            </button>
          ))}
        </div>
      )}

      {rows.length === 0 && <p className="text-sm text-ink-soft">No add-ons. The item is sold as it is.</p>}

      <div className="flex flex-col gap-3">
        {rows.map((row, i) => {
          const rowErrors = errorsFor(errors, `addons[${i}]`);
          const n = i + 1;
          return (
            <fieldset
              key={row.key}
              className={cn(
                "grid gap-3 rounded-md border border-line p-3 sm:grid-cols-[1fr_7rem_11rem_auto]",
                !row.active && "opacity-60",
              )}
            >
              <legend className="sr-only">Add-on {n}</legend>
              <label className={label}>
                <span>Name</span>
                <input
                  className={input}
                  value={row.name}
                  placeholder="Stitched blouse, made to measure"
                  onChange={(e) => set(i, { name: e.target.value })}
                />
              </label>
              <label className={label}>
                <span>Price ₹</span>
                <input
                  className={input}
                  inputMode="decimal"
                  value={row.price}
                  onChange={(e) => set(i, { price: e.target.value })}
                />
              </label>
              <label className={label}>
                <span>Ask for</span>
                <select
                  className={input}
                  value={row.input}
                  onChange={(e) => set(i, { input: e.target.value as AddonRow["input"] })}
                >
                  <option value="NONE">Nothing</option>
                  <option value="SIZE">A standard size</option>
                  <option value="MEASUREMENTS">Measurements</option>
                </select>
              </label>
              <div className="flex items-end gap-3 pb-1.5">
                <label className="flex items-center gap-1.5 text-sm">
                  <input type="checkbox" checked={row.active} onChange={(e) => set(i, { active: e.target.checked })} />
                  <span>Shown</span>
                </label>
                <button
                  type="button"
                  className="text-ink-soft hover:text-bad"
                  aria-label={`Remove add-on ${n}`}
                  onClick={() => onChange(rows.filter((_, j) => j !== i))}
                >
                  ✕
                </button>
              </div>
              <label className={cn(label, "sm:col-span-4")}>
                <span>Note</span>
                <input
                  className={input}
                  value={row.description}
                  placeholder="Shown under the name, e.g. delivery time"
                  onChange={(e) => set(i, { description: e.target.value })}
                />
              </label>
              {rowErrors.map((m) => (
                <p key={m} className="text-xs text-bad sm:col-span-4">
                  {m}
                </p>
              ))}
            </fieldset>
          );
        })}
      </div>
    </section>
  );
}
