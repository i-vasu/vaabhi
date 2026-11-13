"use client";

import Link from "next/link";
import { useState } from "react";
import type { AssetClassView, FixedAssetView, PaymentMode } from "@vaabhi/api-client";
import { BankIcon } from "@/components/icons";
import { ErrorNote, Pill } from "@/components/tailoring";
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
  Toggle,
  buttonPrimary,
  buttonSecondary,
} from "@/components/ui";
import { useIsOwner } from "@/lib/accounts";
import { api } from "@/lib/api";
import { parseRupees, rupees } from "@/lib/settings";
import { PAYMENT_MODES, formatDate, today, useAction, useLoad } from "@/lib/tailoring";

function classOptions(classes: AssetClassView[], activeOnly = true) {
  return [
    { value: "", label: "Choose a class" },
    ...classes
      .filter((klass) => !activeOnly || klass.active)
      .map((klass) => ({ value: String(klass.id), label: klass.name })),
  ];
}

/** An asset the shop owned before the books started: cost and depreciation so far, no bill. */
function OpeningAssetSheet({
  classes,
  onClose,
  onDone,
}: {
  classes: AssetClassView[];
  onClose: () => void;
  onDone: () => void;
}) {
  const options = classOptions(classes);
  const [name, setName] = useState("");
  const [classId, setClassId] = useState(options[1]?.value ?? "");
  const [cost, setCost] = useState("");
  const [putToUseOn, setPutToUseOn] = useState(today());
  const [openingAccdep, setOpeningAccdep] = useState("0");
  const action = useAction();

  const costValue = parseRupees(cost);
  const openingValue = parseRupees(openingAccdep);
  const valid =
    name.trim() !== "" &&
    classId !== "" &&
    costValue !== null &&
    costValue > 0 &&
    openingValue !== null &&
    putToUseOn !== "";

  async function save() {
    if (!valid || costValue === null || openingValue === null) return;
    const created = await action.run(() =>
      api.admin.accounts.assets.createOpening({
        name: name.trim(),
        classId: Number(classId),
        costRupees: costValue,
        putToUseOn,
        openingAccdepRupees: openingValue,
      }),
    );
    if (created) onDone();
  }

  return (
    <Sheet open title="Add opening asset" onClose={onClose}>
      <div className="space-y-3">
        <TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} />
        <SelectField label="Class" options={options} value={classId} onChange={setClassId} />
        <TextField
          label="Cost"
          inputMode="decimal"
          placeholder="0.00"
          value={cost}
          onChange={(e) => setCost(e.target.value)}
        />
        <TextField
          label="Put to use on"
          type="date"
          hint="Bought before the books started."
          value={putToUseOn}
          onChange={(e) => setPutToUseOn(e.target.value)}
        />
        <TextField
          label="Depreciation so far"
          inputMode="decimal"
          value={openingAccdep}
          onChange={(e) => setOpeningAccdep(e.target.value)}
        />
      </div>
      <ErrorNote>{action.error}</ErrorNote>
      <div className="mt-4 flex gap-3">
        <button type="button" className={buttonPrimary} disabled={action.busy || !valid} onClick={save}>
          {action.busy ? "Saving…" : "Add asset"}
        </button>
        <button type="button" className={buttonSecondary} onClick={onClose}>
          Cancel
        </button>
      </div>
    </Sheet>
  );
}

/** Sell, scrap or write off an asset; its book value leaves the books. */
function DisposeSheet({
  asset,
  onClose,
  onDone,
}: {
  asset: FixedAssetView;
  onClose: () => void;
  onDone: () => void;
}) {
  const [disposedOn, setDisposedOn] = useState(today());
  const [proceeds, setProceeds] = useState("0");
  const [mode, setMode] = useState<PaymentMode>("CASH");
  const action = useAction();

  const proceedsValue = parseRupees(proceeds);
  const valid = disposedOn !== "" && proceedsValue !== null && proceedsValue >= 0;
  const modes = PAYMENT_MODES.map((option) => ({ value: option.value, label: option.label }));

  async function save() {
    if (!valid || proceedsValue === null) return;
    const disposed = await action.run(() =>
      api.admin.accounts.assets.dispose(asset.id, {
        disposedOn,
        proceedsRupees: proceedsValue,
        mode: proceedsValue > 0 ? mode : null,
      }),
    );
    if (disposed) onDone();
  }

  return (
    <Sheet open title={`Dispose ${asset.name}`} onClose={onClose}>
      <p className="mb-4 text-sm text-ink-soft">
        Cost {rupees(asset.costRupees)} · Book value {rupees(asset.bookValueRupees)}
      </p>
      <div className="space-y-3">
        <TextField
          label="Disposed on"
          type="date"
          value={disposedOn}
          onChange={(e) => setDisposedOn(e.target.value)}
        />
        <TextField
          label="Proceeds"
          inputMode="decimal"
          value={proceeds}
          onChange={(e) => setProceeds(e.target.value)}
        />
        {proceedsValue !== null && proceedsValue > 0 && (
          <SelectField label="Received into" options={modes} value={mode} onChange={setMode} />
        )}
      </div>
      <ErrorNote>{action.error}</ErrorNote>
      <div className="mt-4 flex gap-3">
        <button type="button" className={buttonPrimary} disabled={action.busy || !valid} onClick={save}>
          {action.busy ? "Disposing…" : "Dispose asset"}
        </button>
        <button type="button" className={buttonSecondary} onClick={onClose}>
          Cancel
        </button>
      </div>
    </Sheet>
  );
}

