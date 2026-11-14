"use client";

import { useState } from "react";
import type { PeriodStatus } from "@vaabhi/api-client";
import {
  EmptyState,
  ListCard,
  ListRow,
  LoadState,
  PageHeader,
  Sheet,
  buttonPrimary,
  buttonSecondary,
} from "@/components/ui";
import { CalendarIcon } from "@/components/icons";
import { ErrorNote, Pill } from "@/components/tailoring";
import { monthTitle, useIsOwner } from "@/lib/accounts";
import { api } from "@/lib/api";
import { formatDate, useAction, useLoad } from "@/lib/tailoring";

/** Months can be locked once they are closed: entries dated in a locked month are refused. */
export default function PeriodsPage() {
  const owner = useIsOwner();
  const periods = useLoad(() => api.admin.accounts.periods(), []);
  const action = useAction();

  const [locking, setLocking] = useState<PeriodStatus | null>(null);

  async function change(period: PeriodStatus) {
    const month = period.month.slice(0, 7);
    const result = await action.run(() =>
      period.locked ? api.admin.accounts.unlock(month) : api.admin.accounts.lock(month),
    );
    if (result) {
      periods.setValue(result);
      setLocking(null);
    }
  }

  const v = periods.value;

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader title="Periods" backHref="/money/accounts/" />
      <ErrorNote>{periods.error ?? action.error}</ErrorNote>
      {!v ? (
        <LoadState loading={periods.loading} error={null} />
      ) : v.length === 0 ? (
        <EmptyState icon={CalendarIcon} title="No months yet">
          Months appear here once the books have started.
        </EmptyState>
      ) : (
        <ListCard>
          {v.map((period) => (
            <ListRow
              key={period.month}
              title={
                <>
                  {monthTitle(period.month)}{" "}
                  <Pill tone={period.locked ? "warn" : "ok"}>{period.locked ? "Locked" : "Open"}</Pill>
                </>
              }
              subtitle={
                period.locked && period.lockedBy
                  ? `Locked by ${period.lockedBy}${period.lockedAt ? ` on ${formatDate(period.lockedAt)}` : ""}`
                  : undefined
              }
              trailing={
                owner ? (
                  <button
                    type="button"
                    className={buttonSecondary}
                    disabled={action.busy}
                    onClick={() => (period.locked ? change(period) : setLocking(period))}
                  >
                    {period.locked ? "Unlock" : "Lock"}
                  </button>
                ) : undefined
              }
            />
          ))}
        </ListCard>
      )}
      <Sheet
        open={locking !== null}
        title={locking ? `Lock ${monthTitle(locking.month)}?` : "Lock month?"}
        onClose={() => setLocking(null)}
      >
        <p className="mb-4 text-ink-soft">
          {locking ? `Entries dated in ${monthTitle(locking.month)} will be refused.` : ""}
        </p>
        <ErrorNote>{action.error}</ErrorNote>
        <div className="mt-4 flex gap-3">
          <button
            type="button"
            className={buttonPrimary}
            disabled={action.busy}
            onClick={() => locking && change(locking)}
          >
            {action.busy ? "Locking…" : "Lock month"}
          </button>
          <button type="button" className={buttonSecondary} onClick={() => setLocking(null)}>
            Cancel
          </button>
        </div>
      </Sheet>
    </section>
  );
}
