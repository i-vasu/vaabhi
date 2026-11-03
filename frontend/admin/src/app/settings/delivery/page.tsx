"use client";

import type { DeliveryPackage } from "@vaabhi/api-client";
import { PlusIcon, TrashIcon, TruckIcon } from "@/components/icons";
import { EmptyState, LoadState, PageHeader, SaveBar, TextField, buttonSecondary } from "@/components/ui";
import { api } from "@/lib/api";
import { parseRupees, useSaveable } from "@/lib/settings";

/** Delivery plans offered on a new order: name, charge and how many days the order takes. */
export default function DeliveryPackagesPage() {
  const list = useSaveable(api.admin.settings.deliveryPackages, api.admin.settings.saveDeliveryPackages);
  const packages = list.value;

  if (!packages) {
    return (
      <section className="mx-auto max-w-3xl">
        <PageHeader title="Delivery charges" backHref="/settings/orders/" />
        <LoadState loading={list.loading} error={list.loadError} />
      </section>
    );
  }

  const replace = (index: number, pkg: DeliveryPackage) =>
    list.setValue(packages.map((p, i) => (i === index ? pkg : p)));
  const add = () => list.setValue([...packages, { id: null, name: "", priceRupees: 0, days: 7 }]);

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader title="Delivery charges" backHref="/settings/orders/" />
      {packages.length === 0 ? (
        <EmptyState
          icon={TruckIcon}
          title="No packages yet"
          action={
            <button type="button" className={buttonSecondary} onClick={add}>
              <PlusIcon size={18} /> Add package
            </button>
          }
        >
          Add plans like Standard (7 days, free) or Express (3 days, ₹200) to pick one on a new order.
        </EmptyState>
      ) : (
        <>
          <ul className="flex flex-col gap-3">
            {packages.map((pkg, index) => (
              <li
                key={pkg.id ?? `new-${index}`}
                className="grid grid-cols-2 items-end gap-3 rounded-2xl border border-line bg-panel p-4 sm:grid-cols-[2fr_1fr_1fr_auto]"
              >
                <TextField
                  label="Package name"
                  className="col-span-2 sm:col-span-1"
                  value={pkg.name}
                  maxLength={60}
                  placeholder="Express"
                  onChange={(event) => replace(index, { ...pkg, name: event.target.value })}
                />
                <TextField
                  label="Charge ₹"
                  inputMode="decimal"
                  defaultValue={String(pkg.priceRupees)}
                  onChange={(event) => {
                    const price = parseRupees(event.target.value);
                    if (price !== null) replace(index, { ...pkg, priceRupees: price });
                  }}
                />
                <TextField
                  label="Days"
                  type="number"
                  min={0}
                  max={365}
                  value={pkg.days}
                  onChange={(event) => replace(index, { ...pkg, days: Number(event.target.value) })}
                />
                <button
                  type="button"
                  aria-label={`Remove ${pkg.name || "package"}`}
                  onClick={() => list.setValue(packages.filter((_, i) => i !== index))}
                  className="mb-1 justify-self-end rounded-lg p-2.5 text-bad hover:bg-bad-soft"
                >
                  <TrashIcon />
                </button>
              </li>
            ))}
          </ul>
          <button type="button" className={`${buttonSecondary} mt-4`} onClick={add}>
            <PlusIcon size={18} /> Add package
          </button>
        </>
      )}
      <SaveBar saving={list.saving} saved={list.saved} error={list.error} onSave={() => void list.save()} />
    </section>
  );
}
