"use client";

import Link from "next/link";
import { useState } from "react";
import type { ClientListParams } from "@vaabhi/api-client";
import { PlusIcon, UsersIcon } from "@/components/icons";
import { ErrorNote, Pager, Pill, SearchBox } from "@/components/tailoring";
import { ChipGroup, EmptyState, ListCard, ListRow, LoadState, PageHeader, buttonPrimary } from "@/components/ui";
import { api } from "@/lib/api";
import { rupees } from "@/lib/settings";
import { formatDate, formatPhone, useLoad } from "@/lib/tailoring";

type Sort = NonNullable<ClientListParams["sort"]>;
const PAGE_SIZE = 20;
const SORTS: { value: Sort; label: string }[] = [
  { value: "RECENT", label: "Recent" },
  { value: "NAME", label: "A–Z" },
  { value: "VALUE", label: "Top spenders" },
  { value: "OWING", label: "Owing" },
];

export default function CustomersPage() {
  const [q, setQ] = useState("");
  const [sort, setSort] = useState<Sort>("RECENT");
  const [page, setPage] = useState(1);
  const result = useLoad(
    () => api.admin.clients.list({ q: q || undefined, sort, page, pageSize: PAGE_SIZE }),
    [q, sort, page],
  );

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader
        title="Customers"
        actions={
          <Link href="/customers/new/" className={buttonPrimary}>
            <PlusIcon size={18} /> Add customer
          </Link>
        }
      />
      <div className="mb-4 flex flex-col gap-3">
        <SearchBox
          label="Search customers"
          placeholder="Name or mobile number"
          onSearch={(query) => {
            setQ(query);
            setPage(1);
          }}
        />
        <ChipGroup
          label="Sort customers"
          options={SORTS}
          value={sort}
          onChange={(next) => {
            setSort(next);
            setPage(1);
          }}
        />
      </div>
      <ErrorNote>{result.error}</ErrorNote>
      {!result.value ? (
        <LoadState loading={result.loading} error={null} />
      ) : result.value.items.length === 0 ? (
        <EmptyState
          icon={UsersIcon}
          title={q ? `No customer matches “${q}”` : "No customers yet"}
          action={
            <Link href="/customers/new/" className={buttonPrimary}>
              Add customer
            </Link>
          }
        >
          {q ? "Try part of the name or the last digits of the number." : "Add the first customer to book an order."}
        </EmptyState>
      ) : (
        <>
          <ListCard>
            {result.value.items.map((client) => (
              <ListRow
                key={client.id}
                href={`/customers/view/?id=${client.id}`}
                title={
                  <>
                    {client.name} {client.clientType !== "RETAIL" && <Pill tone="accent">{client.clientType}</Pill>}
                  </>
                }
                subtitle={
                  <>
                    {formatPhone(client.phone)}
                    {client.lastOrderDate && ` · last order ${formatDate(client.lastOrderDate)}`}
                  </>
                }
                trailing={
                  client.outstandingPaise > 0 ? (
                    <span className="text-warn">{rupees(client.outstandingPaise)} due</span>
                  ) : (
                    `${client.orderCount} ${client.orderCount === 1 ? "order" : "orders"}`
                  )
                }
              />
            ))}
          </ListCard>
          <Pager
            total={result.value.total}
            page={page}
            pageSize={PAGE_SIZE}
            noun={["customer", "customers"]}
            onPage={setPage}
          />
        </>
      )}
    </section>
  );
}
