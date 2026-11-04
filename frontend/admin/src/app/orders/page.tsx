"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import type { OrderFilter, OrderSource, OrderSummary } from "@vaabhi/api-client";
import { BagIcon, PlusIcon } from "@/components/icons";
import { ErrorNote, Pager, Pill, SearchBox, StatusPill } from "@/components/tailoring";
import { ChipGroup, EmptyState, ListCard, ListRow, LoadState, PageHeader, buttonPrimary } from "@/components/ui";
import { api } from "@/lib/api";
import { rupees } from "@/lib/settings";
import { formatDate, formatPhone, idParam, useLoad } from "@/lib/tailoring";

const PAGE_SIZE = 20;

/** The status chips; RETURNS is the website orders with a return to handle. */
type Show = OrderFilter | "RETURNS";
const ORDER_FILTERS: { value: Show; label: string }[] = [
  { value: "OPEN", label: "Open" },
  { value: "DUE_TODAY", label: "Due today" },
  { value: "OVERDUE", label: "Overdue" },
  { value: "READY", label: "Ready / shipped" },
  { value: "DELIVERED", label: "Delivered" },
  { value: "RETURNS", label: "Returns" },
  { value: "CANCELLED", label: "Cancelled" },
  { value: "ALL", label: "All" },
];

type SourceChoice = "ALL" | OrderSource;
const SOURCES: { value: SourceChoice; label: string }[] = [
  { value: "ALL", label: "Shop & online" },
  { value: "SHOP", label: "Shop" },
  { value: "ONLINE", label: "Online" },
];

/** A website order's own status, as the shop says it. */
const ONLINE_STATUS: Record<string, string> = {
  PLACED: "To ship",
  SHIPPED: "Shipped",
  DELIVERED: "Delivered",
  CANCELLED: "Cancelled",
};

function Orders() {
  const params = useSearchParams();
  const clientId = idParam(params.get("clientId")) ?? undefined;
  const [show, setShow] = useState<Show>(
    ORDER_FILTERS.find((f) => f.value === params.get("filter"))?.value ?? (clientId ? "ALL" : "OPEN"),
  );
  const [source, setSource] = useState<SourceChoice>(
    SOURCES.find((s) => s.value === params.get("source"))?.value ?? "ALL",
  );
  const [q, setQ] = useState("");
  const [page, setPage] = useState(1);
  const returns = show === "RETURNS";
  const result = useLoad(
    () =>
      api.admin.orders.list({
        q: q || undefined,
        filter: returns ? "ALL" : show,
        source: returns ? "ONLINE" : source === "ALL" ? undefined : source,
        openReturns: returns || undefined,
        clientId,
        page,
        pageSize: PAGE_SIZE,
      }),
    [q, show, source, clientId, page],
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
        {!clientId && (
          <ChipGroup
            label="Where orders came from"
            options={SOURCES}
            value={source}
            onChange={(next) => {
              setSource(next);
              setPage(1);
            }}
          />
        )}
        <ChipGroup
          label="Show orders"
          options={ORDER_FILTERS}
          value={show}
          onChange={(next) => {
            setShow(next);
            setPage(1);
          }}
        />
      </div>
      <ErrorNote>{result.error}</ErrorNote>
      {!result.value ? (
        <LoadState loading={result.loading} error={null} />
      ) : result.value.items.length === 0 ? (
        <EmptyState icon={BagIcon} title={q ? `No order matches “${q}”` : "No orders here"}>
          {show === "OVERDUE"
            ? "Nothing is past its delivery date."
            : returns
              ? "No returns to handle."
              : "Book an order from the customer's page; website orders appear here once paid."}
        </EmptyState>
      ) : (
        <>
          <ListCard>
            {result.value.items.map((order) => (
              <OrderRow key={`${order.source}-${order.id}`} order={order} />
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

function OrderRow({ order }: Readonly<{ order: OrderSummary }>) {
  const online = order.source === "ONLINE";
  const status = online ? (
    <Pill tone={order.onlineStatus === "PLACED" ? "accent" : order.onlineStatus === "DELIVERED" ? "ok" : "default"}>
      {ONLINE_STATUS[order.onlineStatus ?? ""] ?? order.onlineStatus}
    </Pill>
  ) : (
    <StatusPill status={order.status} overdue={order.overdue} />
  );
  const when = online
    ? `ordered ${formatDate(order.orderDate)}`
    : order.deliveryDate
      ? `due ${formatDate(order.deliveryDate)}`
      : "";
  return (
    <ListRow
      href={online ? `/online-orders/view/?number=${encodeURIComponent(order.number)}` : `/orders/view/?id=${order.id}`}
      title={
        <>
          {order.number} <Pill tone={online ? "accent" : "default"}>{online ? "Online" : "Shop"}</Pill> ·{" "}
          {order.clientName} {status}
          {order.openReturn && (
            <>
              {" "}
              <Pill tone="warn">Return</Pill>
            </>
          )}
        </>
      }
      subtitle={[order.itemsLabel, formatPhone(order.clientPhone), when].filter(Boolean).join(" · ")}
      trailing={
        order.balanceRupees > 0 && order.status !== "CANCELLED" ? (
          <span className="text-warn">{rupees(order.balanceRupees)} due</span>
        ) : (
          rupees(order.totalRupees)
        )
      }
    />
  );
}

export default function OrdersPage() {
  return (
    <Suspense fallback={<p className="text-ink-soft">Loading…</p>}>
      <Orders />
    </Suspense>
  );
}
