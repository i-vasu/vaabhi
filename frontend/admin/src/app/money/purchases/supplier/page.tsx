"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import type { SupplierPaymentForm } from "@vaabhi/api-client";
import { TruckIcon } from "@/components/icons";
import { ErrorNote, Fact } from "@/components/tailoring";
import {
  EmptyState,
  ListCard,
  ListRow,
  LoadState,
  PageHeader,
  SectionLabel,
  SelectField,
  Sheet,
  StatTile,
  TextField,
  buttonPrimary,
  buttonSecondary,
} from "@/components/ui";
import { api } from "@/lib/api";
import { PAYMENT_MODE_OPTIONS, defaultAllocation } from "@/lib/purchases";
import { parseRupees, rupees } from "@/lib/settings";
import { formatDate, idParam, today, useAction, useLoad } from "@/lib/tailoring";
import { SupplierSheet } from "../supplier-sheet";

/** A supplier: their details, what they are owed, their statement, and the way to add a bill or pay them. */
function SupplierView({ id }: { id: number }) {
  const supplier = useLoad(() => api.admin.purchases.supplier(id), [id]);
  const [editing, setEditing] = useState(false);
  const [paying, setPaying] = useState(false);
  const v = supplier.value;

  if (!v) {
    return (
      <section className="mx-auto max-w-3xl">
        <PageHeader title="Supplier" backHref="/money/purchases/" />
        <LoadState loading={supplier.loading} error={supplier.error} />
      </section>
    );
  }

  const balance = v.balance;

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader
        title={balance.name}
        backHref="/money/purchases/"
        actions={
          <>
            <button type="button" className={buttonSecondary} onClick={() => setEditing(true)}>
              Edit
            </button>
            <button type="button" className={buttonSecondary} onClick={() => setPaying(true)}>
              Pay
            </button>
            <Link href={`/money/purchases/bill/?supplier=${id}`} className={buttonPrimary}>
              New bill
            </Link>
          </>
        }
      />
      <ErrorNote>{supplier.error}</ErrorNote>

      <div className="mb-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
        <StatTile label="Billed" value={rupees(balance.billedRupees)} />
        <StatTile label="Paid" value={rupees(balance.paidRupees)} />
        <StatTile label="TDS deducted" value={rupees(balance.tdsRupees)} />
        <StatTile
          label={balance.balanceRupees < 0 ? "Advance" : "Balance"}
          value={rupees(Math.abs(balance.balanceRupees))}
          tone={balance.balanceRupees > 0 ? "warn" : "ok"}
        />
      </div>

      <dl className="mb-4 grid grid-cols-2 gap-4 rounded-2xl border border-line bg-panel p-4 text-sm sm:grid-cols-3">
        <Fact label="GSTIN">{balance.gstin ?? "—"}</Fact>
        <Fact label="PAN">{v.pan ?? "—"}</Fact>
        <Fact label="Phone">{balance.phone ?? "—"}</Fact>
        <Fact label="State">{balance.state ?? "—"}</Fact>
        <Fact label="Email">{v.email ?? "—"}</Fact>
        <Fact label="Address">{[v.address, v.city, v.pinCode].filter(Boolean).join(", ") || "—"}</Fact>
      </dl>

      <SectionLabel>Statement</SectionLabel>
      {v.entries.length === 0 ? (
        <EmptyState icon={TruckIcon} title="No bills or payments yet">
          Add their first bill to see what you owe.
        </EmptyState>
      ) : (
        <ListCard>
          {v.entries.map((entry) =>
            entry.kind === "BILL" ? (
              <ListRow
                key={`bill-${entry.id}`}
                href={`/money/purchases/bill/?id=${entry.id}`}
                title={entry.reference}
                subtitle={`Bill · ${formatDate(entry.date)}`}
                trailing={
                  <span className="text-right">
                    <span className="block font-medium text-ink">{rupees(entry.billedRupees)}</span>
                    <span className="block text-xs text-ink-faint">balance {rupees(entry.runningBalanceRupees)}</span>
                  </span>
                }
              />
            ) : (
              <ListRow
                key={`payment-${entry.id}`}
                title={entry.reference}
                subtitle={`Payment · ${formatDate(entry.date)}`}
                trailing={
                  <span className="text-right">
                    <span className="block font-medium text-ok">−{rupees(entry.paidRupees)}</span>
                    <span className="block text-xs text-ink-faint">balance {rupees(entry.runningBalanceRupees)}</span>
                  </span>
                }
              />
            ),
          )}
        </ListCard>
      )}

      {editing && (
        <SupplierSheet
          supplier={v}
          onClose={() => setEditing(false)}
          onSaved={() => {
            setEditing(false);
            supplier.reload();
          }}
        />
      )}
      {paying && (
        <PaySheet
          partyId={id}
          supplierName={balance.name}
          onClose={() => setPaying(false)}
          onSaved={() => {
            setPaying(false);
            supplier.reload();
          }}
        />
      )}
    </section>
  );
}

