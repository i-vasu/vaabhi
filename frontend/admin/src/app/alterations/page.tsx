"use client";

import Link from "next/link";
import { useState } from "react";
import type { OrderFilter } from "@vaabhi/api-client";
import { PlusIcon, RulerIcon } from "@/components/icons";
import { ErrorNote, Pager, SearchBox, StatusPill } from "@/components/tailoring";
import { ChipGroup, EmptyState, ListCard, ListRow, LoadState, PageHeader, buttonPrimary } from "@/components/ui";
import { api } from "@/lib/api";
import { rupees } from "@/lib/settings";
import { formatDate, useLoad } from "@/lib/tailoring";

const PAGE_SIZE = 20;
const FILTERS: { value: OrderFilter; label: string }[] = [
  { value: "OPEN", label: "Open" },
  { value: "OVERDUE", label: "Overdue" },
  { value: "READY", label: "Ready" },
  { value: "DELIVERED", label: "Delivered" },
  { value: "ALL", label: "All" },
];

export default function AlterationsPage() {
  const [filter, setFilter] = useState<OrderFilter>("OPEN");
  const [q, setQ] = useState("");
  const [page, setPage] = useState(1);
  const result = useLoad(
    () => api.admin.alterations.list({ q: q || undefined, filter, page, pageSize: PAGE_SIZE }),
    [q, filter, page],
  );

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader
        title="Alterations"
        actions={
          <Link href="/alterations/new/" className={buttonPrimary}>
            <PlusIcon size={18} /> New alteration
          </Link>
        }
      />
      <div className="mb-4 flex flex-col gap-3">
        <SearchBox
          label="Search alterations"
          placeholder="Number, name or mobile"
          onSearch={(query) => {
            setQ(query);
            setPage(1);
          }}
        />
        <ChipGroup
          label="Show alterations"
          options={FILTERS}
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
        <EmptyState icon={RulerIcon} title="No alterations here">
          Book one when a customer brings a garment back for a fix.
        </EmptyState>
      ) : (
        <>
          <ListCard>
            {result.value.items.map((alteration) => (
              <ListRow
                key={alteration.id}
                href={`/alterations/view/?id=${alteration.id}`}
                title={
                  <>
                    {alteration.number} · {alteration.clientName}{" "}
                    <StatusPill status={alteration.status} overdue={alteration.overdue} />
                  </>
                }
                subtitle={`${alteration.itemsLabel} · ready by ${formatDate(alteration.readyBy)}`}
                trailing={
                  !alteration.paid ? (
                    "Free"
                  ) : alteration.balancePaise > 0 ? (
                    <span className="text-warn">{rupees(alteration.balancePaise)} due</span>
                  ) : (
                    rupees(alteration.totalPaise)
                  )
                }
              />
            ))}
          </ListCard>
          <Pager
            total={result.value.total}
            page={page}
            pageSize={PAGE_SIZE}
            noun={["alteration", "alterations"]}
            onPage={setPage}
          />
        </>
      )}
    </section>
  );
}
