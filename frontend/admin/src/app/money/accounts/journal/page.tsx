"use client";

import { useState } from "react";
import { DocumentIcon } from "@/components/icons";
import { ErrorNote, Pager, Pill, SearchBox } from "@/components/tailoring";
import { EmptyState, ListCard, ListRow, LoadState, PageHeader, TextField } from "@/components/ui";
import { sourceLabel } from "@/lib/accounts";
import { api } from "@/lib/api";
import { rupees } from "@/lib/settings";
import { formatDate, useLoad } from "@/lib/tailoring";

const PAGE_SIZE = 20;

/** Every journal entry, newest first, with search and a date range. */
export default function JournalPage() {
  const [q, setQ] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [page, setPage] = useState(1);
  const journal = useLoad(
    () =>
      api.admin.accounts.journal({
        q: q || undefined,
        from: from || undefined,
        to: to || undefined,
        page,
        size: PAGE_SIZE,
      }),
    [q, from, to, page],
  );
  const v = journal.value;

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader title="Journal" backHref="/money/accounts/" />
      <div className="mb-4 flex flex-col gap-3">
        <SearchBox
          label="Search journal"
          placeholder="Entry number or narration"
          onSearch={(query) => {
            setQ(query);
            setPage(1);
          }}
        />
        <div className="grid grid-cols-2 gap-3">
          <TextField
            label="From"
            type="date"
            value={from}
            onChange={(e) => {
              setFrom(e.target.value);
              setPage(1);
            }}
          />
          <TextField
            label="To"
            type="date"
            value={to}
            onChange={(e) => {
              setTo(e.target.value);
              setPage(1);
            }}
          />
        </div>
      </div>
      <ErrorNote>{journal.error}</ErrorNote>
      {!v ? (
        <LoadState loading={journal.loading} error={null} />
      ) : v.items.length === 0 ? (
        <EmptyState icon={DocumentIcon} title="No entries">
          Entries appear here as the books are posted.
        </EmptyState>
      ) : (
        <>
          <ListCard>
            {v.items.map((entry) => (
              <ListRow
                key={entry.id}
                href={`/money/accounts/entry/?id=${entry.id}`}
                title={
                  <>
                    {entry.number} {entry.reversed && <Pill tone="warn">Reversed</Pill>}
                  </>
                }
                subtitle={`${formatDate(entry.date)} · ${sourceLabel(entry.sourceType)} · ${entry.narration}`}
                trailing={<span className="font-medium text-ink">{rupees(entry.totalRupees)}</span>}
              />
            ))}
          </ListCard>
          <Pager total={v.total} page={page} pageSize={PAGE_SIZE} noun={["entry", "entries"]} onPage={setPage} />
        </>
      )}
    </section>
  );
}
