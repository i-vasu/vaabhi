"use client";

import Link from "next/link";
import { useState } from "react";
import { DocumentIcon } from "@/components/icons";
import { MonthPicker } from "@/components/money";
import { ErrorNote, Pill } from "@/components/tailoring";
import {
  EmptyState,
  ListCard,
  ListRow,
  LoadState,
  PageHeader,
  SelectField,
  StatTile,
  buttonPrimary,
  buttonSecondary,
} from "@/components/ui";
import { api } from "@/lib/api";
import { monthOf } from "@/lib/money";
import { rupees } from "@/lib/settings";
import { formatDate, today, useLoad } from "@/lib/tailoring";

const STATUSES = [
  { value: "", label: "All statuses" },
  { value: "OPEN", label: "Open" },
  { value: "CANCELLED", label: "Cancelled" },
];

/** Supplier bills for a month, filtered by supplier and status. */
export default function BillsPage() {
  const [month, setMonth] = useState(() => monthOf(today()));
  const [supplierId, setSupplierId] = useState("");
  const [status, setStatus] = useState("");
  const suppliers = useLoad(() => api.admin.purchases.suppliers(), []);
  const bills = useLoad(
    () =>
      api.admin.purchases.bills({
        partyId: supplierId ? Number(supplierId) : undefined,
        status: status || undefined,
        month: month.from.slice(0, 7),
      }),
    [supplierId, status, month.from],
  );
  const list = bills.value ?? [];
  const billed = list.reduce((sum, bill) => sum + bill.totalRupees, 0);
  const toPay = list
    .filter((bill) => bill.status === "OPEN")
    .reduce((sum, bill) => sum + Math.max(0, bill.payableRupees - bill.paidRupees), 0);

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader
        title="Bills"
        backHref="/money/purchases/"
        actions={
          <>
            <Link href="/money/purchases/shiprocket/" className={buttonSecondary}>
              Shiprocket invoices
            </Link>
            <Link href="/money/purchases/bill/" className={buttonPrimary}>
              New bill
            </Link>
          </>
        }
      />
      <div className="mb-4 flex flex-col gap-3">
        <MonthPicker value={month} onChange={setMonth} />
        <div className="grid grid-cols-2 gap-3">
          <SelectField
            label="Supplier"
            options={[
              { value: "", label: "All suppliers" },
              ...(suppliers.value ?? []).map((supplier) => ({
                value: String(supplier.partyId),
                label: supplier.name,
              })),
            ]}
            value={supplierId}
            onChange={setSupplierId}
          />
          <SelectField label="Status" options={STATUSES} value={status} onChange={setStatus} />
        </div>
      </div>
      <ErrorNote>{bills.error ?? suppliers.error}</ErrorNote>
      {!bills.value ? (
        <LoadState loading={bills.loading} error={null} />
      ) : list.length === 0 ? (
        <EmptyState icon={DocumentIcon} title="No bills yet">
          Record what a supplier billed you, or import Shiprocket&apos;s freight invoices.
        </EmptyState>
      ) : (
        <>
          <div className="mb-4 grid grid-cols-2 gap-3">
            <StatTile label="Billed" value={rupees(billed)} />
            <StatTile label="Still to pay" value={rupees(toPay)} tone={toPay > 0 ? "warn" : "default"} />
          </div>
          <ListCard>
            {list.map((bill) => (
              <ListRow
                key={bill.id}
                href={`/money/purchases/bill/?id=${bill.id}`}
                title={
                  <>
                    {bill.billNo} · {bill.supplierName}{" "}
                    {bill.status === "CANCELLED" && <Pill>Cancelled</Pill>}
                  </>
                }
                subtitle={[
                  formatDate(bill.billDate),
                  bill.dueDate ? `due ${formatDate(bill.dueDate)}` : null,
                  bill.source === "SHIPROCKET" ? "Shiprocket" : null,
                ]
                  .filter(Boolean)
                  .join(" · ")}
                trailing={
                  <span className="text-right">
                    <span className="block font-medium text-ink">{rupees(bill.totalRupees)}</span>
                    {bill.paidRupees > 0 && (
                      <span className="block text-xs text-ink-faint">paid {rupees(bill.paidRupees)}</span>
                    )}
                  </span>
                }
              />
            ))}
          </ListCard>
        </>
      )}
    </section>
  );
}
