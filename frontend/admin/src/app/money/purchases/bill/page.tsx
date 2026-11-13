"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import {
  ApiError,
  type Account,
  type AssetClassView,
  type StockItem,
  type SupplierBalance,
  type SupplierBillForm,
  type SupplierBillLineForm,
  type SupplierBillLineView,
  type SupplierBillView,
  type TdsSectionView,
  type TdsSuggestion,
} from "@vaabhi/api-client";
import { AmountLine } from "@/components/money";
import { ErrorNote, Pill } from "@/components/tailoring";
import {
  LoadState,
  PageHeader,
  SectionLabel,
  SelectField,
  Sheet,
  StatTile,
  TextAreaField,
  TextField,
  ToggleRow,
  buttonPrimary,
  buttonSecondary,
} from "@/components/ui";
import { api } from "@/lib/api";
import { unitLabel } from "@/lib/extras";
import { LINE_KINDS, PAYMENT_MODE_OPTIONS, billTotals, lineTax } from "@/lib/purchases";
import { describeError, parseRupees, rupees } from "@/lib/settings";
import { formatDate, idParam, today, useAction, useLoad } from "@/lib/tailoring";

// --- line state -------------------------------------------------------------------------------------------------

type LineState = {
  key: number;
  kind: "EXPENSE" | "STOCK" | "ASSET";
  description: string;
  accountId: string;
  stockItemId: string;
  quantity: string;
  assetClassId: string;
  hsn: string;
  taxable: string;
  rate: string;
};

let nextLineKey = 0;

function blankLine(): LineState {
  return {
    key: ++nextLineKey,
    kind: "EXPENSE",
    description: "",
    accountId: "",
    stockItemId: "",
    quantity: "",
    assetClassId: "",
    hsn: "",
    taxable: "",
    rate: "5",
  };
}

function lineFromView(line: SupplierBillLineView): LineState {
  const kind: LineState["kind"] = line.kind === "STOCK" ? "STOCK" : line.kind === "ASSET" ? "ASSET" : "EXPENSE";
  return {
    key: ++nextLineKey,
    kind,
    description: line.description,
    accountId: String(line.accountId),
    stockItemId: line.stockItemId === null ? "" : String(line.stockItemId),
    quantity: line.quantity === null ? "" : String(line.quantity),
    assetClassId: line.assetClassId === null ? "" : String(line.assetClassId),
    hsn: line.hsn ?? "",
    taxable: String(line.taxableRupees),
    rate: String(line.gstRatePercent),
  };
}

// --- page -------------------------------------------------------------------------------------------------------

function BillView({ id, supplier }: { id: number | null; supplier: string }) {
  const bill = useLoad(() => (id ? api.admin.purchases.bill(id) : Promise.resolve(null)), [id]);
  const suppliers = useLoad(() => api.admin.purchases.suppliers(), []);
  const chart = useLoad(() => api.admin.accounts.chart(), []);
  const stock = useLoad(() => api.admin.stock.list(), []);
  const classes = useLoad(() => api.admin.accounts.assets.classes(), []);
  const sections = useLoad(() => api.admin.accounts.tds.sections(), []);

  if (id && !bill.value) {
    return (
      <section className="mx-auto max-w-3xl">
        <PageHeader title="Bill" backHref="/money/purchases/bills/" />
        <LoadState loading={bill.loading} error={bill.error} />
      </section>
    );
  }
  // A bill the books hold (or one already cancelled) needs no option lists: it only reads.
  if (bill.value && (bill.value.posted || bill.value.status === "CANCELLED")) {
    return <BillReadOnly view={bill.value} onChanged={bill.reload} />;
  }
  const supplierList = suppliers.value;
  const chartAccounts = chart.value;
  const classList = classes.value;
  if (!supplierList || !chartAccounts || !classList) {
    return (
      <section className="mx-auto max-w-3xl">
        <PageHeader title={id ? "Bill" : "New bill"} backHref="/money/purchases/bills/" />
        <LoadState
          loading={suppliers.loading || chart.loading || classes.loading}
          error={suppliers.error ?? chart.error ?? classes.error}
        />
      </section>
    );
  }

  return (
    <BillEditor
      key={id ?? "new"}
      view={bill.value ?? null}
      initialSupplierId={supplier}
      suppliers={supplierList}
      accounts={chartAccounts}
      stockItems={stock.value ?? []}
      assetClasses={classList}
      sections={sections.value ?? []}
      onChanged={bill.reload}
    />
  );
}