/** Records a payment; its amount spreads over the open bills, oldest first, and can be changed by hand. */
function PaySheet({
  partyId,
  supplierName,
  onClose,
  onSaved,
}: {
  partyId: number;
  supplierName: string;
  onClose: () => void;
  onSaved: () => void;
}) {
  const bills = useLoad(() => api.admin.purchases.bills({ partyId, status: "OPEN" }), [partyId]);
  const [amount, setAmount] = useState("");
  const [mode, setMode] = useState("BANK");
  const [paidOn, setPaidOn] = useState(today());
  const [reference, setReference] = useState("");
  const [edited, setEdited] = useState(false);
  const [allocationText, setAllocationText] = useState<Record<string, string>>({});
  const action = useAction();

  const openBills = (bills.value ?? [])
    .map((bill) => ({
      id: bill.id,
      billNo: bill.billNo,
      billDate: bill.billDate,
      dueDate: bill.dueDate,
      outstandingRupees: Math.max(0, bill.payableRupees - bill.paidRupees),
    }))
    .filter((bill) => bill.outstandingRupees > 0);

  const defaults = Object.fromEntries(
    defaultAllocation(openBills, amount).map((allocation): [number, string] => [
      allocation.billId,
      allocation.amountRupees,
    ]),
  );
  const shown = edited
    ? allocationText
    : Object.fromEntries(openBills.map((bill): [number, string] => [bill.id, defaults[bill.id] ?? ""]));
  const valueFor = (billId: number) => shown[billId] ?? "";

  function changeAmount(value: string) {
    setAmount(value);
    setEdited(false);
    setAllocationText({});
  }

  function changeAllocation(billId: number, value: string) {
    setEdited(true);
    setAllocationText({ ...shown, [billId]: value });
  }

  async function pay() {
    const total = parseRupees(amount);
    if (total === null || total <= 0) return action.setError("Enter the amount paid.");
    const allocations: { billId: number; amountRupees: number }[] = [];
    for (const bill of openBills) {
      const text = valueFor(bill.id).trim();
      if (text === "") continue;
      const amountRupees = parseRupees(text);
      if (amountRupees === null) return action.setError(`Enter a rupee amount for ${bill.billNo}.`);
      if (amountRupees <= 0) continue;
      if (Math.round(amountRupees * 100) > Math.round(bill.outstandingRupees * 100)) {
        return action.setError(`${bill.billNo} only has ${rupees(bill.outstandingRupees)} left to pay.`);
      }
      allocations.push({ billId: bill.id, amountRupees });
    }
    const allocated = allocations.reduce((sum, allocation) => sum + allocation.amountRupees, 0);
    if (Math.round(allocated * 100) > Math.round(total * 100)) {
      return action.setError("Allocated more than paid.");
    }
    const form: SupplierPaymentForm = {
      partyId,
      paidOn,
      mode,
      amountRupees: total,
      reference: reference.trim() || null,
      notes: null,
      allocations,
    };
    const saved = await action.run(() => api.admin.purchases.createPayment(form));
    if (saved) onSaved();
  }

  return (
    <Sheet open title={`Pay ${supplierName}`} onClose={onClose}>
      <div className="flex flex-col gap-4">
        <div className="grid grid-cols-2 gap-3">
          <TextField
            label="Amount (₹)"
            inputMode="decimal"
            value={amount}
            onChange={(e) => changeAmount(e.target.value)}
          />
          <TextField label="Date" type="date" value={paidOn} onChange={(e) => setPaidOn(e.target.value)} />
          <SelectField label="Paid by" options={PAYMENT_MODE_OPTIONS} value={mode} onChange={setMode} />
          <TextField
            label="Reference"
            maxLength={100}
            value={reference}
            onChange={(e) => setReference(e.target.value)}
          />
        </div>

        <SectionLabel>Bills, oldest first</SectionLabel>
        <ErrorNote>{bills.error}</ErrorNote>
        {!bills.value ? (
          <p className="text-sm text-ink-soft">Loading bills…</p>
        ) : openBills.length === 0 ? (
          <p className="text-sm text-ink-soft">No open bills, so the whole amount is an advance.</p>
        ) : (
          <div className="space-y-2">
            {openBills.map((bill) => (
              <div key={bill.id} className="flex items-center gap-3 rounded-xl border border-line p-3">
                <div className="min-w-0 flex-1 text-sm">
                  <p className="font-medium">{bill.billNo}</p>
                  <p className="text-ink-soft">
                    {formatDate(bill.dueDate ?? bill.billDate)} · {rupees(bill.outstandingRupees)} left
                  </p>
                </div>
                <TextField
                  className="w-28"
                  label="Amount (₹)"
                  inputMode="decimal"
                  value={valueFor(bill.id)}
                  onChange={(e) => changeAllocation(bill.id, e.target.value)}
                />
              </div>
            ))}
          </div>
        )}

        <ErrorNote>{action.error}</ErrorNote>
        <button
          type="button"
          className={buttonPrimary}
          disabled={action.busy || bills.loading}
          onClick={() => void pay()}
        >
          {action.busy ? "Saving…" : "Record payment"}
        </button>
      </div>
    </Sheet>
  );
}

function Route() {
  const id = idParam(useSearchParams().get("id"));
  return id ? <SupplierView key={id} id={id} /> : <ErrorNote>No supplier selected.</ErrorNote>;
}

/** One supplier: their statement, their bills and payments, and the actions to add or settle one. */
export default function SupplierPage() {
  return (
    <Suspense fallback={<p className="text-ink-soft">Loading…</p>}>
      <Route />
    </Suspense>
  );
}
