"use client";

import Link from "next/link";
import { useState } from "react";
import { BankIcon, ReceiptIcon, TruckIcon } from "@/components/icons";
import { ErrorNote } from "@/components/tailoring";
import { EmptyState, ListCard, ListRow, LoadState, PageHeader, SegmentedControl, buttonPrimary } from "@/components/ui";
import { api } from "@/lib/api";
import { useIsOwner } from "@/lib/accounts";
import { rupees } from "@/lib/settings";
import { formatDate, useAction, useLoad } from "@/lib/tailoring";

type Tab = "statements" | "razorpay" | "cod";

const TABS: { value: Tab; label: string }[] = [
  { value: "statements", label: "Statements" },
  { value: "razorpay", label: "Razorpay" },
  { value: "cod", label: "COD remittances" },
];

type SyncNote = { tone: "ok" | "warn" | "bad"; text: string };

/** Bank statements, Razorpay settlements and COD remittances, and where they can be reconciled. */
export default function BankPage() {
  const owner = useIsOwner();
  const [tab, setTab] = useState<Tab>("statements");
  const statements = useLoad(() => api.admin.accounts.bank.statements(), []);
  const settlements = useLoad(() => api.admin.accounts.bank.settlements(), []);
  const remittances = useLoad(() => api.admin.accounts.bank.remittances(), []);
  const sync = useAction();
  const [syncNote, setSyncNote] = useState<SyncNote | null>(null);

  async function syncNow() {
    setSyncNote(null);
    const outcome = await sync.run(async () => {
      const result = await api.admin.accounts.bank.syncRazorpay();
      await api.admin.accounts.sweep();
      settlements.reload();
      return result;
    });
    if (!outcome) return;
    if (!outcome.configured) {
      setSyncNote({ tone: "warn", text: "Razorpay is not configured." });
    } else if (outcome.error) {
      setSyncNote({ tone: "bad", text: outcome.error });
    } else {
      setSyncNote({
        tone: "ok",
        text: `Added ${outcome.added} ${outcome.added === 1 ? "settlement" : "settlements"}.`,
      });
    }
  }

  const statementRows = statements.value;
  const settlementRows = settlements.value;
  const remittanceRows = remittances.value;

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader title="Bank reconciliation" backHref="/money/accounts/" />
      <div className="mb-6">
        <SegmentedControl label="Bank reconciliation view" options={TABS} value={tab} onChange={setTab} />
      </div>

      {tab === "statements" && (
        <>
          <ErrorNote>{statements.error}</ErrorNote>
          {owner && (
            <div className="mb-4">
              <Link href="/money/accounts/bank/upload/?kind=bank" className={buttonPrimary}>
                Upload statement
              </Link>
            </div>
          )}
          {!statementRows ? (
            <LoadState loading={statements.loading} error={null} />
          ) : statementRows.length === 0 ? (
            <EmptyState icon={BankIcon} title="No statements yet">
              Upload your bank&apos;s CSV or Excel export.
            </EmptyState>
          ) : (
            <ListCard>
              {statementRows.map((statement) => (
                <ListRow
                  key={statement.id}
                  href={`/money/accounts/bank/statement/?id=${statement.id}`}
                  title={statement.fileName}
                  subtitle={`${formatDate(statement.periodFrom)} – ${formatDate(statement.periodTo)} · ${
                    statement.lineCount
                  } lines · ${statement.unmatchedCount} unmatched`}
                />
              ))}
            </ListCard>
          )}
        </>
      )}

      {tab === "razorpay" && (
        <>
          <ErrorNote>{settlements.error ?? sync.error}</ErrorNote>
          {owner && (
            <div className="mb-4 flex flex-wrap items-center gap-3">
              <button type="button" className={buttonPrimary} disabled={sync.busy} onClick={syncNow}>
                {sync.busy ? "Syncing…" : "Sync now"}
              </button>
              {syncNote && (
                <p
                  role="status"
                  className={
                    syncNote.tone === "ok"
                      ? "text-sm text-ok"
                      : syncNote.tone === "warn"
                        ? "text-sm text-warn"
                        : "text-sm text-bad"
                  }
                >
                  {syncNote.text}
                </p>
              )}
            </div>
          )}
          {!settlementRows ? (
            <LoadState loading={settlements.loading} error={null} />
          ) : settlementRows.length === 0 ? (
            <EmptyState icon={ReceiptIcon} title="No settlements synced yet." />
          ) : (
            <ListCard>
              {settlementRows.map((settlement) => (
                <ListRow
                  key={settlement.id}
                  icon={ReceiptIcon}
                  title={settlement.utr ? `UTR ${settlement.utr}` : settlement.id}
                  subtitle={`${formatDate(settlement.settledOn)} · Gross ${rupees(
                    settlement.grossRupees,
                  )} · Fee ${rupees(settlement.feeRupees)} · Tax ${rupees(settlement.taxRupees)} · Net ${rupees(
                    settlement.netRupees,
                  )}`}
                  trailing={
                    settlement.journalNumber && settlement.journalEntryId ? (
                      <Link
                        href={`/money/accounts/entry/?id=${settlement.journalEntryId}`}
                        className="font-medium text-accent-ink underline"
                      >
                        {settlement.journalNumber}
                      </Link>
                    ) : (
                      "Not posted yet"
                    )
                  }
                />
              ))}
            </ListCard>
          )}
        </>
      )}

      {tab === "cod" && (
        <>
          <ErrorNote>{remittances.error}</ErrorNote>
          {owner && (
            <div className="mb-4">
              <Link href="/money/accounts/bank/upload/?kind=cod" className={buttonPrimary}>
                Upload remittance report
              </Link>
            </div>
          )}
          {!remittanceRows ? (
            <LoadState loading={remittances.loading} error={null} />
          ) : remittanceRows.length === 0 ? (
            <EmptyState icon={TruckIcon} title="No remittance reports yet." />
          ) : (
            <ListCard>
              {remittanceRows.map((remittance) => (
                <ListRow
                  key={remittance.id}
                  icon={TruckIcon}
                  title={remittance.remittanceRef}
                  subtitle={`${formatDate(remittance.remittedOn)} · ${remittance.awbCount} AWBs · COD ${rupees(
                    remittance.codRupees,
                  )} · Deduction ${rupees(remittance.deductionRupees)} · Net ${rupees(remittance.netRupees)}`}
                  trailing={remittance.journalNumber ?? "Not posted yet"}
                />
              ))}
            </ListCard>
          )}
        </>
      )}
    </section>
  );
}
