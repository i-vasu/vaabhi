"use client";

import { useState } from "react";
import type { GarmentTemplate, ProductionStage, ServiceProduct, ServiceProductForm } from "@vaabhi/api-client";
import { PlusIcon, TagIcon, TrashIcon } from "@/components/icons";
import { ErrorNote, PhotoInput, SearchBox } from "@/components/tailoring";
import {
  EmptyState,
  ListCard,
  ListRow,
  LoadState,
  PageHeader,
  SectionLabel,
  SelectField,
  Sheet,
  TextField,
  buttonPrimary,
  buttonSecondary,
} from "@/components/ui";
import { api } from "@/lib/api";
import { parseRupees, rupees } from "@/lib/settings";
import { useAction, useLoad } from "@/lib/tailoring";

type Editing = { product: ServiceProduct | null };

export default function PriceListPage() {
  const [q, setQ] = useState("");
  const [editing, setEditing] = useState<Editing | null>(null);
  const products = useLoad(() => api.admin.serviceProducts.list({ q: q || undefined }), [q]);
  const setup = useLoad(() => Promise.all([api.admin.settings.garmentTemplates(), api.admin.settings.stages()]), []);

  const groups = new Map<string, ServiceProduct[]>();
  for (const product of products.value ?? []) {
    groups.set(product.garment, [...(groups.get(product.garment) ?? []), product]);
  }

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader
        title="Price list"
        backHref="/more/"
        actions={
          <button type="button" className={buttonPrimary} onClick={() => setEditing({ product: null })}>
            <PlusIcon size={18} /> Add item
          </button>
        }
      />
      <p className="mb-4 text-ink-soft">
        What the shop stitches and for how much. Add each stage&apos;s piece rate to see the profit per piece.
      </p>
      <div className="mb-4">
        <SearchBox label="Search price list" placeholder="Name, code or garment" onSearch={setQ} />
      </div>
      <ErrorNote>{products.error}</ErrorNote>
      {!products.value ? (
        <LoadState loading={products.loading} error={null} />
      ) : products.value.length === 0 ? (
        <EmptyState icon={TagIcon} title={q ? `Nothing matches “${q}”` : "No items yet"}>
          Add what you stitch so New order can fill the price in.
        </EmptyState>
      ) : (
        [...groups.entries()].map(([garment, items]) => (
          <div key={garment}>
            <SectionLabel>{garment}</SectionLabel>
            <ListCard>
              {items.map((product) => (
                <button
                  key={product.id}
                  type="button"
                  className="block w-full text-left hover:bg-panel-2"
                  onClick={() => setEditing({ product })}
                >
                  <ListRow
                    title={product.name}
                    subtitle={
                      product.stageCosts.length > 0
                        ? `${product.code} · profit ${rupees(product.profitRupees)}`
                        : product.code
                    }
                    trailing={<span className="font-medium text-ink">{rupees(product.priceRupees)}</span>}
                  />
                </button>
              ))}
            </ListCard>
          </div>
        ))
      )}
      {editing && setup.value && (
        <ProductSheet
          product={editing.product}
          templates={setup.value[0]}
          stages={setup.value[1].filter((stage) => stage.kind === "WORK")}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            products.reload();
          }}
        />
      )}
    </section>
  );
}

function ProductSheet({
  product,
  templates,
  stages,
  onClose,
  onSaved,
}: {
  product: ServiceProduct | null;
  templates: GarmentTemplate[];
  stages: ProductionStage[];
  onClose: () => void;
  onSaved: () => void;
}) {
  const [templateId, setTemplateId] = useState(product?.templateId ?? templates[0]?.id ?? 0);
  const [name, setName] = useState(product?.name ?? "");
  const [code, setCode] = useState(product?.code ?? "");
  const [price, setPrice] = useState(product ? String(product.priceRupees) : "");
  const [photoKey, setPhotoKey] = useState<string | null>(product?.photoKey ?? null);
  const [costs, setCosts] = useState<Record<number, string>>(() =>
    Object.fromEntries((product?.stageCosts ?? []).map((cost) => [cost.stageId, String(cost.rupees)])),
  );
  const action = useAction();

  const priceRupees = parseRupees(price);
  const costRupees = Object.values(costs).reduce((sum, text) => sum + (parseRupees(text) ?? 0), 0);

  async function save() {
    if (!name.trim()) return action.setError("Enter the name.");
    if (priceRupees === null) return action.setError("Enter the price.");
    const form: ServiceProductForm = {
      templateId,
      code: code.trim() || null,
      name: name.trim(),
      priceRupees,
      photoKey,
      stageCosts: stages
        .map((stage) => ({ stageId: stage.id!, rupees: parseRupees(costs[stage.id!] ?? "") ?? 0 }))
        .filter((cost) => cost.rupees > 0),
    };
    const saved = await action.run(() =>
      product ? api.admin.serviceProducts.update(product.id, form) : api.admin.serviceProducts.create(form),
    );
    if (saved) onSaved();
  }

  return (
    <Sheet open title={product ? "Edit item" : "New item"} onClose={onClose}>
      <div className="flex flex-col gap-4">
        <SelectField
          label="Garment"
          options={templates.map((t) => ({ value: String(t.id), label: t.name }))}
          value={String(templateId)}
          onChange={(value) => setTemplateId(Number(value))}
        />
        <TextField
          label="Name"
          maxLength={80}
          placeholder="3 dart blouse"
          value={name}
          onChange={(e) => setName(e.target.value)}
        />
        <div className="grid grid-cols-2 gap-3">
          <TextField label="Price (₹)" inputMode="decimal" value={price} onChange={(e) => setPrice(e.target.value)} />
          <TextField
            label="Code"
            maxLength={40}
            hint={product ? undefined : "Blank takes the next item code"}
            value={code}
            onChange={(e) => setCode(e.target.value)}
          />
        </div>
        <PhotoInput label="Add photo" purpose="PRODUCT" value={photoKey} onChange={setPhotoKey} />
        {stages.length > 0 && (
          <div>
            <p className="mb-2 text-sm font-medium text-ink-soft">Piece rate per stage (₹)</p>
            <div className="grid grid-cols-2 gap-3">
              {stages.map((stage) => (
                <TextField
                  key={stage.id}
                  label={stage.name}
                  inputMode="decimal"
                  value={costs[stage.id!] ?? ""}
                  onChange={(e) => setCosts({ ...costs, [stage.id!]: e.target.value })}
                />
              ))}
            </div>
            {priceRupees !== null && costRupees > 0 && (
              <p className="mt-2 text-sm">
                Profit per piece:{" "}
                <span className={priceRupees - costRupees < 0 ? "text-bad" : "text-ok"}>
                  {rupees(priceRupees - costRupees)}
                </span>
              </p>
            )}
          </div>
        )}
        <ErrorNote>{action.error}</ErrorNote>
        <div className="flex flex-wrap gap-2">
          <button type="button" className={buttonPrimary} disabled={action.busy} onClick={() => void save()}>
            {action.busy ? "Saving…" : "Save item"}
          </button>
          {product && (
            <button
              type="button"
              className={`${buttonSecondary} text-bad`}
              disabled={action.busy}
              onClick={async () => {
                const done = await action.run(() => api.admin.serviceProducts.remove(product.id).then(() => true));
                if (done) onSaved();
              }}
            >
              <TrashIcon size={18} /> Remove
            </button>
          )}
        </div>
      </div>
    </Sheet>
  );
}