function percent(text: string, max: number): number | null {
  const cleaned = text.trim();
  if (cleaned === "" || !/^\d+(\.\d{1,2})?$/.test(cleaned)) return null;
  const value = Number(cleaned);
  return value <= max ? value : null;
}

/** A class of asset: how long it lasts and its two accounts. */
function ClassSheet({
  klass,
  onClose,
  onDone,
}: {
  klass: AssetClassView;
  onClose: () => void;
  onDone: () => void;
}) {
  const [name, setName] = useState(klass.name);
  const [lifeYears, setLifeYears] = useState(String(klass.lifeYears));
  const [residual, setResidual] = useState(String(klass.residualPercent));
  const [active, setActive] = useState(klass.active);
  const action = useAction();

  const life = /^\d+$/.test(lifeYears.trim()) ? Number(lifeYears) : null;
  const residualValue = percent(residual, 50);
  const valid = name.trim() !== "" && life !== null && life >= 1 && life <= 60 && residualValue !== null;

  async function save() {
    if (!valid || life === null || residualValue === null) return;
    const updated = await action.run(() =>
      api.admin.accounts.assets.updateClass(klass.id, {
        name: name.trim(),
        lifeYears: life,
        residualPercent: residualValue,
        active,
      }),
    );
    if (updated) onDone();
  }

  return (
    <Sheet open title={`Edit ${klass.name}`} onClose={onClose}>
      <div className="space-y-3">
        <TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} />
        <TextField
          label="Life in years"
          inputMode="numeric"
          value={lifeYears}
          onChange={(e) => setLifeYears(e.target.value)}
        />
        <TextField
          label="Residual %"
          inputMode="decimal"
          value={residual}
          onChange={(e) => setResidual(e.target.value)}
        />
        <div className="flex items-center justify-between gap-3">
          <span className="text-sm font-medium text-ink-soft">Active</span>
          <Toggle checked={active} onChange={setActive} label="Active" />
        </div>
        <p className="text-sm text-ink-faint">
          Asset account {klass.assetAccountCode} · accumulated depreciation {klass.accdepAccountCode}
        </p>
      </div>
      <ErrorNote>{action.error}</ErrorNote>
      <div className="mt-4 flex gap-3">
        <button type="button" className={buttonPrimary} disabled={action.busy || !valid} onClick={save}>
          {action.busy ? "Saving…" : "Save class"}
        </button>
        <button type="button" className={buttonSecondary} onClick={onClose}>
          Cancel
        </button>
      </div>
    </Sheet>
  );
}

