"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import type { Account, BankLineView } from "@vaabhi/api-client";
import { DocumentIcon } from "@/components/icons";
import { ErrorNote, Pill } from "@/components/tailoring";
import {
  ChipGroup,
  EmptyState,
  LoadState,
  PageHeader,
  SectionLabel,
  SelectField,
  Sheet,
  TextField,
  buttonPrimary,
  buttonSecondary,
} from "@/components/ui";
import { useIsOwner } from "@/lib/accounts";
import { api } from "@/lib/api";
import { LINE_STATUSES } from "@/lib/bank";
import { rupees } from "@/lib/settings";
import { formatDate, idParam, useAction, useLoad } from "@/lib/tailoring";

const STATUS_LABELS: Record<string, string> = {
  UNMATCHED: "Unmatched",
  MATCHED: "Matched",
  POSTED: "Posted",
  IGNORED: "Ignored",
};

const STATUS_TONES: Record<string, "default" | "accent" | "ok" | "warn"> = {
  UNMATCHED: "warn",
  MATCHED: "ok",
  POSTED: "accent",
  IGNORED: "default",
};

/** The line's amount, by the side it moves the bank. */
function lineMoney(line: BankLineView): string {
  return line.creditRupees > 0 ? rupees(line.creditRupees) : rupees(line.debitRupees);
}

function accountOptions(accounts: Account[]) {
  return [
    { value: "", label: "Choose an account" },
    ...accounts
      .filter((account) => account.active && account.systemKey !== "BANK")
      .map((account) => ({ value: String(account.id), label: `${account.code} · ${account.name}` })),
  ];
}

