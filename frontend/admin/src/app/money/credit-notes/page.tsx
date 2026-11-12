"use client";

import { useState } from "react";
import { DocumentIcon } from "@/components/icons";
import { ErrorNote, Pager, SearchBox } from "@/components/tailoring";
import { EmptyState, ListCard, ListRow, LoadState, PageHeader } from "@/components/ui";
import { api } from "@/lib/api";
import { rupees } from "@/lib/settings";
import { formatDate, useLoad } from "@/lib/tailoring";

const PAGE_SIZE = 20;

/** Credit notes, newest first. */
export default function CreditNotesPage() {
  const [q, setQ] = useState("");
  const [page, setPage] = useState(1);
  const notes = useLoad(
    () => api.admin.money.creditNotes.list({ q: q || undefined, page, size: PAGE_SIZE }),
    [q, page],
  );
  const v = notes.value;

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader title="Credit notes" backHref="/money/" />
      <div className="mb-4">
        <SearchBox
          label="Search credit notes"
          placeholder="Credit note or invoice number, customer"
          onSearch={(query) => {
            setQ(query);
            setPage(1);
          }}
        />
      </div>
      <ErrorNote>{notes.error}</ErrorNote>
      {!v ? (
        <LoadState loading={notes.loading} error={null} />
      ) : v.items.length === 0 ? (
        <EmptyState icon={DocumentIcon} title={q ? `No credit notes match “${q}”` : "No credit notes"}>
          {q
            ? "Try the credit note or invoice number, or the customer's name."
            : "Credit notes are made from an invoice, or when a return is refunded."}
        </EmptyState>
      ) : (
        <>
          <ListCard>
            {v.items.map((note) => (
              <ListRow
                key={note.id}
                href={`/money/credit-notes/view/?id=${note.id}`}
                title={`${note.number} · ${note.buyerName}`}
                subtitle={`${formatDate(note.issuedOn)} · Against ${note.invoiceNumber}`}
                trailing={<span className="font-medium text-ink">{rupees(note.totalRupees)}</span>}
              />
            ))}
          </ListCard>
          <Pager
            total={v.total}
            page={page}
            pageSize={PAGE_SIZE}
            noun={["credit note", "credit notes"]}
            onPage={setPage}
          />
        </>
      )}
    </section>
  );
}