function BillEditor({
  view,
  initialSupplierId,
  suppliers,
  accounts,
  stockItems,
  assetClasses,
  sections,
  onChanged,
}: {
  view: SupplierBillView | null;
  initialSupplierId: string;
  suppliers: SupplierBalance[];
  accounts: Account[];
  stockItems: StockItem[];
  assetClasses: AssetClassView[];
  sections: TdsSectionView[];
  onChanged: () => void;
}) {
  if (view && (view.posted || view.status === "CANCELLED")) {
    return <BillReadOnly view={view} onChanged={onChanged} />;
  }
  return (
    <BillForm
      view={view}
      initialSupplierId={initialSupplierId}
      suppliers={suppliers}
      accounts={accounts}
      stockItems={stockItems}
      assetClasses={assetClasses}
      sections={sections}
      onChanged={onChanged}
    />
  );
}

// --- editable form ----------------------------------------------------------------------------------------------

function BillForm({
  view,
  initialSupplierId,
  suppliers,
  accounts,
  stockItems,
  assetClasses,
  sections,
  onChanged,
}: {
  view: SupplierBillView | null;
  initialSupplierId: string;
  suppliers: SupplierBalance[];
  accounts: Account[];
  stockItems: StockItem[];
  assetClasses: AssetClassView[];
  sections: TdsSectionView[];
  onChanged: () => void;
}) {
  const [liveId, setLiveId] = useState<number | null>(view?.id ?? null);
  const [supplierId, setSupplierId] = useState(view ? String(view.partyId) : initialSupplierId);
  const [billNo, setBillNo] = useState(view?.billNo ?? "");
  const [billDate, setBillDate] = useState(view?.billDate ?? today());
  const [dueDate, setDueDate] = useState(view?.dueDate ?? "");
  const [interState, setInterState] = useState(view ? view.igstRupees > 0 : false);
  const [tdsSectionId, setTdsSectionId] = useState(view ? String(view.tdsSectionId ?? "") : "");
  const [tds, setTds] = useState(view && view.tdsRupees > 0 ? String(view.tdsRupees) : "");
  const [tdsEdited, setTdsEdited] = useState(view !== null && view.tdsRupees > 0);
  const [sectionEdited, setSectionEdited] = useState(view !== null && view.tdsSectionId !== null);
  const [notes, setNotes] = useState(view?.notes ?? "");
  const [lines, setLines] = useState<LineState[]>(view ? view.lines.map(lineFromView) : [blankLine()]);
  const [paidNow, setPaidNow] = useState(false);
  const [paidMode, setPaidMode] = useState("UPI");
  const [paidReference, setPaidReference] = useState("");
  const [suggestion, setSuggestion] = useState<TdsSuggestion | null>(null);
  const [saved, setSaved] = useState<SupplierBillView | null>(null);
  const action = useAction();

  const totals = billTotals(lines, interState);
  const taxable = Number(totals.taxable);
  const tdsRupees = tds.trim() === "" ? 0 : (parseRupees(tds) ?? 0);
  const payable = Math.max(0, Number(totals.total) - tdsRupees);
  const supplier = suppliers.find((row) => String(row.partyId) === supplierId);

  useEffect(() => {
    let cancelled = false;
    const timer = window.setTimeout(() => {
      if (!supplierId || !billDate || !(taxable > 0)) {
        setSuggestion(null);
        return;
      }
      api.admin.purchases
        .tdsCheck({ partyId: Number(supplierId), billDate, taxableRupees: taxable, billId: liveId })
        .then((next) => {
          if (cancelled) return;
          setSuggestion(next);
          if (!tdsEdited) setTds(next.suggestedRupees > 0 ? String(next.suggestedRupees) : "");
          if (!sectionEdited && next.sectionId !== null) setTdsSectionId(String(next.sectionId));
        })
        .catch(() => {
          if (!cancelled) setSuggestion(null);
        });
    }, 400);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [supplierId, billDate, taxable, liveId, tdsEdited, sectionEdited]);

  const supplierOptions = [
    { value: "", label: "Choose a supplier" },
    ...suppliers.map((row) => ({ value: String(row.partyId), label: row.name })),
  ];
  if (supplierId && !supplierOptions.some((option) => option.value === supplierId)) {
    supplierOptions.push({ value: supplierId, label: view?.supplierName ?? "Saved supplier" });
  }

  const accountOptions = [
    { value: "", label: "Choose an account" },
    ...accounts
      .filter((account) => account.type === "EXPENSE" && account.active)
      .map((account) => ({ value: String(account.id), label: `${account.code} · ${account.name}` })),
  ];
  const stockOptions = [
    { value: "", label: "Choose an item" },
    ...stockItems.map((item) => ({
      value: String(item.id),
      label: item.sku ? `${item.name} · ${item.sku}` : item.name,
    })),
  ];
  const classOptions = [
    { value: "", label: "Choose a class" },
    ...assetClasses.filter((row) => row.active).map((row) => ({ value: String(row.id), label: row.name })),
  ];
  const sectionOptions = [
    { value: "", label: "No TDS" },
    ...sections
      .filter((row) => row.active)
      .map((row) => ({ value: String(row.id), label: `${row.code} · ${row.name}` })),
  ];
  if (tdsSectionId && !sectionOptions.some((option) => option.value === tdsSectionId)) {
    sectionOptions.push({ value: tdsSectionId, label: "Saved section" });
  }

  function changeLine(index: number, patch: Partial<LineState>) {
    setLines((current) => current.map((line, i) => (i === index ? { ...line, ...patch } : line)));
  }

  async function save() {
    if (!supplierId) return action.setError("Pick a supplier.");
    if (!billNo.trim()) return action.setError("Enter the supplier's bill number.");
    if (!billDate) return action.setError("Pick the bill date.");
    if (dueDate && dueDate < billDate) return action.setError("The due date is before the bill date.");
    const tdsAmount = tds.trim() === "" ? 0 : parseRupees(tds);
    if (tdsAmount === null) return action.setError("Enter a rupee amount for TDS.");

    const builtLines: SupplierBillLineForm[] = [];
    for (const line of lines) {
      if (line.description.trim() === "" && line.taxable.trim() === "") continue;
      if (!line.description.trim()) return action.setError("Say what each line is for.");
      const taxableRupees = parseRupees(line.taxable);
      if (taxableRupees === null || taxableRupees <= 0) {
        return action.setError("Enter the taxable amount for each line.");
      }
      const rate = Number(line.rate);
      if (line.rate.trim() === "" || !Number.isFinite(rate) || rate < 0 || rate > 28) {
        return action.setError("The GST rate must be between 0 and 28.");
      }
      const form: SupplierBillLineForm = {
        kind: line.kind,
        description: line.description.trim(),
        accountId: null,
        stockItemId: null,
        quantity: null,
        assetClassId: null,
        hsn: line.hsn.trim() || null,
        taxableRupees,
        gstRatePercent: rate,
      };
      if (line.kind === "EXPENSE") {
        if (!line.accountId) return action.setError("Pick the expense account for each expense line.");
        form.accountId = Number(line.accountId);
      } else if (line.kind === "STOCK") {
        const quantity = Number(line.quantity);
        if (!line.stockItemId) return action.setError("Pick the stock item for each stock line.");
        if (!line.quantity.trim() || !Number.isFinite(quantity) || quantity <= 0) {
          return action.setError("Enter the quantity for each stock line.");
        }
        form.stockItemId = Number(line.stockItemId);
        form.quantity = quantity;
      } else {
        if (!line.assetClassId) return action.setError("Pick the asset class for each asset line.");
        form.assetClassId = Number(line.assetClassId);
      }
      builtLines.push(form);
    }
    if (builtLines.length === 0) return action.setError("Add at least one line.");

    const form: SupplierBillForm = {
      partyId: Number(supplierId),
      billNo: billNo.trim(),
      billDate,
      dueDate: dueDate || null,
      // The server decides the split from the supplier's state; the toggle above is only a preview.
      supplyStateCode: null,
      tdsSectionId: tdsSectionId ? Number(tdsSectionId) : null,
      tdsRupees: tdsAmount,
      notes: notes.trim() || null,
      lines: builtLines,
      paidNow: !liveId && paidNow ? { mode: paidMode, reference: paidReference.trim() || null } : null,
    };
    const savedView = liveId
      ? await action.run(() => api.admin.purchases.updateBill(liveId, form))
      : await action.run(() => api.admin.purchases.createBill(form));
    if (savedView) {
      setSaved(savedView);
      if (!liveId) setLiveId(savedView.id);
      onChanged();
    }
  }

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader
        title={view ? `Bill ${view.billNo}` : saved ? `Bill ${saved.billNo}` : "New bill"}
        backHref="/money/purchases/bills/"
      />
      <ErrorNote>{action.error}</ErrorNote>

      <div className="grid gap-3 sm:grid-cols-2">
        <SelectField
          label="Supplier"
          options={supplierOptions}
          value={supplierId}
          onChange={(value) => {
            setSupplierId(value);
            setSuggestion(null);
          }}
        />
        <TextField
          label="Their bill number"
          maxLength={40}
          value={billNo}
          onChange={(e) => setBillNo(e.target.value)}
        />
        <TextField label="Bill date" type="date" value={billDate} onChange={(e) => setBillDate(e.target.value)} />
        <TextField label="Due date" type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} />
      </div>

      <div className="mt-3 rounded-2xl border border-line bg-panel">
        <ToggleRow
          title="Inter-state (IGST)"
          subtitle={`Preview only: the server uses the supplier's state${
            supplier?.state ? ` (${supplier.state})` : ""
          } and the shop's own state to decide the split.`}
          checked={interState}
          onChange={setInterState}
        />
      </div>

      <SectionLabel
        action={
          <button
            type="button"
            className={buttonSecondary}
            onClick={() => setLines((current) => [...current, blankLine()])}
          >
            Add line
          </button>
        }
      >
        Lines
      </SectionLabel>
      <div className="space-y-3">
        {lines.map((line, index) => {
          const split = lineTax(line.taxable, line.rate, interState);
          const stockItem = stockItems.find((item) => String(item.id) === line.stockItemId);
          return (
            <div key={line.key} className="rounded-2xl border border-line bg-panel p-4">
              <div className="grid gap-3 sm:grid-cols-2">
                <SelectField
                  label="Line kind"
                  options={LINE_KINDS}
                  value={line.kind}
                  onChange={(kind) => changeLine(index, { kind })}
                />
                <TextField
                  label="Description"
                  maxLength={200}
                  value={line.description}
                  onChange={(e) => changeLine(index, { description: e.target.value })}
                />
                {line.kind === "EXPENSE" && (
                  <SelectField
                    className="sm:col-span-2"
                    label="Expense account"
                    options={accountOptions}
                    value={line.accountId}
                    onChange={(accountId) => changeLine(index, { accountId })}
                  />
                )}
                {line.kind === "STOCK" && (
                  <>
                    <SelectField
                      label="Stock item"
                      options={stockOptions}
                      value={line.stockItemId}
                      onChange={(stockItemId) => changeLine(index, { stockItemId })}
                    />
                    <TextField
                      label={`Quantity${stockItem ? ` (${unitLabel(stockItem.unit)})` : ""}`}
                      inputMode="decimal"
                      value={line.quantity}
                      onChange={(e) => changeLine(index, { quantity: e.target.value })}
                    />
                  </>
                )}
                {line.kind === "ASSET" && (
                  <SelectField
                    className="sm:col-span-2"
                    label="Asset class"
                    options={classOptions}
                    value={line.assetClassId}
                    onChange={(assetClassId) => changeLine(index, { assetClassId })}
                  />
                )}
                <TextField
                  label="HSN / SAC"
                  maxLength={8}
                  value={line.hsn}
                  onChange={(e) => changeLine(index, { hsn: e.target.value })}
                />
                <TextField
                  label="Taxable (₹)"
                  inputMode="decimal"
                  value={line.taxable}
                  onChange={(e) => changeLine(index, { taxable: e.target.value })}
                />
                <TextField
                  label="GST %"
                  inputMode="decimal"
                  value={line.rate}
                  onChange={(e) => changeLine(index, { rate: e.target.value })}
                />
              </div>
              <p className="mt-2 text-sm text-ink-soft">
                {interState
                  ? `IGST ${rupees(Number(split.igst))}`
                  : `CGST ${rupees(Number(split.cgst))} · SGST ${rupees(Number(split.sgst))}`}{" "}
                · total {rupees(Number(split.total))}
              </p>
              {lines.length > 1 && (
                <button
                  type="button"
                  className="mt-2 text-sm text-bad underline"
                  onClick={() => setLines((current) => current.filter((_, i) => i !== index))}
                >
                  Remove line
                </button>
              )}
            </div>
          );
        })}
      </div>

      <dl aria-label="Bill totals" className="mt-4 rounded-2xl border border-line bg-panel p-4 text-sm">
        <AmountLine label="Taxable value" value={rupees(taxable)} />
        {interState ? (
          <AmountLine label="IGST" value={rupees(Number(totals.igst))} />
        ) : (
          <>
            <AmountLine label="CGST" value={rupees(Number(totals.cgst))} />
            <AmountLine label="SGST" value={rupees(Number(totals.sgst))} />
          </>
        )}
        <AmountLine label="Total" value={rupees(Number(totals.total))} />
        {tdsRupees > 0 && <AmountLine label="TDS" value={`−${rupees(tdsRupees)}`} />}
        {tdsRupees > 0 && <AmountLine label="Payable" value={rupees(payable)} strong />}
      </dl>

      <div className="mt-4 grid gap-3 sm:grid-cols-2">
        <SelectField
          label="TDS section"
          options={sectionOptions}
          value={tdsSectionId}
          onChange={(value) => {
            setSectionEdited(true);
            setTdsSectionId(value);
          }}
        />
        <div>
          <TextField
            label="TDS (₹)"
            inputMode="decimal"
            value={tds}
            onChange={(e) => {
              setTdsEdited(true);
              setTds(e.target.value);
            }}
          />
          {suggestion?.warning && <p className="mt-1.5 text-sm text-warn">{suggestion.warning}</p>}
          {!suggestion?.warning && suggestion?.sectionCode && !tdsEdited && (
            <p className="mt-1.5 text-sm text-ink-faint">Suggested under {suggestion.sectionCode}</p>
          )}
        </div>
      </div>

      <TextAreaField
        className="mt-3"
        label="Notes"
        maxLength={300}
        value={notes}
        onChange={(e) => setNotes(e.target.value)}
      />

      {!liveId && (
        <div className="mt-3 rounded-2xl border border-line bg-panel">
          <ToggleRow
            title="Paid now"
            subtitle="Record the payment with the bill, in full."
            checked={paidNow}
            onChange={setPaidNow}
          />
          {paidNow && (
            <div className="grid gap-3 px-4 pb-4 sm:grid-cols-2">
              <SelectField label="Paid by" options={PAYMENT_MODE_OPTIONS} value={paidMode} onChange={setPaidMode} />
              <TextField
                label="Reference"
                maxLength={100}
                value={paidReference}
                onChange={(e) => setPaidReference(e.target.value)}
              />
            </div>
          )}
        </div>
      )}

      <div className="mt-5 flex flex-wrap items-center gap-3">
        <button type="button" className={buttonPrimary} disabled={action.busy} onClick={() => void save()}>
          {action.busy ? "Saving…" : "Save bill"}
        </button>
        {liveId && (
          <Link href={`/money/purchases/supplier/?id=${supplierId}`} className={buttonSecondary}>
            Supplier statement
          </Link>
        )}
      </div>

      {saved && (
        <div className="mt-4 rounded-2xl border border-line bg-panel p-4 text-sm">
          <p className="font-medium">Saved {saved.billNo}</p>
          <p className="mt-1 text-ink-soft">
            CGST {rupees(saved.cgstRupees)} · SGST {rupees(saved.sgstRupees)} · IGST {rupees(saved.igstRupees)} ·
            payable {rupees(saved.payableRupees)}
            {saved.paidRupees > 0 ? ` · paid ${rupees(saved.paidRupees)}` : ""}
          </p>
          {saved.tdsWarning && <p className="mt-1 text-warn">{saved.tdsWarning}</p>}
          <Link href={`/money/purchases/bill/?id=${saved.id}`} className={`${buttonSecondary} mt-3`}>
            Open the bill
          </Link>
        </div>
      )}

      {liveId && <Attachment billId={liveId} hasAttachment={Boolean(saved?.attachmentKey ?? view?.attachmentKey)} />}
    </section>
  );
}

