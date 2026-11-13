"use client";

import { useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import type { BankLineView } from "@vaabhi/api-client";
import { ErrorNote } from "@/components/tailoring";
import {
  ListCard,
  ListRow,
  LoadState,
  PageHeader,
  SectionLabel,
  StatTile,
  buttonSecondary,
} from "@/components/ui";
import { api } from "@/lib/api";
import { rupees } from "@/lib/settings";
import { formatDate, idParam, useLoad } from "@/lib/tailoring";

function lineMoney(line: BankLineView): string {
  return line.creditRupees > 0 ? rupees(line.creditRupees) : rupees(line.debitRupees);
}

function ReportView({ id }: { id: number }) {
  const report = useLoad(() => api.admin.accounts.bank.report(id), [id]);
  const [downloadError, setDownloadError] = useState<string | null>(null);
  const v = report.value;

  async function downloadCsv() {
    if (!v) return;
    setDownloadError(null);
    try {
      const blob = await api.admin.accounts.bank.reportCsv(id);
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `bank-reconciliation-${v.statement.periodTo}.csv`;
      link.click();
      setTimeout(() => URL.revokeObjectURL(url), 0);
    } catch (e) {
      setDownloadError(e instanceof Error ? e.message : "Could not download the CSV");
    }
  }

  if (!v) {
    return (
      <section className="mx-auto max-w-3xl">
        <PageHeader title="Reconciliation report" backHref={`/money/accounts/bank/statement/?id=${id}`} />
        <LoadState loading={report.loading} error={report.error} />
      </section>
    );
  }

  const difference = v.differenceRupees;
  const balanced = difference !== null && Math.abs(difference) < 0.005;

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader
        title="Reconciliation report"
        backHref={`/money/accounts/bank/statement/?id=${id}`}
        badge={`${formatDate(v.statement.periodFrom)} – ${formatDate(v.statement.periodTo)}`}
        actions={
          <button type="button" className={buttonSecondary} onClick={downloadCsv}>
            Download CSV
          </button>
        }
      />
      <ErrorNote>{report.error ?? downloadError}</ErrorNote>

      {v.openingMismatch && (
        <p className="mb-4 rounded-xl bg-warn-soft px-4 py-3 text-warn">
          The statement&apos;s opening balance doesn&apos;t match the books. Check it before reconciling.
        </p>
      )}

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <StatTile
          label="Statement closing"
          value={v.statementClosingRupees === null ? "—" : rupees(v.statementClosingRupees)}
        />
        <StatTile label="Ledger balance" value={rupees(v.ledgerBalanceRupees)} />
        <StatTile
          label="Difference"
          value={difference === null ? "—" : rupees(difference)}
          tone={difference === null ? "default" : balanced ? "ok" : "bad"}
          surface
        />
        <StatTile label="Razorpay clearing" value={rupees(v.razorpayClearingRupees)} />
        <StatTile label="COD clearing" value={rupees(v.codClearingRupees)} />
        <StatTile
          label="Oldest unsettled web payment"
          value={v.oldestUnsettledWebPayment ? formatDate(v.oldestUnsettledWebPayment) : "None"}
        />
      </div>

      <SectionLabel>Bank lines not in the books</SectionLabel>
      {v.unmatchedLines.length === 0 ? (
        <p className="text-sm text-ink-soft">None.</p>
      ) : (
        <ListCard>
          {v.unmatchedLines.map((line) => (
            <ListRow
              key={line.id}
              title={line.description}
              subtitle={`${formatDate(line.date)}${line.reference ? ` · ${line.reference}` : ""}`}
              trailing={<span className="tabular-nums">{lineMoney(line)}</span>}
            />
          ))}
        </ListCard>
      )}

      <SectionLabel>Ignored lines</SectionLabel>
      {v.ignoredLines.length === 0 ? (
        <p className="text-sm text-ink-soft">None.</p>
      ) : (
        <ListCard>
          {v.ignoredLines.map((line) => (
            <ListRow
              key={line.id}
              title={line.description}
              subtitle={`${formatDate(line.date)}${line.ignoreReason ? ` · ${line.ignoreReason}` : ""}`}
              trailing={<span className="tabular-nums">{lineMoney(line)}</span>}
            />
          ))}
        </ListCard>
      )}

      <SectionLabel>In the books but not on the statement</SectionLabel>
      {v.ledgerOnly.length === 0 ? (
        <p className="text-sm text-ink-soft">None.</p>
      ) : (
        <ListCard>
          {v.ledgerOnly.map((movement) => (
            <ListRow
              key={movement.journalEntryId}
              href={`/money/accounts/entry/?id=${movement.journalEntryId}`}
              title={movement.number}
              subtitle={`${formatDate(movement.date)} · ${movement.narration}`}
              trailing={
                <span className="tabular-nums">
                  {movement.debitRupees > 0 ? rupees(movement.debitRupees) : rupees(movement.creditRupees)}
                </span>
              }
            />
          ))}
        </ListCard>
      )}
    </section>
  );
}

function Route() {
  const id = idParam(useSearchParams().get("id"));
  return id ? <ReportView key={id} id={id} /> : <ErrorNote>No statement selected.</ErrorNote>;
}

/** How a statement reconciles to the books: balances, clearing accounts, and what is on one side only. */
export default function ReportPage() {
  return (
    <Suspense fallback={<p className="text-ink-soft">Loading…</p>}>
      <Route />
    </Suspense>
  );
}
