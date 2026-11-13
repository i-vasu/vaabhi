"use client";

import Link from "next/link";
import { useState } from "react";
import type { Expense, ExpenseCategory, ExpenseForm, PaymentMode } from "@vaabhi/api-client";
import { MonthPicker } from "@/components/money";
import { PlusIcon, ReceiptIcon, TrashIcon } from "@/components/icons";
import { ErrorNote } from "@/components/tailoring";
import {
  EmptyState,
  ListCard,
  ListRow,
  LoadState,
  PageHeader,
  SectionLabel,
  SelectField,
  Sheet,
  StatTile,
  TextField,
  buttonPrimary,
  buttonSecondary,
} from "@/components/ui";
import { api } from "@/lib/api";
import { EXPENSE_CATEGORIES, categoryLabel, monthOf } from "@/lib/money";
import { parseRupees, rupees } from "@/lib/settings";
import { PAYMENT_MODES, formatDate, today, useAction, useLoad } from "@/lib/tailoring";

export default function ExpensesPage() {
  const [month, setMonth] = useState(() => monthOf(today()));
  const [category, setCategory] = useState<ExpenseCategory | "">("");
  const [editing, setEditing] = useState<{ expense: Expense | null } | null>(null);
  const expenses = useLoad(
    () => api.admin.money.expenses({ from: month.from, to: month.to, category: category || undefined }),
    [month.from, category],
  );
  const e = expenses.value;

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader
        title="Expenses"
        backHref="/money/"
        actions={
          <button type="button" className={buttonPrimary} onClick={() => setEditing({ expense: null })}>
            <PlusIcon size={18} /> Add expense
          </button>
        }
      />
      <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
        <MonthPicker value={month} onChange={setMonth} />
        <SelectField
          className="min-w-48"
          label="Category"
          options={[{ value: "" as const, label: "All categories" }, ...EXPENSE_CATEGORIES]}
          value={category}
          onChange={setCategory}
        />
      </div>
      <ErrorNote>{expenses.error}</ErrorNote>
      {!e ? (
        <LoadState loading={expenses.loading} error={null} />
      ) : (
        <>
          <div className="mb-2 grid grid-cols-2 gap-3 sm:grid-cols-3">
            <StatTile label="Spent" value={rupees(e.totalRupees)} />
            {e.byCategory.slice(0, 2).map((c) => (
              <StatTile key={c.category} label={categoryLabel(c.category)} value={rupees(c.amountRupees)} />
            ))}
          </div>
          <SectionLabel>{e.items.length} expenses</SectionLabel>
          {e.items.length === 0 ? (
            <EmptyState icon={ReceiptIcon} title="No expenses this month">
              Add rent, salaries, material and bills to see what the shop really earns.
            </EmptyState>
          ) : (
            <ListCard>
              {e.items.map((expense) =>
                expense.fromPayroll ? (
                  // Paid from a payslip: changed from Payroll, so it is a link there, not an editable row.
                  <Link key={expense.id} href="/payroll/" className="block hover:bg-panel-2">
                    <ListRow
                      title={expense.description}
                      subtitle={`${formatDate(expense.spentOn)} · ${categoryLabel(expense.category)} · from Payroll`}
                      trailing={<span className="font-medium text-ink">{rupees(expense.amountRupees)}</span>}
                    />
                  </Link>
                ) : (
                  <button
                    key={expense.id}
                    type="button"
                    className="block w-full text-left hover:bg-panel-2"
                    onClick={() => setEditing({ expense })}
                  >
                    <ListRow
                      title={expense.description}
                      subtitle={`${formatDate(expense.spentOn)} · ${categoryLabel(expense.category)}${expense.paidTo ? ` · ${expense.paidTo}` : ""}`}
                      trailing={<span className="font-medium text-ink">{rupees(expense.amountRupees)}</span>}
                    />
                  </button>
                ),
              )}
            </ListCard>
          )}
        </>
      )}
      {editing && (
        <ExpenseSheet
          expense={editing.expense}
          defaultDate={today() <= month.to && today() >= month.from ? today() : month.from}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            expenses.reload();
          }}
        />
      )}
    </section>
  );
}

function ExpenseSheet({
  expense,
  defaultDate,
  onClose,
  onSaved,
}: {
  expense: Expense | null;
  defaultDate: string;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [spentOn, setSpentOn] = useState(expense?.spentOn ?? defaultDate);
  const [category, setCategory] = useState<ExpenseCategory>(expense?.category ?? "MATERIAL");
  const [description, setDescription] = useState(expense?.description ?? "");
  const [amount, setAmount] = useState(expense ? String(expense.amountRupees) : "");
  const [mode, setMode] = useState<PaymentMode>(expense?.mode ?? "CASH");
  const [paidTo, setPaidTo] = useState(expense?.paidTo ?? "");
  const action = useAction();

  async function save() {
    const amountRupees = parseRupees(amount);
    if (!description.trim()) return action.setError("Say what it was for.");
    if (!amountRupees) return action.setError("Enter the amount.");
    const form: ExpenseForm = {
      spentOn,
      category,
      description: description.trim(),
      amountRupees,
      mode,
      paidTo: paidTo.trim() || null,
    };
    const saved = await action.run(() =>
      expense ? api.admin.money.updateExpense(expense.id, form) : api.admin.money.createExpense(form),
    );
    if (saved) onSaved();
  }

  return (
    <Sheet open title={expense ? "Edit expense" : "Add expense"} onClose={onClose}>
      <div className="flex flex-col gap-4">
        <TextField
          label="What for"
          maxLength={200}
          placeholder="Thread and buttons"
          value={description}
          onChange={(event) => setDescription(event.target.value)}
        />
        <div className="grid grid-cols-2 gap-3">
          <TextField
            label="Amount (₹)"
            inputMode="decimal"
            value={amount}
            onChange={(event) => setAmount(event.target.value)}
          />
          <TextField label="Date" type="date" value={spentOn} onChange={(event) => setSpentOn(event.target.value)} />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <SelectField label="Category" options={EXPENSE_CATEGORIES} value={category} onChange={setCategory} />
          <SelectField label="Paid by" options={PAYMENT_MODES} value={mode} onChange={setMode} />
        </div>
        <TextField label="Paid to" maxLength={120} value={paidTo} onChange={(event) => setPaidTo(event.target.value)} />
        <ErrorNote>{action.error}</ErrorNote>
        <div className="flex flex-wrap gap-2">
          <button type="button" className={buttonPrimary} disabled={action.busy} onClick={() => void save()}>
            {action.busy ? "Saving…" : "Save expense"}
          </button>
          {expense && (
            <button
              type="button"
              className={`${buttonSecondary} text-bad`}
              disabled={action.busy}
              onClick={async () => {
                const done = await action.run(() => api.admin.money.deleteExpense(expense.id).then(() => true));
                if (done) onSaved();
              }}
            >
              <TrashIcon size={18} /> Remove
            </button>
          )}
        </div>
      </div>
    </Sheet>
  );
}
