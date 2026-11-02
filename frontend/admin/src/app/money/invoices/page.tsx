"use client";

import { useState } from "react";
import { DocumentIcon } from "@/components/icons";
import { MonthPicker } from "@/components/money";
import { ErrorNote, Pager, Pill, SearchBox } from "@/components/tailoring";
import { EmptyState, ListCard, ListRow, LoadState, PageHeader, StatTile } from "@/components/ui";
import { api } from "@/lib/api";
import { monthOf } from "@/lib/money";
import { rupees } from "@/lib/settings";
import { formatDate, today, useLoad } from "@/lib/tailoring";

const PAGE_SIZE = 20;

/** The invoice register for a month, with what was billed and the GST on it. */
export default function InvoicesPage() {
  const [month, setMonth] = useState(() => monthOf(today()));
  const [q, setQ] = useState("");
  const [page, setPage] = useState(1);
  const invoices = useLoad(
    () =>
      api.admin.money.invoices({
        q: q || undefined,
        from: q ? undefined : month.from,
        to: q ? undefined : month.to,
        page,
        pageSize: PAGE_SIZE,
      }),
    [q, month.from, page],
  );
  const v = invoices.value;

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader title="Invoices" backHref="/money/" />
      <div className="mb-4 flex flex-col gap-3">
        <SearchBox
          label="Search invoices"
          placeholder="Invoice or order number, customer (all months)"
          onSearch={(query) => {
            setQ(query);
            setPage(1);
          }}
        />
        {!q && (
          <MonthPicker
            value={month}
            onChange={(next) => {
              setMonth(next);
              setPage(1);
            }}
          />
        )}
      </div>
      <ErrorNote>{invoices.error}</ErrorNote>
      {!v ? (
        <LoadState loading={invoices.loading} error={null} />
      ) : (
        <>
          <div className="mb-4 grid grid-cols-2 gap-3">
            <StatTile label="Billed" value={rupees(v.totalPaise)} />
            <StatTile label="GST" value={rupees(v.taxPaise)} />
          </div>
          {v.items.length === 0 ? (
            <EmptyState icon={DocumentIcon} title="No invoices">
              Invoices are made when an order is delivered, or from the order&apos;s page.
            </EmptyState>
          ) : (
            <>
              <ListCard>
                {v.items.map((invoice) => (
                  <ListRow
                    key={invoice.id}
                    href={`/money/invoices/view/?id=${invoice.id}`}
                    title={
                      <>
                        {invoice.number} · {invoice.clientName} {invoice.cancelled && <Pill tone="bad">Cancelled</Pill>}
                      </>
                    }
                    subtitle={`${formatDate(invoice.issuedOn)} · ${invoice.orderNumber}${invoice.taxPaise > 0 ? ` · GST ${rupees(invoice.taxPaise)}` : ""}`}
                    trailing={
                      <span>
                        <span className={invoice.cancelled ? "block line-through" : "block font-medium text-ink"}>
                          {rupees(invoice.totalPaise)}
                        </span>
                        {invoice.balancePaise > 0 && (
                          <span className="block text-xs text-warn">{rupees(invoice.balancePaise)} due</span>
                        )}
                      </span>
                    }
                  />
                ))}
              </ListCard>
              <Pager total={v.total} page={page} pageSize={PAGE_SIZE} noun={["invoice", "invoices"]} onPage={setPage} />
            </>
          )}
        </>
      )}
    </section>
  );
}
