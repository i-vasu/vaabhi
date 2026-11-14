"use client";

import Link from "next/link";
import { useState } from "react";
import type { Account, AccountForm, ScheduleLine } from "@vaabhi/api-client";
import { BankIcon } from "@/components/icons";
import { ErrorNote, Pill } from "@/components/tailoring";
import {
  EmptyState,
  ListCard,
  LoadState,
  PageHeader,
  SectionLabel,
  SelectField,
  Sheet,
  TextField,
  buttonPrimary,
  buttonSecondary,
} from "@/components/ui";
import { ACCOUNT_TYPES, useIsOwner, type AccountType } from "@/lib/accounts";
import { api } from "@/lib/api";
import { rupees } from "@/lib/settings";
import { defaultLineFor, linesFor, scheduleLineLabel } from "@/lib/statements";
import { useAction, useLoad } from "@/lib/tailoring";

type Editing = { account: Account | null };

function AccountSheet({ editing, onClose, onSaved }: { editing: Editing; onClose: () => void; onSaved: () => void }) {
  const account = editing.account;
  const [code, setCode] = useState(account?.code ?? "");
  const [name, setName] = useState(account?.name ?? "");
  const [type, setType] = useState<AccountType>(account?.type ?? "ASSET");
  const [scheduleLine, setScheduleLine] = useState<ScheduleLine>(
    account?.scheduleLine ?? defaultLineFor(account?.type ?? "ASSET"),
  );
  const action = useAction();
  const title = account ? "Rename account" : "Add account";

  function changeType(next: AccountType) {
    setType(next);
    setScheduleLine(defaultLineFor(next));
  }

  async function save() {
    const form: AccountForm = {
      code: code.trim(),
      name: name.trim(),
      type,
      parentId: account?.parentId ?? null,
      active: account?.active ?? true,
      scheduleLine,
    };
    const saved = await action.run(() =>
      account ? api.admin.accounts.updateAccount(account.id, form) : api.admin.accounts.createAccount(form),
    );
    if (saved) {
      onSaved();
      onClose();
    }
  }

  return (
    <Sheet open title={title} onClose={onClose}>
      <div className="flex flex-col gap-4">
        <TextField label="Code" value={code} onChange={(e) => setCode(e.target.value)} />
        <TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} />
        {!account && <SelectField label="Type" options={ACCOUNT_TYPES} value={type} onChange={changeType} />}
        <SelectField
          label="Schedule III line"
          options={linesFor(type)}
          value={scheduleLine}
          onChange={setScheduleLine}
        />
        <ErrorNote>{action.error}</ErrorNote>
        <button
          type="button"
          className={buttonPrimary}
          disabled={action.busy || !code.trim() || !name.trim()}
          onClick={save}
        >
          {action.busy ? "Saving…" : "Save"}
        </button>
      </div>
    </Sheet>
  );
}

/** Every account by type with its balance; the owner can add, rename and turn accounts off. */
export default function ChartPage() {
  const owner = useIsOwner();
  const chart = useLoad(() => api.admin.accounts.chart(), []);
  const toggle = useAction();
  const [editing, setEditing] = useState<Editing | null>(null);
  const accounts = chart.value;

  async function setActive(account: Account, active: boolean) {
    const saved = await toggle.run(() =>
      api.admin.accounts.updateAccount(account.id, {
        code: account.code,
        name: account.name,
        type: account.type,
        parentId: account.parentId,
        active,
        scheduleLine: account.scheduleLine,
      }),
    );
    if (saved) chart.reload();
  }

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader
        title="Chart of accounts"
        backHref="/money/accounts/"
        actions={
          owner && (
            <button type="button" className={buttonSecondary} onClick={() => setEditing({ account: null })}>
              Add account
            </button>
          )
        }
      />
      <ErrorNote>{chart.error ?? toggle.error}</ErrorNote>
      {!accounts ? (
        <LoadState loading={chart.loading} error={null} />
      ) : accounts.length === 0 ? (
        <EmptyState icon={BankIcon} title="No accounts">
          The chart of accounts is empty.
        </EmptyState>
      ) : (
        ACCOUNT_TYPES.map((group) => {
          const rows = accounts.filter((a) => a.type === group.value);
          if (rows.length === 0) return null;
          return (
            <div key={group.value}>
              <SectionLabel>{group.label}</SectionLabel>
              <ListCard>
                {rows.map((account) => (
                  <div key={account.id} className="flex items-center gap-3 px-4 py-3">
                    <Link href={`/money/accounts/ledger/?id=${account.id}`} className="min-w-0 flex-1 hover:underline">
                      <span className="font-medium">
                        {account.code} · {account.name}
                      </span>{" "}
                      {!account.active && <Pill>Off</Pill>}
                      <span className="block text-sm text-ink-soft">{scheduleLineLabel(account.scheduleLine)}</span>
                    </Link>
                    <span className="tabular-nums text-sm">{rupees(account.balanceRupees)}</span>
                    {owner && (
                      <span className="flex gap-2 text-sm">
                        <button
                          type="button"
                          className="text-accent-ink underline"
                          aria-label={`Rename ${account.name}`}
                          onClick={() => setEditing({ account })}
                        >
                          Rename
                        </button>
                        <button
                          type="button"
                          className="text-accent-ink underline disabled:opacity-50"
                          aria-label={`${account.active ? "Turn off" : "Turn on"} ${account.name}`}
                          disabled={toggle.busy}
                          onClick={() => setActive(account, !account.active)}
                        >
                          {account.active ? "Turn off" : "Turn on"}
                        </button>
                      </span>
                    )}
                  </div>
                ))}
              </ListCard>
            </div>
          );
        })
      )}
      {editing && (
        <AccountSheet
          key={editing.account?.id ?? "new"}
          editing={editing}
          onClose={() => setEditing(null)}
          onSaved={chart.reload}
        />
      )}
    </section>
  );
}