/** An unmatched line: match it to an entry, post it as a new one, or ignore it. */
function UnmatchedSheet({
  line,
  accounts,
  owner,
  onClose,
  onDone,
}: {
  line: BankLineView;
  accounts: Account[];
  owner: boolean;
  onClose: () => void;
  onDone: () => void;
}) {
  const candidates = useLoad(() => api.admin.accounts.bank.candidates(line.id), [line.id]);
  const [chosen, setChosen] = useState<number | null>(null);
  const [differenceAccount, setDifferenceAccount] = useState("");
  const [postAccount, setPostAccount] = useState("");
  const [narration, setNarration] = useState("");
  const [reason, setReason] = useState("");
  const action = useAction();

  const moneyIn = line.creditRupees > 0;
  const lineAmount = Math.abs(line.creditRupees - line.debitRupees);
  const candidate = candidates.value?.find((entry) => entry.journalEntryId === chosen) ?? null;
  const difference = candidate ? candidate.amountRupees - lineAmount : 0;
  const differs = candidate !== null && Math.abs(difference) >= 0.005;
  const differenceKey = moneyIn ? "EXPENSE_GATEWAY_FEES" : "EXPENSE_BANK_CHARGES";
  const defaultDifference = String(accounts.find((account) => account.systemKey === differenceKey)?.id ?? "");
  const differenceValue = differenceAccount || defaultDifference;
  const options = accountOptions(accounts);

  async function match() {
    if (chosen === null) return;
    const matched = await action.run(() =>
      api.admin.accounts.bank.match(line.id, {
        journalEntryId: chosen,
        differenceAccountId: differs && differenceValue ? Number(differenceValue) : null,
      }),
    );
    if (matched) onDone();
  }

  async function post() {
    if (!postAccount) return;
    const posted = await action.run(() =>
      api.admin.accounts.bank.post(line.id, {
        accountId: Number(postAccount),
        narration: narration.trim() || null,
      }),
    );
    if (posted) onDone();
  }

  async function ignore() {
    const ignored = await action.run(() => api.admin.accounts.bank.ignore(line.id, reason.trim()));
    if (ignored) onDone();
  }

  return (
    <Sheet open title={line.description} onClose={onClose}>
      <p className="text-sm text-ink-soft">
        {formatDate(line.date)} · {lineMoney(line)}
        {line.reference ? ` · ${line.reference}` : ""}
      </p>

      <SectionLabel>Match to an entry</SectionLabel>
      <ErrorNote>{candidates.error}</ErrorNote>
      {candidates.loading ? (
        <p className="text-sm text-ink-soft">Looking for entries…</p>
      ) : !candidates.value || candidates.value.length === 0 ? (
        <p className="text-sm text-ink-soft">No candidates within 15 days.</p>
      ) : (
        <fieldset className="space-y-2">
          <legend className="sr-only">Match candidate</legend>
          {candidates.value.map((entry) => (
            <label
              key={entry.journalEntryId}
              className={`flex cursor-pointer items-center gap-3 rounded-xl border p-3 ${
                chosen === entry.journalEntryId ? "border-accent/60 bg-accent-soft" : "border-line"
              }`}
            >
              <input
                type="radio"
                name={`candidate-${line.id}`}
                checked={chosen === entry.journalEntryId}
                onChange={() => setChosen(entry.journalEntryId)}
              />
              <span className="min-w-0 flex-1 text-sm">
                <span className="block font-medium">{entry.number}</span>
                <span className="block text-ink-soft">
                  {formatDate(entry.date)} · {entry.narration}
                </span>
              </span>
              <span className="shrink-0 tabular-nums">{rupees(entry.amountRupees)}</span>
            </label>
          ))}
        </fieldset>
      )}

      {differs && (
        <div className="mt-3 space-y-2">
          <p className="text-sm text-ink-soft">Difference {rupees(Math.abs(difference))}</p>
          <SelectField
            label="Difference account"
            options={options}
            value={differenceValue}
            onChange={setDifferenceAccount}
          />
        </div>
      )}

      {owner && (
        <button
          type="button"
          className={`${buttonPrimary} mt-4`}
          disabled={action.busy || chosen === null || (differs && !differenceValue)}
          onClick={match}
        >
          {action.busy ? "Saving…" : "Match"}
        </button>
      )}

      {owner && (
        <>
          <SectionLabel>Post as</SectionLabel>
          <div className="space-y-3">
            <SelectField label="Account" options={options} value={postAccount} onChange={setPostAccount} />
            <TextField
              label="Narration"
              placeholder={line.description}
              value={narration}
              onChange={(e) => setNarration(e.target.value)}
            />
            <button
              type="button"
              className={buttonSecondary}
              disabled={action.busy || !postAccount}
              onClick={post}
            >
              Post
            </button>
          </div>

          <SectionLabel>Ignore</SectionLabel>
          <div className="space-y-3">
            <TextField label="Reason" value={reason} onChange={(e) => setReason(e.target.value)} />
            <button type="button" className={buttonSecondary} disabled={action.busy} onClick={ignore}>
              Ignore line
            </button>
          </div>
        </>
      )}

      <ErrorNote>{action.error}</ErrorNote>
    </Sheet>
  );
}

/** A matched, posted or ignored line: its entry (or reason) and Undo. */
function SettledSheet({
  line,
  owner,
  onClose,
  onDone,
}: {
  line: BankLineView;
  owner: boolean;
  onClose: () => void;
  onDone: () => void;
}) {
  const action = useAction();

  async function undo() {
    const undone = await action.run(() => api.admin.accounts.bank.undo(line.id));
    if (undone) onDone();
  }

  return (
    <Sheet open title={line.description} onClose={onClose}>
      <p className="text-sm text-ink-soft">
        {formatDate(line.date)} · {lineMoney(line)} · {STATUS_LABELS[line.status] ?? line.status}
        {line.reference ? ` · ${line.reference}` : ""}
      </p>
      {line.status === "IGNORED" && (
        <p className="mt-3">{line.ignoreReason ? `Reason: ${line.ignoreReason}` : "No reason recorded."}</p>
      )}
      {line.journalNumber && line.journalEntryId && (
        <p className="mt-3">
          <Link
            href={`/money/accounts/entry/?id=${line.journalEntryId}`}
            className="font-medium text-accent-ink underline"
          >
            {line.journalNumber}
          </Link>
        </p>
      )}
      {owner && (
        <button type="button" className={`${buttonSecondary} mt-4`} disabled={action.busy} onClick={undo}>
          {action.busy ? "Undoing…" : "Undo"}
        </button>
      )}
      <ErrorNote>{action.error}</ErrorNote>
    </Sheet>
  );
}

