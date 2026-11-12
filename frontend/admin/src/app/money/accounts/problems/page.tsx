"use client";

import Link from "next/link";
import { DocumentIcon } from "@/components/icons";
import { ErrorNote } from "@/components/tailoring";
import { EmptyState, ListCard, ListRow, LoadState, PageHeader, buttonPrimary } from "@/components/ui";
import { relativeTime, sourceHref, sourceLabel, useIsOwner } from "@/lib/accounts";
import { api } from "@/lib/api";
import { useAction, useLoad } from "@/lib/tailoring";

/** Things the books could not post, with why; "Post now" tries them again. */
export default function ProblemsPage() {
  const owner = useIsOwner();
  const problems = useLoad(() => api.admin.accounts.problems(), []);
  const sweep = useAction();
  const v = problems.value;

  async function postNow() {
    const result = await sweep.run(() => api.admin.accounts.sweep());
    if (result) problems.reload();
  }

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader
        title="Posting problems"
        backHref="/money/accounts/"
        actions={
          owner ? (
            <button type="button" className={buttonPrimary} disabled={sweep.busy} onClick={postNow}>
              {sweep.busy ? "Posting…" : "Post now"}
            </button>
          ) : undefined
        }
      />
      <ErrorNote>{problems.error ?? sweep.error}</ErrorNote>
      {!v ? (
        <LoadState loading={problems.loading} error={null} />
      ) : v.length === 0 ? (
        <EmptyState icon={DocumentIcon} title="No problems">
          Everything that could be posted has been posted.
        </EmptyState>
      ) : (
        <ListCard>
          {v.map((problem) => {
            const label = `${sourceLabel(problem.sourceType)} #${problem.sourceId}`;
            // Payments, web orders and returns are found by an order id or number, which a problem does not carry.
            const href = sourceHref({
              type: problem.sourceType,
              id: problem.sourceId,
              label: String(problem.sourceId),
              orderId: null,
              alterationId: null,
              orderNumber: null,
            });
            return (
              <ListRow
                key={`${problem.sourceType}-${problem.sourceId}-${problem.sourceStep}`}
                title={
                  href ? (
                    <Link href={href} className="text-accent-ink underline">
                      {label}
                    </Link>
                  ) : (
                    label
                  )
                }
                subtitle={
                  <>
                    <span className="block text-bad">{problem.message}</span>
                    <span className="block">
                      First seen {relativeTime(problem.firstSeen)} · last seen {relativeTime(problem.lastSeen)}
                    </span>
                  </>
                }
              />
            );
          })}
        </ListCard>
      )}
    </section>
  );
}
