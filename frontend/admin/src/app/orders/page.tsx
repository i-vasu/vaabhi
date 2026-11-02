"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import type { OrderFilter } from "@vaabhi/api-client";
import { BagIcon, PlusIcon } from "@/components/icons";
import { ErrorNote, Pager, SearchBox, StatusPill } from "@/components/tailoring";
import { ChipGroup, EmptyState, ListCard, ListRow, LoadState, PageHeader, buttonPrimary } from "@/components/ui";
import { api } from "@/lib/api";
import { rupees } from "@/lib/settings";
import { formatDate, formatPhone, idParam, useLoad } from "@/lib/tailoring";

const PAGE_SIZE = 20;
const ORDER_FILTERS: { value: OrderFilter; label: string }[] = [
  { value: "OPEN", label: "Open" },
  { value: "DUE_TODAY", label: "Due today" },
  { value: "OVERDUE", label: "Overdue" },
  { value: "READY", label: "Ready" },
  { value: "DELIVERED", label: "Delivered" },
  { value: "CANCELLED", label: "Cancelled" },
  { value: "ALL", label: "All" },
];

function Orders() {
  const params = useSearchParams();
  const clientId = idParam(params.get("clientId")) ?? undefined;
  const [filter, setFilter] = useState<OrderFilter>(
    ORDER_FILTERS.find((f) => f.value === params.get("filter"))?.value ?? (clientId ? "ALL" : "OPEN"),
  );
  const [q, setQ] = useState("");
  const [page, setPage] = useState(1);
  const result = useLoad(
    () => api.admin.orders.list({ q: q || undefined, filter, clientId, page, pageSize: PAGE_SIZE }),
    [q, filter, clientId, page],
  );

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader
        title="Orders"
        backHref={clientId ? `/customers/view/?id=${clientId}` : undefined}
        actions={
          <Link href={clientId ? `/orders/new/?clientId=${clientId}` : "/orders/new/"} className={buttonPrimary}>
            <PlusIcon size={18} /> New order
          </Link>
        }
      />
      <div className="mb-4 flex flex-col gap-3">
        <SearchBox
          label="Search orders"
          placeholder="Order number, name or mobile"
          onSearch={(query) => {
            setQ(query);
            setPage(1);
          }}
        />
        <ChipGroup
          label="Show orders"
          options={ORDER_FILTERS}
          value={filter}
          onChange={(next) => {
            setFilter(next);
            setPage(1);
          }}
        />
      </div>
      <ErrorNote>{result.error}</ErrorNote>
      {!result.value ? (
        <LoadState loading={result.loading} error={null} />
      ) : result.value.items.length === 0 ? (
        <EmptyState icon={BagIcon} title={q ? `No order matches “${q}”` : "No orders here"}>
          {filter === "OVERDUE" ? "Nothing is past its delivery date." : "Book an order from the customer's page."}
        </EmptyState>
      ) : (
        <>
          <ListCard>
            {result.value.items.map((order) => (
              <ListRow
                key={order.id}
                href={`/orders/view/?id=${order.id}`}
                title={
                  <>
                    {order.number} · {order.clientName} <StatusPill status={order.status} overdue={order.overdue} />
                  </>
                }
                subtitle={`${order.itemsLabel} · ${formatPhone(order.clientPhone)} · due ${formatDate(order.deliveryDate)}`}
                trailing={
                  order.balancePaise > 0 && order.status !== "CANCELLED" ? (
                    <span className="text-warn">{rupees(order.balancePaise)} due</span>
                  ) : (
                    rupees(order.totalPaise)
                  )
                }
              />
            ))}
          </ListCard>
          <Pager
            total={result.value.total}
            page={page}
            pageSize={PAGE_SIZE}
            noun={["order", "orders"]}
            onPage={setPage}
          />
        </>
      )}
    </section>
  );
}

export default function OrdersPage() {
  return (
    <Suspense fallback={<p className="text-ink-soft">Loading…</p>}>
      <Orders />
    </Suspense>
  );
}