function StatementView({ id }: { id: number }) {
  const owner = useIsOwner();
  const [status, setStatus] = useState("");
  const detail = useLoad(() => api.admin.accounts.bank.statement(id, status || undefined), [id, status]);
  const chart = useLoad(() => api.admin.accounts.chart(), []);
  const [openId, setOpenId] = useState<number | null>(null);
  const v = detail.value;

  if (!v) {
    return (
      <section className="mx-auto max-w-3xl">
        <PageHeader title="Bank statement" backHref="/money/accounts/bank/" />
        <LoadState loading={detail.loading} error={detail.error} />
      </section>
    );
  }

  const statement = v.statement;
  const line = openId === null ? null : (v.lines.find((row) => row.id === openId) ?? null);

  function done() {
    setOpenId(null);
    detail.reload();
  }

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader
        title={statement.fileName}
        backHref="/money/accounts/bank/"
        badge={`${formatDate(statement.periodFrom)} – ${formatDate(statement.periodTo)}`}
        actions={
          <Link href={`/money/accounts/bank/report/?id=${id}`} className={buttonSecondary}>
            Reconciliation report
          </Link>
        }
      />
      <p className="mb-4 text-sm text-ink-soft">
        Opening {statement.openingRupees === null ? "—" : rupees(statement.openingRupees)} · Closing{" "}
        {statement.closingRupees === null ? "—" : rupees(statement.closingRupees)}
      </p>
      <div className="mb-4">
        <ChipGroup label="Line status" options={LINE_STATUSES} value={status} onChange={setStatus} />
      </div>
      <ErrorNote>{detail.error ?? chart.error}</ErrorNote>

      {v.lines.length === 0 ? (
        <EmptyState icon={DocumentIcon} title="No lines">
          No lines with this status.
        </EmptyState>
      ) : (
        <div className="overflow-x-auto rounded-2xl border border-line bg-panel">
          <div className="min-w-[34rem] divide-y divide-line">
            <div className="grid grid-cols-[4rem_1fr_5.5rem_5.5rem_5rem] gap-3 bg-panel-2 px-4 py-2 text-xs uppercase tracking-[0.1em] text-ink-faint">
              <span>Date</span>
              <span>Description</span>
              <span className="text-right">In</span>
              <span className="text-right">Out</span>
              <span className="text-right">Status</span>
            </div>
            {v.lines.map((row) => (
              <button
                key={row.id}
                type="button"
                onClick={() => setOpenId(row.id)}
                className="grid w-full grid-cols-[4rem_1fr_5.5rem_5.5rem_5rem] items-center gap-3 px-4 py-3 text-left hover:bg-panel-2"
              >
                <span className="text-sm text-ink-soft">{formatDate(row.date)}</span>
                <span className="min-w-0">
                  <span className="block font-medium">{row.description}</span>
                  {row.reference && <span className="mt-0.5 block text-xs text-ink-faint">{row.reference}</span>}
                </span>
                <span className="text-right tabular-nums">
                  {row.creditRupees > 0 ? rupees(row.creditRupees) : ""}
                </span>
                <span className="text-right tabular-nums">
                  {row.debitRupees > 0 ? rupees(row.debitRupees) : ""}
                </span>
                <span className="text-right">
                  <Pill tone={STATUS_TONES[row.status] ?? "default"}>
                    {STATUS_LABELS[row.status] ?? row.status}
                  </Pill>
                </span>
              </button>
            ))}
          </div>
        </div>
      )}

      {line &&
        (line.status === "UNMATCHED" ? (
          <UnmatchedSheet
            key={line.id}
            line={line}
            accounts={chart.value ?? []}
            owner={owner}
            onClose={() => setOpenId(null)}
            onDone={done}
          />
        ) : (
          <SettledSheet key={line.id} line={line} owner={owner} onClose={() => setOpenId(null)} onDone={done} />
        ))}
    </section>
  );
}

function Route() {
  const id = idParam(useSearchParams().get("id"));
  return id ? <StatementView key={id} id={id} /> : <ErrorNote>No statement selected.</ErrorNote>;
}

/** One imported statement: its lines, filtered by status, with the actions each line allows. */
export default function StatementPage() {
  return (
    <Suspense fallback={<p className="text-ink-soft">Loading…</p>}>
      <Route />
    </Suspense>
  );
}
