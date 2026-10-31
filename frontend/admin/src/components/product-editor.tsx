"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { ApiError, type AdminProduct, type CatalogOptions, type FieldError } from "@vaabhi/api-client";
import { Button, cn } from "@vaabhi/ui";
import { api } from "@/lib/api";
import { ProductPhotos } from "./product-photos";
import {
  BADGES,
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
  "w-full rounded-md border border-neutral-300 bg-white px-2.5 py-1.5 text-sm focus:border-neutral-900 focus:outline-none";
const label = "flex flex-col gap-1 text-sm font-medium";
const card = "rounded-lg border border-neutral-200 p-5";

type LoadState = { kind: "loading" } | { kind: "error"; message: string } | { kind: "ready" };

function describe(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401 || error.status === 403)
      return "Your session has expired or lacks admin access. Sign in again.";
    return error.detail ?? error.message;
  }
  return "Could not reach the API. Check your connection and try again.";
}

/** Create (no id) or edit a product. Saving always keeps the current status; publishing is a separate step. */
export function ProductEditor({ id, notice }: Readonly<{ id?: number; notice?: string }>) {
  const router = useRouter();
  const [options, setOptions] = useState<CatalogOptions | null>(null);
  const [product, setProduct] = useState<AdminProduct | null>(null);
  const [state, setState] = useState<EditorState>(emptyState());
  const [load, setLoad] = useState<LoadState>({ kind: "loading" });
  const [errors, setErrors] = useState<FieldError[]>([]);
  const [message, setMessage] = useState<{ tone: "ok" | "error"; text: string } | null>(
    notice ? { tone: "ok", text: notice } : null,
  );
  const [busy, setBusy] = useState(false);

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

  /** A new product moves to its edit URL once the whole action is done (the page reloads it from the API). */
  function done(saved: AdminProduct, text: string) {
    if (product) {
      setMessage({ tone: "ok", text });
    } else {
      router.replace(`/products/edit/?id=${saved.id}&notice=${encodeURIComponent(text)}`);
    }
  }

  async function submit() {
    const saved = await save();
    if (saved) done(saved, "Saved.");
  }

  async function changeStatus(action: "publish" | "unpublish" | "archive") {
    const saved = await save();
    if (!saved) return;
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

  if (load.kind === "loading") return <p className="text-sm text-neutral-500">Loading…</p>;
  if (load.kind === "error" || !options) {
    return (
      <div className="flex flex-col items-start gap-3">
        <p className="text-sm text-red-700">{load.kind === "error" ? load.message : "Something went wrong."}</p>
        <Link href="/login/" className="text-sm underline">
          Sign in
        </Link>
      </div>
    );
  }

  const status = product?.status ?? "DRAFT";
  const fieldError = (field: string) => errorsFor(errors, field)[0];

  return (
    <form
      className="flex max-w-5xl flex-col gap-6"
      onSubmit={(event) => {
        event.preventDefault();
        void submit();
      }}
    >
      <header className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <Link href="/products/" className="text-sm text-neutral-500 hover:underline">
            ← Products
          </Link>
          <h1 className="text-2xl font-semibold">{product ? product.name : "New product"}</h1>
          <StatusBadge status={status} />
        </div>
        <div className="flex flex-wrap items-center gap-2">
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
            <Button variant="secondary" disabled={busy} onClick={() => void changeStatus("publish")}>
              Save &amp; publish
            </Button>
          )}
          <Button type="submit" disabled={busy}>
            {busy ? "Saving…" : "Save"}
          </Button>
        </div>
      </header>

      {message?.tone === "error" && (
        <div role="alert" className="rounded-md bg-red-50 px-4 py-3 text-sm text-red-800">
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
        <output className="block rounded-md bg-green-50 px-4 py-3 text-sm text-green-800">{message.text}</output>
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
          <Field label="Badge" error={fieldError("badge")}>
            <select
              className={input}
              value={state.badge}
              onChange={(e) => update({ badge: e.target.value as EditorState["badge"] })}
            >
              <option value="">None</option>
              {BADGES.map((b) => (
                <option key={b} value={b}>
                  {b.charAt(0) + b.slice(1).toLowerCase()}
                </option>
              ))}
            </select>
          </Field>
          <Field label="URL name" hint="Leave empty to generate it from the name" error={fieldError("slug")}>
            <input
              className={input}
              value={state.slug}
              placeholder="kanjeevaram-silk-saree"
              onChange={(e) => update({ slug: e.target.value })}
            />
          </Field>
          <Field label="Care instructions" error={fieldError("careInstructions")}>
            <input
              className={input}
              value={state.careInstructions}
              onChange={(e) => update({ careInstructions: e.target.value })}
            />
          </Field>
          <Field label="Description" error={fieldError("description")} className="md:col-span-2">
            <textarea
              className={cn(input, "h-28")}
              value={state.description}
              onChange={(e) => update({ description: e.target.value })}
            />
          </Field>
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
        <p className="mb-4 text-sm text-neutral-500">Shoppers filter by these in the store.</p>
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
                          on ? "border-neutral-900 bg-neutral-900 text-white" : "border-neutral-300",
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
      />
    </form>
  );
}

function StatusBadge({ status }: Readonly<{ status: AdminProduct["status"] }>) {
  return (
    <span
      className={cn(
        "rounded-full px-2.5 py-0.5 text-xs font-medium",
        status === "PUBLISHED" && "bg-green-100 text-green-800",
        status === "DRAFT" && "bg-amber-100 text-amber-900",
        status === "ARCHIVED" && "bg-neutral-200 text-neutral-700",
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
      {hint && !error && <span className="text-xs font-normal text-neutral-500">{hint}</span>}
      <FieldMessage message={error} />
    </label>
  );
}

function FieldMessage({ message }: Readonly<{ message?: string }>) {
  return message ? <span className="text-xs font-normal text-red-700">{message}</span> : null;
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
      <p className="mb-4 text-sm text-neutral-500">
        One row per colour{oneSize ? "" : " and size"}. Prices in rupees. SKUs are generated when left empty.
      </p>
      {general.map((m) => (
        <p key={m} className="mb-3 text-sm text-red-700">
          {m}
        </p>
      ))}

      <fieldset className="mb-5 grid gap-3 rounded-md bg-neutral-50 p-4 md:grid-cols-6">
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
            className="h-9 w-full rounded-md border border-neutral-300"
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
        <p className="text-sm text-neutral-500">No variants yet. Add a colour above.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[820px] text-left text-sm">
            <thead className="text-xs uppercase text-neutral-500">
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
                  <tr key={row.key} className="border-t border-neutral-100 align-top">
                    <td className="py-2 pr-2">
                      <span className="flex items-center gap-2">
                        <span
                          aria-hidden="true"
                          className="size-4 shrink-0 rounded-full border border-neutral-300"
                          style={{ backgroundColor: row.colourHex }}
                        />
                        {row.colourName}
                      </span>
                      {rowErrors.map((m) => (
                        <span key={m} className="block text-xs text-red-700">
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
                        className="text-neutral-500 hover:text-red-700"
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

function Addons({
  rows,
  errors,
  onChange,
}: Readonly<{ rows: AddonRow[]; errors: FieldError[]; onChange: (rows: AddonRow[]) => void }>) {
  const set = (index: number, patch: Partial<AddonRow>) =>
    onChange(rows.map((row, i) => (i === index ? { ...row, ...patch } : row)));

  return (
    <section className={card} aria-labelledby="addons">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
        <div>
          <h2 id="addons" className="font-semibold">
            Blouse options
          </h2>
          <p className="text-sm text-neutral-500">Choices shown on the product page; the price is added to the item.</p>
        </div>
        <div className="flex gap-2">
          {rows.length === 0 && (
            <Button variant="secondary" onClick={() => onChange(standardBlouseAddons())}>
              Add standard blouse options
            </Button>
          )}
          <Button
            variant="secondary"
            onClick={() =>
              onChange([
                ...rows,
                { key: newKey(), code: "", name: "", description: "", price: "0", input: "NONE", active: true },
              ])
            }
          >
            Add option
          </Button>
        </div>
      </div>
      {rows.map((row, i) => {
        const rowErrors = errorsFor(errors, `addons[${i}]`);
        const n = i + 1;
        return (
          <fieldset key={row.key} className="mb-3 grid gap-3 border-t border-neutral-100 pt-3 md:grid-cols-12">
            <legend className="sr-only">Option {n}</legend>
            <label className={cn(label, "md:col-span-3")}>
              <span>Name</span>
              <input className={input} value={row.name} onChange={(e) => set(i, { name: e.target.value })} />
            </label>
            <label className={cn(label, "md:col-span-2")}>
              <span>Code</span>
              <input
                className={cn(input, "font-mono text-xs")}
                value={row.code}
                placeholder="blouse-custom"
                onChange={(e) => set(i, { code: e.target.value })}
              />
            </label>
            <label className={cn(label, "md:col-span-3")}>
              <span>Note</span>
              <input
                className={input}
                value={row.description}
                onChange={(e) => set(i, { description: e.target.value })}
              />
            </label>
            <label className={cn(label, "md:col-span-1")}>
              <span>Price ₹</span>
              <input
                className={input}
                inputMode="decimal"
                value={row.price}
                onChange={(e) => set(i, { price: e.target.value })}
              />
            </label>
            <label className={cn(label, "md:col-span-2")}>
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
            <div className="flex items-end justify-between gap-2 pb-1.5 md:col-span-1">
              <label className="flex items-center gap-1 text-sm">
                <input type="checkbox" checked={row.active} onChange={(e) => set(i, { active: e.target.checked })} />
                <span>On</span>
              </label>
              <button
                type="button"
                className="text-neutral-500 hover:text-red-700"
                aria-label={`Remove option ${n}`}
                onClick={() => onChange(rows.filter((_, j) => j !== i))}
              >
                ✕
              </button>
            </div>
            {rowErrors.map((m) => (
              <p key={m} className="text-xs text-red-700 md:col-span-12">
                {m}
              </p>
            ))}
          </fieldset>
        );
      })}
    </section>
  );
}