/** The fixed-asset register and its classes: what the shop owns and what it has depreciated. */
export default function AssetsPage() {
  const owner = useIsOwner();
  const assets = useLoad(() => api.admin.accounts.assets.list(), []);
  const classes = useLoad(() => api.admin.accounts.assets.classes(), []);

  const [adding, setAdding] = useState(false);
  const [disposing, setDisposing] = useState<FixedAssetView | null>(null);
  const [editingClass, setEditingClass] = useState<AssetClassView | null>(null);

  const rows = assets.value;
  const classRows = classes.value;
  const totalCost = (rows ?? []).reduce((sum, asset) => sum + asset.costRupees, 0);
  const totalAccumulated = (rows ?? []).reduce((sum, asset) => sum + asset.accumulatedRupees, 0);
  const totalBookValue = (rows ?? []).reduce((sum, asset) => sum + asset.bookValueRupees, 0);

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader
        title="Fixed assets"
        backHref="/money/accounts/"
        actions={
          owner && (
            <button
              type="button"
              className={buttonPrimary}
              disabled={!classRows || classRows.length === 0}
              onClick={() => setAdding(true)}
            >
              Add opening asset
            </button>
          )
        }
      />
      <ErrorNote>{assets.error ?? classes.error}</ErrorNote>

      {!rows ? (
        <LoadState loading={assets.loading} error={null} />
      ) : rows.length === 0 ? (
        <EmptyState icon={BankIcon} title="No assets yet">
          Add what the shop already owned when the books started, or buy something on a supplier bill.
        </EmptyState>
      ) : (
        <div className="overflow-x-auto rounded-2xl border border-line bg-panel">
          <table className="w-full min-w-[46rem] text-left text-sm">
            <thead className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
              <tr>
                <th className="px-4 py-2 font-medium">Asset</th>
                <th className="px-4 py-2 font-medium">Class</th>
                <th className="px-4 py-2 text-right font-medium">Cost</th>
                <th className="px-4 py-2 text-right font-medium">Depreciated</th>
                <th className="px-4 py-2 text-right font-medium">Book value</th>
                <th className="px-4 py-2 font-medium">Bill</th>
                <th className="px-4 py-2 font-medium">Status</th>
                <th className="px-4 py-2" />
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {rows.map((asset) => (
                <tr key={asset.id}>
                  <td className="px-4 py-3 font-medium">{asset.name}</td>
                  <td className="px-4 py-3 text-ink-soft">{asset.className}</td>
                  <td className="whitespace-nowrap px-4 py-3 text-right tabular-nums">{rupees(asset.costRupees)}</td>
                  <td className="whitespace-nowrap px-4 py-3 text-right tabular-nums">
                    {rupees(asset.accumulatedRupees)}
                  </td>
                  <td className="whitespace-nowrap px-4 py-3 text-right tabular-nums">
                    {rupees(asset.bookValueRupees)}
                  </td>
                  <td className="whitespace-nowrap px-4 py-3">
                    {asset.billId && asset.billNo ? (
                      <Link href={`/money/purchases/bill/?id=${asset.billId}`} className="text-accent-ink underline">
                        {asset.billNo}
                      </Link>
                    ) : (
                      <span className="text-ink-faint">—</span>
                    )}
                  </td>
                  <td className="whitespace-nowrap px-4 py-3">
                    {asset.disposedOn ? (
                      <Pill tone="default">Disposed {formatDate(asset.disposedOn)}</Pill>
                    ) : (
                      <Pill tone="ok">In use</Pill>
                    )}
                  </td>
                  <td className="whitespace-nowrap px-4 py-3 text-right">
                    {owner && !asset.disposedOn && (
                      <button type="button" className={buttonSecondary} onClick={() => setDisposing(asset)}>
                        Dispose
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
            <tfoot className="border-t border-line font-medium">
              <tr>
                <td className="px-4 py-3" colSpan={2}>
                  Total
                </td>
                <td className="px-4 py-3 text-right tabular-nums">{rupees(totalCost)}</td>
                <td className="px-4 py-3 text-right tabular-nums">{rupees(totalAccumulated)}</td>
                <td className="px-4 py-3 text-right tabular-nums">{rupees(totalBookValue)}</td>
                <td colSpan={3} />
              </tr>
            </tfoot>
          </table>
        </div>
      )}

      <SectionLabel>Asset classes</SectionLabel>
      {!classRows ? (
        <LoadState loading={classes.loading} error={null} />
      ) : classRows.length === 0 ? (
        <p className="text-sm text-ink-soft">No asset classes yet.</p>
      ) : (
        <ListCard>
          {classRows.map((klass) => (
            <ListRow
              key={klass.id}
              title={klass.name}
              subtitle={`${klass.lifeYears} years · ${klass.residualPercent}% residual · ${klass.assetAccountCode} / ${
                klass.accdepAccountCode
              }`}
              trailing={
                owner ? (
                  <button type="button" className={buttonSecondary} onClick={() => setEditingClass(klass)}>
                    Edit
                  </button>
                ) : undefined
              }
            />
          ))}
        </ListCard>
      )}

      {adding && classRows && (
        <OpeningAssetSheet
          classes={classRows}
          onClose={() => setAdding(false)}
          onDone={() => {
            setAdding(false);
            assets.reload();
          }}
        />
      )}
      {disposing && (
        <DisposeSheet
          asset={disposing}
          onClose={() => setDisposing(null)}
          onDone={() => {
            setDisposing(null);
            assets.reload();
          }}
        />
      )}
      {editingClass && (
        <ClassSheet
          klass={editingClass}
          onClose={() => setEditingClass(null)}
          onDone={() => {
            setEditingClass(null);
            classes.reload();
            assets.reload();
          }}
        />
      )}
    </section>
  );
}
