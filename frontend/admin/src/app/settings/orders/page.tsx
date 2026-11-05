"use client";

import { useEffect, useState } from "react";
import type { OrderSettings } from "@vaabhi/api-client";
import { FlowIcon, RulerIcon, TruckIcon } from "@/components/icons";
import { ListCard, ListRow, LoadState, PageHeader, SectionLabel, ToggleRow } from "@/components/ui";
import { api } from "@/lib/api";
import { useSection } from "@/lib/settings";

const plural = (n: number, word: string) => `${n} ${word}${n === 1 ? "" : "s"}`;

/** Order and product-page switches save as soon as they are flipped. */
export default function OrderSettingsPage() {
  const section = useSection("orders");
  const settings = section.value;
  const [counts, setCounts] = useState<{ stages?: number; packages?: number; garments?: number }>({});

  useEffect(() => {
    Promise.all([
      api.admin.settings.stages(),
      api.admin.settings.deliveryPackages(),
      api.admin.settings.garmentTemplates(),
    ])
      .then(([stages, packages, garments]) =>
        setCounts({ stages: stages.length, packages: packages.length, garments: garments.length }),
      )
      .catch(() => setCounts({}));
  }, []);

  const flip = (key: keyof OrderSettings) => (checked: boolean) =>
    settings && void section.save({ ...settings, [key]: checked });

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader title="Orders & production" backHref="/settings/" />

      <SectionLabel>Orders setup</SectionLabel>
      <ListCard>
        <ListRow
          icon={TruckIcon}
          title="Delivery charges"
          trailing={
            counts.packages === undefined
              ? ""
              : counts.packages === 0
                ? "No packages yet"
                : plural(counts.packages, "package")
          }
          href="/settings/delivery/"
        />
        <ListRow
          icon={FlowIcon}
          title="Production stages"
          trailing={counts.stages === undefined ? "" : plural(counts.stages, "stage")}
          href="/settings/stages/"
        />
      </ListCard>

      {!settings ? (
        <div className="mt-6">
          <LoadState loading={section.loading} error={section.loadError} />
        </div>
      ) : (
        <>
          <div className="mt-4">
            <ListCard>
              <ToggleRow
                title="Trial / fitting date"
                subtitle="Adds a trial date to new orders, and shows it on the order and in the workshop"
                checked={settings.trialDate}
                onChange={flip("trialDate")}
                disabled={section.saving}
              />
            </ListCard>
          </div>

          <SectionLabel>Garments & measurements</SectionLabel>
          <ListCard>
            <ListRow
              icon={RulerIcon}
              title="Garment templates"
              subtitle="Fields here appear in New order and Add measurement"
              trailing={counts.garments === undefined ? "" : plural(counts.garments, "garment")}
              href="/settings/garments/"
            />
          </ListCard>

          <SectionLabel>Product page</SectionLabel>
          <ListCard>
            <ToggleRow
              title="Sub-categories"
              subtitle="Adds a sub-category field to products"
              checked={settings.subCategories}
              onChange={flip("subCategories")}
              disabled={section.saving}
            />
            <ToggleRow
              title="Barcodes"
              subtitle="Show the barcode field on a product"
              checked={settings.barcodes}
              onChange={flip("barcodes")}
              disabled={section.saving}
            />
            <ToggleRow
              title="Do not preload products"
              subtitle="For shops with very large catalogues"
              checked={settings.lazyProductList}
              onChange={flip("lazyProductList")}
              disabled={section.saving}
            />
            <ToggleRow
              title="Tailor charge on add-ons"
              subtitle="What the shop pays the worker for each add-on. Not printed on any bill."
              checked={settings.tailorChargeOnAddons}
              onChange={flip("tailorChargeOnAddons")}
              disabled={section.saving}
            />
          </ListCard>
          {section.error && (
            <p role="alert" className="mt-4 text-bad">
              {section.error}
            </p>
          )}
        </>
      )}
    </section>
  );
}
