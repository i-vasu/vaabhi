import type { ExpenseCategory, LedgerKind } from "@vaabhi/api-client";

export const EXPENSE_CATEGORIES: { value: ExpenseCategory; label: string }[] = [
  { value: "RENT", label: "Rent" },
  { value: "SALARY", label: "Salary" },
  { value: "MATERIAL", label: "Material" },
  { value: "UTILITIES", label: "Bills & utilities" },
  { value: "TRANSPORT", label: "Transport" },
  { value: "MAINTENANCE", label: "Repairs & upkeep" },
  { value: "MARKETING", label: "Marketing" },
  { value: "OTHER", label: "Other" },
];

export const categoryLabel = (category: ExpenseCategory) =>
  EXPENSE_CATEGORIES.find((c) => c.value === category)?.label ?? category;

export const LEDGER_KINDS: { value: LedgerKind; label: string }[] = [
  { value: "ORDER_PAYMENT", label: "Order payment" },
  { value: "ALTERATION_PAYMENT", label: "Alteration" },
  { value: "REFUND", label: "Refund" },
  { value: "EXPENSE", label: "Expense" },
];

export const kindLabel = (kind: LedgerKind) => LEDGER_KINDS.find((k) => k.value === kind)?.label ?? kind;

export type Month = { from: string; to: string };

/** The calendar month containing `isoDate` (yyyy-mm-dd), as first and last day. */
export function monthOf(isoDate: string): Month {
  const [year, month] = isoDate.split("-").map(Number) as [number, number];
  const last = new Date(Date.UTC(year, month, 0)).getUTCDate();
  const mm = String(month).padStart(2, "0");
  return { from: `${year}-${mm}-01`, to: `${year}-${mm}-${String(last).padStart(2, "0")}` };
}

/** The month `count` months after (negative: before) the one starting at `from`. */
export function shiftMonth(month: Month, count: number): Month {
  const [year, m] = month.from.split("-").map(Number) as [number, number];
  const date = new Date(Date.UTC(year, m - 1 + count, 1));
  return monthOf(date.toISOString().slice(0, 10));
}

const monthName = new Intl.DateTimeFormat("en-IN", { month: "long", year: "numeric", timeZone: "UTC" });

/** "November 2026". */
export function monthLabel(month: Month): string {
  return monthName.format(new Date(`${month.from}T00:00:00Z`));
}
