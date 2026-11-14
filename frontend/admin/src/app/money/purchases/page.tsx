"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { TruckIcon } from "@/components/icons";
import { ErrorNote, Pill, SearchBox } from "@/components/tailoring";
import {
  EmptyState,
  ListCard,
  ListRow,
  LoadState,
  PageHeader,
  SectionLabel,
  StatTile,
  buttonPrimary,
  buttonSecondary,
} from "@/components/ui";
import { api } from "@/lib/api";
import { rupees } from "@/lib/settings";
import { formatDate, useLoad } from "@/lib/tailoring";
import { SupplierSheet } from "./supplier-sheet";

/** The shops the shop buys from, what each is owed, and the way into their bills and payments. */
export default function PurchasesPage() {
  const router = useRouter();
  const [q, setQ] = useState("");
  const [adding, setAdding] = useState(false);
  const suppliers = useLoad(() => api.admin.purchases.suppliers(q || undefined), [q]);
  const list = suppliers.value ?? [];
  const owed = list.reduce((sum, supplier) => sum + Math.max(0, supplier.balanceRupees), 0);

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader
        title="Purchases"
        backHref="/money/"
        actions={
          <>
            <Link href="/money/purchases/bills/" className={buttonSecondary}>
              Bills
            </Link>
            <Link href="/money/purchases/shiprocket/" className={buttonSecondary}>
              Shiprocket invoices
            </Link>
            <Link href="/money/purchases/bill/" className={buttonPrimary}>
              New bill
            </Link>
          </>
        }
      />
      <div className="mb-4">
        <SearchBox label="Search suppliers" placeholder="Name, phone or GSTIN" onSearch={setQ} />
      </div>
      <ErrorNote>{suppliers.error}</ErrorNote>
      <SectionLabel
        action={
          <button type="button" className={buttonSecondary} onClick={() => setAdding(true)}>
            Add supplier
          </button>
        }
      >
        Suppliers
      </SectionLabel>
      {!suppliers.value ? (
        <LoadState loading={suppliers.loading} error={null} />
      ) : list.length === 0 ? (
        <EmptyState icon={TruckIcon} title="No suppliers yet">
          Add the shops and suppliers you buy from to record their bills and what you still owe them.
        </EmptyState>
      ) : (
        <>
          <div className="mb-4 grid grid-cols-2 gap-3">
            <StatTile label="You owe" value={rupees(owed)} tone={owed > 0 ? "warn" : "default"} />
            <StatTile label="Suppliers" value={list.length} />
          </div>
          <ListCard>
            {list.map((supplier) => (
              <ListRow
                key={supplier.partyId}
                href={`/money/purchases/supplier/?id=${supplier.partyId}`}
                title={
                  <>
                    {supplier.name} {supplier.overdue && <Pill tone="warn">Overdue</Pill>}
                  </>
                }
                subtitle={
                  [supplier.gstin, supplier.state].filter(Boolean).join(" · ") ||
                  (supplier.phone ?? "No GSTIN or state")
                }
                trailing={
                  <span className="text-right">
                    <span className="block font-medium text-ink">{rupees(supplier.balanceRupees)}</span>
                    {supplier.oldestUnpaidDue && (
                      <span className={`block text-xs ${supplier.overdue ? "text-warn" : "text-ink-faint"}`}>
                        due {formatDate(supplier.oldestUnpaidDue)}
                      </span>
                    )}
                  </span>
                }
              />
            ))}
          </ListCard>
        </>
      )}
      {adding && (
        <SupplierSheet
          supplier={null}
          onClose={() => setAdding(false)}
          onSaved={(id) => router.push(`/money/purchases/supplier/?id=${id}`)}
        />
      )}
    </section>
  );
}
