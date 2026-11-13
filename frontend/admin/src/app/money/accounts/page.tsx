"use client";

import Link from "next/link";
import { useState } from "react";
import type { Account, BooksSettings } from "@vaabhi/api-client";
import { ActivityIcon, BankIcon, DocumentIcon } from "@/components/icons";
import { ErrorNote } from "@/components/tailoring";
import {
  ListCard,
  ListRow,
  LoadState,
  PageHeader,
  SectionLabel,
  SelectField,
  TextField,
  buttonPrimary,
  buttonSecondary,
} from "@/components/ui";
import { api } from "@/lib/api";
import { MODE_ACCOUNTS, relativeTime, useIsOwner } from "@/lib/accounts";
import { rupees } from "@/lib/settings";
import { formatDate, today, useAction, useLoad } from "@/lib/tailoring";

function StartDate({ settings, owner, onSaved }: { settings: BooksSettings; owner: boolean; onSaved: () => void }) {
  const [date, setDate] = useState(today());
  const action = useAction();

  async function set() {
    const saved = await action.run(() =>
      api.admin.accounts.saveSettings({ startDate: date, modeAccounts: settings.modeAccounts }),
    );
    if (saved) onSaved();
  }

  return (
    <div className="mb-4 rounded-2xl border border-line bg-panel p-4">
      {settings.startDate ? (
        <p>Books start on {formatDate(settings.startDate)}.</p>
      ) : (
        <>
          <p className="font-medium">Books haven&apos;t started. Set the start date to begin.</p>
          {owner && (
            <div className="mt-3 flex flex-wrap items-end gap-3">
              <TextField label="Start date" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
              <button type="button" className={buttonPrimary} disabled={action.busy || !date} onClick={set}>
                Set
              </button>
            </div>
          )}
        </>
      )}
      <ErrorNote>{action.error}</ErrorNote>
    </div>
  );
}

function PaymentAccounts({
  settings,
  assets,
  onSaved,
}: {
  settings: BooksSettings;
  assets: Account[];
  onSaved: () => void;
}) {
  const [map, setMap] = useState<Record<string, string>>(() =>
    Object.fromEntries(MODE_ACCOUNTS.map((m) => [m.value, String(settings.modeAccounts[m.value] ?? "")])),
  );
  const [saved, setSaved] = useState(false);
  const action = useAction();
  const options = assets.map((a) => ({ value: String(a.id), label: `${a.code} · ${a.name}` }));

  async function save() {
    setSaved(false);
    const modeAccounts: Record<string, number> = {};
    for (const mode of MODE_ACCOUNTS) modeAccounts[mode.value] = Number(map[mode.value]);
    const result = await action.run(() =>
      api.admin.accounts.saveSettings({ startDate: settings.startDate, modeAccounts }),
    );
    if (result) {
      setSaved(true);
      onSaved();
    }
  }

  return (
    <div className="rounded-2xl border border-line bg-panel p-4">
      <p className="mb-3 text-sm text-ink-soft">Which account each way of taking money is posted to.</p>
      <div className="grid gap-3 sm:grid-cols-2">
        {MODE_ACCOUNTS.map((mode) => (
          <SelectField
            key={mode.value}
            label={mode.label}
            options={options}
            value={map[mode.value] ?? ""}
            onChange={(value) => setMap((current) => ({ ...current, [mode.value]: value }))}
          />
        ))}
      </div>
      <div className="mt-4 flex flex-wrap items-center gap-3">
        <button
          type="button"
          className={buttonPrimary}
          disabled={action.busy || MODE_ACCOUNTS.some((m) => !map[m.value])}
          onClick={save}
        >
          {action.busy ? "Saving…" : "Save payment accounts"}
        </button>
        <span role="status" className="text-sm text-ok">
          {saved ? "Saved" : ""}
        </span>
      </div>
      <ErrorNote>{action.error}</ErrorNote>
    </div>
  );
}

/** Books at a glance: when they start, whether they balance, what is stuck, and where to go next. */
export default function AccountsPage() {
  const owner = useIsOwner();
  const status = useLoad(() => api.admin.accounts.status(), []);
  const settings = useLoad(() => api.admin.accounts.settings(), []);
  const chart = useLoad(() => api.admin.accounts.chart(), []);
  const sweep = useAction();
  const s = status.value;
  const b = settings.value;

  function refresh() {
    status.reload();
    settings.reload();
  }

  async function postNow() {
    const result = await sweep.run(() => api.admin.accounts.sweep());
    if (result) refresh();
  }

  const assets = (chart.value ?? []).filter((a) => a.type === "ASSET" && a.active);

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader title="Accounts" backHref="/money/" />
      <ErrorNote>{status.error ?? settings.error ?? chart.error}</ErrorNote>
      {!b || !s ? (
        <LoadState loading={status.loading || settings.loading} error={null} />
      ) : (
        <>
          <StartDate settings={b} owner={owner} onSaved={refresh} />
          {b.startDate && (
            <div className="mb-4 rounded-2xl border border-line bg-panel p-4 text-sm">
              <p>
                {s.lastSweepAt ? `Posted ${relativeTime(s.lastSweepAt)}` : "Nothing posted yet"}
                {owner && (
                  <button type="button" className={`${buttonSecondary} ml-3`} disabled={sweep.busy} onClick={postNow}>
                    {sweep.busy ? "Posting…" : "Post now"}
                  </button>
                )}
              </p>
              <p className="mt-2">
                <Link href="/money/accounts/problems/" className="font-medium text-accent-ink underline">
                  {s.problemCount === 1 ? "1 problem" : `${s.problemCount} problems`}
                </Link>
              </p>
              <p className={`mt-2 font-medium ${Math.abs(s.trialBalanceDifference) < 0.005 ? "text-ok" : "text-bad"}`}>
                {Math.abs(s.trialBalanceDifference) < 0.005
                  ? "Balanced"
                  : `Out by ${rupees(Math.abs(s.trialBalanceDifference))}`}
              </p>
              <ErrorNote>{sweep.error}</ErrorNote>
            </div>
          )}
          <ListCard>
            <ListRow
              href="/money/accounts/journal/"
              icon={DocumentIcon}
              title="Journal"
              subtitle="Every entry, newest first"
            />
            <ListRow
              href="/money/accounts/trial-balance/"
              icon={ActivityIcon}
              title="Trial balance"
              subtitle="Debits and credits as at a date"
            />
            <ListRow
              href="/money/accounts/chart/"
              icon={BankIcon}
              title="Chart of accounts"
              subtitle="Accounts and their ledgers"
            />
            <ListRow
              href="/money/accounts/manual/"
              icon={DocumentIcon}
              title="Manual journal"
              subtitle="Post an entry by hand"
            />
            <ListRow
              href="/money/accounts/opening/"
              icon={BankIcon}
              title="Opening balances"
              subtitle="What the books start with"
            />
            <ListRow
              href="/money/accounts/periods/"
              icon={ActivityIcon}
              title="Periods"
              subtitle="Lock months that are closed"
            />
            <ListRow
              href="/money/accounts/bank/"
              icon={BankIcon}
              title="Bank reconciliation"
              subtitle="Import bank statements and COD remittances, sync Razorpay, match to the books"
            />
          </ListCard>
          {owner && (
            <>
              <SectionLabel>Payment accounts</SectionLabel>
              <PaymentAccounts key={assets.length} settings={b} assets={assets} onSaved={refresh} />
            </>
          )}
        </>
      )}
    </section>
  );
}
