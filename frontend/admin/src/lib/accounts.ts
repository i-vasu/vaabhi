import type { Account, JournalSourceLink, LedgerStatement } from "@vaabhi/api-client";
import { useStaff } from "@/components/app-shell";
import { getStaff } from "@/lib/auth";
import { parseRupees, rupees } from "@/lib/settings";

export type AccountType = Account["type"];

export const ACCOUNT_TYPES: { value: AccountType; label: string }[] = [
  { value: "ASSET", label: "Assets" },
  { value: "LIABILITY", label: "Liabilities" },
  { value: "EQUITY", label: "Equity" },
  { value: "INCOME", label: "Income" },
  { value: "EXPENSE", label: "Expenses" },
];

/** The ways money is taken, each posted to one asset account. */
export const MODE_ACCOUNTS: { value: string; label: string }[] = [
  { value: "CASH", label: "Cash" },
  { value: "UPI", label: "UPI" },
  { value: "CARD", label: "Card" },
  { value: "BANK", label: "Bank transfer" },
  { value: "RAZORPAY", label: "Razorpay" },
  { value: "COD", label: "Cash on delivery" },
];

/** Writes under Accounts are for the owner; with nobody known the server decides. */
export function useIsOwner(): boolean {
  const staff = useStaff() ?? getStaff();
  return !staff || staff.role === "OWNER";
}

/** Where a journal entry came from, so it can be opened; null when there is no page for it. */
export function sourceHref(link: JournalSourceLink): string | null {
  switch (link.type) {
    case "INVOICE":
      return `/money/invoices/view/?id=${link.id}`;
    case "CREDIT_NOTE":
      return `/money/credit-notes/view/?id=${link.id}`;
    case "ORDER_PAYMENT":
      return link.orderId == null ? null : `/orders/view/?id=${link.orderId}`;
    case "ALTERATION_PAYMENT":
      return link.alterationId == null ? null : `/alterations/view/?id=${link.alterationId}`;
    case "WEB_ORDER":
    case "WEB_RETURN":
      return link.orderNumber ? `/online-orders/view/?number=${encodeURIComponent(link.orderNumber)}` : null;
    case "EXPENSE":
      return "/money/expenses/";
    default:
      return null;
  }
}

export function sourceLabel(type: string): string {
  switch (type) {
    case "INVOICE":
      return "Invoice";
    case "CREDIT_NOTE":
      return "Credit note";
    case "ORDER_PAYMENT":
      return "Order payment";
    case "ALTERATION_PAYMENT":
      return "Alteration payment";
    case "WEB_ORDER":
      return "Web order";
    case "WEB_RETURN":
      return "Web return";
    case "EXPENSE":
      return "Expense";
    case "MANUAL":
      return "Manual entry";
    case "OPENING":
      return "Opening balances";
    default:
      return type;
  }
}

/** "just now", "2 minutes ago", "3 hours ago", "5 days ago". */
export function relativeTime(iso: string, now: Date = new Date()): string {
  const seconds = Math.max(0, Math.round((now.getTime() - new Date(iso).getTime()) / 1000));
  if (seconds < 60) return "just now";
  const units: [number, string][] = [
    [86_400, "day"],
    [3_600, "hour"],
    [60, "minute"],
  ];
  for (const [size, name] of units) {
    if (seconds >= size) {
      const n = Math.floor(seconds / size);
      return `${n} ${name}${n === 1 ? "" : "s"} ago`;
    }
  }
  return "just now";
}

export function csvCell(value: string | number): string {
  let text = String(value);
  // A text cell that starts like a formula would be run by a spreadsheet; numbers (even negative) are safe.
  if (typeof value === "string" && /^[=+\-@]/.test(text)) text = `'${text}`;
  return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

/** Whole paise of what a person typed ("" = 0), or null when it is not a rupee amount; no float drift. */
export function toPaise(text: string): number | null {
  if (text.trim() === "") return 0;
  const amount = parseRupees(text);
  return amount === null ? null : Math.round(amount * 100);
}

/** Paise → "1,250.50"-style rupees for display. */
export function paiseLabel(paise: number): string {
  return rupees(paise / 100);
}

/** The financial year (1 April – 31 March) a date falls in, as its first and last day. */
export function fyRange(isoDate: string): { from: string; to: string } {
  const year = Number(isoDate.slice(0, 4));
  const startYear = Number(isoDate.slice(5, 7)) >= 4 ? year : year - 1;
  return { from: `${startYear}-04-01`, to: `${startYear + 1}-03-31` };
}

/** "2026-04-01" → "FY 2026–27". */
export function fyLabel(fromDate: string): string {
  const startYear = Number(fromDate.slice(0, 4));
  return `FY ${startYear}–${String((startYear + 1) % 100).padStart(2, "0")}`;
}

/** "2026-04-01" → "April 2026". */
export function monthTitle(isoDate: string): string {
  return new Date(`${isoDate.slice(0, 7)}-01T00:00:00Z`).toLocaleDateString("en-IN", {
    month: "long",
    year: "numeric",
    timeZone: "UTC",
  });
}

/** An account's statement as CSV: opening, every line with its running balance, closing. */
export function ledgerCsv(statement: LedgerStatement): string {
  const rows: (string | number)[][] = [
    ["Date", "Entry", "Narration", "Party", "Memo", "Debit", "Credit", "Balance"],
    [statement.from, "", "Opening balance", "", "", "", "", statement.openingRupees],
    ...statement.lines.map((line) => [
      line.date,
      line.number,
      line.narration,
      line.partyName ?? "",
      line.memo ?? "",
      line.debitRupees || "",
      line.creditRupees || "",
      line.balanceRupees,
    ]),
    [
      statement.to,
      "",
      "Closing balance",
      "",
      "",
      statement.totalDebitRupees,
      statement.totalCreditRupees,
      statement.closingRupees,
    ],
  ];
  return rows.map((row) => row.map(csvCell).join(",")).join("\r\n");
}

/** Downloads text as a file, via an object URL. */
export function downloadText(filename: string, text: string, type = "text/csv;charset=utf-8") {
  const url = URL.createObjectURL(new Blob([text], { type }));
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 0);
}