// --- read-only bill ----------------------------------------------------------------------------------------------

function BillReadOnly({ view, onChanged }: { view: SupplierBillView; onChanged: () => void }) {
  const [cancelling, setCancelling] = useState(false);

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader
        title={`Bill ${view.billNo}`}
        backHref={`/money/purchases/supplier/?id=${view.partyId}`}
        badge={view.status === "CANCELLED" ? "Cancelled" : view.posted ? "Posted" : "Not posted"}
        actions={
          view.status === "OPEN" ? (
            <button type="button" className={`${buttonSecondary} text-bad`} onClick={() => setCancelling(true)}>
              Cancel bill
            </button>
          ) : undefined
        }
      />
      <div className="mb-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
        <StatTile label="Total" value={rupees(view.totalRupees)} />
        <StatTile label="TDS" value={rupees(view.tdsRupees)} />
        <StatTile
          label="Payable"
          value={rupees(view.payableRupees)}
          tone={view.payableRupees > view.paidRupees ? "warn" : "default"}
        />
        <StatTile label="Paid" value={rupees(view.paidRupees)} />
      </div>
      <dl className="mb-4 grid grid-cols-2 gap-4 rounded-2xl border border-line bg-panel p-4 text-sm sm:grid-cols-3">
        <div>
          <dt className="text-xs font-semibold uppercase tracking-[0.12em] text-ink-faint">Supplier</dt>
          <dd className="mt-1">
            <Link
              href={`/money/purchases/supplier/?id=${view.partyId}`}
              className="font-medium text-accent-ink underline"
            >
              {view.supplierName}
            </Link>
          </dd>
        </div>
        <div>
          <dt className="text-xs font-semibold uppercase tracking-[0.12em] text-ink-faint">Bill date</dt>
          <dd className="mt-1">{formatDate(view.billDate)}</dd>
        </div>
        <div>
          <dt className="text-xs font-semibold uppercase tracking-[0.12em] text-ink-faint">Due</dt>
          <dd className="mt-1">{view.dueDate ? formatDate(view.dueDate) : "—"}</dd>
        </div>
        <div>
          <dt className="text-xs font-semibold uppercase tracking-[0.12em] text-ink-faint">Source</dt>
          <dd className="mt-1">{view.source === "SHIPROCKET" ? "Shiprocket import" : "Typed in"}</dd>
        </div>
        <div>
          <dt className="text-xs font-semibold uppercase tracking-[0.12em] text-ink-faint">State code</dt>
          <dd className="mt-1">{view.supplyStateCode ?? "—"}</dd>
        </div>
      </dl>

      <div className="overflow-x-auto rounded-2xl border border-line bg-panel">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
            <tr>
              <th className="px-4 py-2 font-medium">Line</th>
              <th className="px-4 py-2 text-right font-medium">Taxable</th>
              <th className="px-4 py-2 text-right font-medium">GST %</th>
              <th className="px-4 py-2 text-right font-medium">CGST</th>
              <th className="px-4 py-2 text-right font-medium">SGST</th>
              <th className="px-4 py-2 text-right font-medium">IGST</th>
              <th className="px-4 py-2 text-right font-medium">Total</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-line">
            {view.lines.map((line) => (
              <tr key={line.id}>
                <td className="px-4 py-3">
                  <span className="block font-medium">{line.description}</span>
                  <span className="block text-xs text-ink-faint">
                    {line.kind}
                    {line.stockItemName ? ` · ${line.stockItemName}` : ""}
                    {line.assetClassName ? ` · ${line.assetClassName}` : ""}
                    {line.quantity !== null ? ` · ${line.quantity}` : ""}
                  </span>
                </td>
                <td className="px-4 py-3 text-right tabular-nums">{rupees(line.taxableRupees)}</td>
                <td className="px-4 py-3 text-right tabular-nums">{line.gstRatePercent}%</td>
                <td className="px-4 py-3 text-right tabular-nums">{rupees(line.cgstRupees)}</td>
                <td className="px-4 py-3 text-right tabular-nums">{rupees(line.sgstRupees)}</td>
                <td className="px-4 py-3 text-right tabular-nums">{rupees(line.igstRupees)}</td>
                <td className="px-4 py-3 text-right tabular-nums">{rupees(line.totalRupees)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <dl aria-label="Bill totals" className="mt-4 rounded-2xl border border-line bg-panel p-4 text-sm">
        <AmountLine label="Taxable value" value={rupees(view.taxableRupees)} />
        {view.igstRupees > 0 ? (
          <AmountLine label="IGST" value={rupees(view.igstRupees)} />
        ) : (
          <>
            <AmountLine label="CGST" value={rupees(view.cgstRupees)} />
            <AmountLine label="SGST" value={rupees(view.sgstRupees)} />
          </>
        )}
        <AmountLine label="Total" value={rupees(view.totalRupees)} />
        {view.tdsRupees > 0 && <AmountLine label="TDS" value={`−${rupees(view.tdsRupees)}`} />}
        {view.tdsRupees > 0 && <AmountLine label="Payable" value={rupees(view.payableRupees)} strong />}
      </dl>
      {view.notes && <p className="mt-3 text-sm text-ink-soft">{view.notes}</p>}
      {view.tdsWarning && <p className="mt-3 text-sm text-warn">{view.tdsWarning}</p>}
      {view.status === "OPEN" && <Attachment billId={view.id} hasAttachment={view.attachmentKey !== null} />}

      {cancelling && (
        <CancelSheet
          bill={view}
          onClose={() => setCancelling(false)}
          onCancelled={() => {
            setCancelling(false);
            onChanged();
          }}
        />
      )}
    </section>
  );
}

function CancelSheet({
  bill,
  onClose,
  onCancelled,
}: {
  bill: SupplierBillView;
  onClose: () => void;
  onCancelled: () => void;
}) {
  const [on, setOn] = useState(today());
  const [reason, setReason] = useState("");
  const action = useAction();

  async function cancel() {
    if (!reason.trim()) return action.setError("Say why the bill is cancelled.");
    const cancelled = await action.run(() => api.admin.purchases.cancelBill(bill.id, { on, reason: reason.trim() }));
    if (cancelled) onCancelled();
  }

  return (
    <Sheet open title={`Cancel bill ${bill.billNo}`} onClose={onClose}>
      <div className="flex flex-col gap-4">
        <p className="text-sm text-ink-soft">
          The stock it brought in goes back and any asset bought on it leaves the register. Payments on it must be
          removed first.
        </p>
        <TextField label="Date" type="date" value={on} onChange={(e) => setOn(e.target.value)} />
        <TextField label="Reason" maxLength={300} value={reason} onChange={(e) => setReason(e.target.value)} />
        <ErrorNote>{action.error}</ErrorNote>
        <button type="button" className={buttonPrimary} disabled={action.busy} onClick={() => void cancel()}>
          {action.busy ? "Cancelling…" : "Cancel bill"}
        </button>
      </div>
    </Sheet>
  );
}

// --- attachment -------------------------------------------------------------------------------------------------

function contentTypeOf(file: File): string {
  if (file.type) return file.type;
  const name = file.name.toLowerCase();
  if (name.endsWith(".pdf")) return "application/pdf";
  if (name.endsWith(".png")) return "image/png";
  if (name.endsWith(".webp")) return "image/webp";
  return "image/jpeg";
}

/** Uploads a PDF or photo of the bill: a presigned PUT, then the key is saved on the bill. */
function Attachment({ billId, hasAttachment }: { billId: number; hasAttachment: boolean }) {
  const [attached, setAttached] = useState(hasAttachment);
  const [unavailable, setUnavailable] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function start(contentType: string) {
    try {
      return await api.admin.purchases.startAttachment(billId, contentType);
    } catch (e) {
      if (e instanceof ApiError && e.status === 503) {
        setUnavailable(true);
        return null;
      }
      throw e;
    }
  }

  async function upload(file: File) {
    setBusy(true);
    setError(null);
    const contentType = contentTypeOf(file);
    try {
      const started = await start(contentType);
      if (!started) return;
      const response = await fetch(started.uploadUrl, {
        method: "PUT",
        headers: { "Content-Type": contentType },
        body: file,
      });
      if (!response.ok) throw new Error("Uploading the file failed");
      await api.admin.purchases.confirmAttachment(billId, started.key);
      setAttached(true);
    } catch (e) {
      setError(describeError(e).message);
    } finally {
      setBusy(false);
    }
  }

  if (unavailable) {
    return (
      <p className="mt-4 text-sm text-ink-soft">File storage is not set up, so the bill paper cannot be attached.</p>
    );
  }

  return (
    <div className="mt-4">
      <SectionLabel>Bill paper</SectionLabel>
      <div className="flex flex-wrap items-center gap-3">
        <label className={buttonSecondary}>
          {busy ? "Uploading…" : attached ? "Replace bill file" : "Attach bill (PDF or photo)"}
          <input
            type="file"
            accept=".pdf,.jpg,.jpeg,.png,.webp,application/pdf,image/jpeg,image/png,image/webp"
            className="sr-only"
            aria-label="Bill file"
            disabled={busy}
            onChange={(event) => {
              const file = event.target.files?.[0];
              event.target.value = "";
              if (file) void upload(file);
            }}
          />
        </label>
        {attached && <Pill tone="ok">Attached</Pill>}
      </div>
      <ErrorNote>{error}</ErrorNote>
    </div>
  );
}

// --- route ------------------------------------------------------------------------------------------------------

function Route() {
  const params = useSearchParams();
  const id = idParam(params.get("id"));
  const supplierParam = params.get("supplier") ?? "";
  const supplier = /^\d+$/.test(supplierParam) ? supplierParam : "";
  return <BillView key={id ?? `new-${supplier}`} id={id} supplier={supplier} />;
}

/** A supplier bill: new, editable until the books hold it, then read-only with Cancel. */
export default function BillPage() {
  return (
    <Suspense fallback={<p className="text-ink-soft">Loading…</p>}>
      <Route />
    </Suspense>
  );
}
