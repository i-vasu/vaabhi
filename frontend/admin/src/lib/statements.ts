import type { ScheduleLine } from "@vaabhi/api-client";
import type { AccountType } from "@/lib/accounts";

/** The Schedule III lines of the balance sheet and profit and loss, in statement order. */
export const SCHEDULE_LINES: { value: ScheduleLine; label: string; side: AccountType }[] = [
  { value: "REVENUE_FROM_OPERATIONS", label: "Revenue from operations", side: "INCOME" },
  { value: "OTHER_INCOME", label: "Other income", side: "INCOME" },
  { value: "COST_OF_MATERIALS", label: "Cost of materials consumed", side: "EXPENSE" },
  { value: "PURCHASES_STOCK_IN_TRADE", label: "Purchases of stock-in-trade", side: "EXPENSE" },
  { value: "CHANGE_IN_INVENTORIES", label: "Changes in inventories", side: "EXPENSE" },
  { value: "EMPLOYEE_BENEFITS", label: "Employee benefits expense", side: "EXPENSE" },
  { value: "FINANCE_COSTS", label: "Finance costs", side: "EXPENSE" },
  { value: "DEPRECIATION", label: "Depreciation and amortisation", side: "EXPENSE" },
  { value: "OTHER_EXPENSES", label: "Other expenses", side: "EXPENSE" },
  { value: "SHARE_CAPITAL", label: "Share capital", side: "EQUITY" },
  { value: "RESERVES_SURPLUS", label: "Reserves and surplus", side: "EQUITY" },
  { value: "LONG_TERM_BORROWINGS", label: "Long-term borrowings", side: "LIABILITY" },
  { value: "SHORT_TERM_BORROWINGS", label: "Short-term borrowings", side: "LIABILITY" },
  { value: "TRADE_PAYABLES", label: "Trade payables", side: "LIABILITY" },
  { value: "OTHER_CURRENT_LIABILITIES", label: "Other current liabilities", side: "LIABILITY" },
  { value: "PPE", label: "Property, plant and equipment", side: "ASSET" },
  { value: "INVENTORIES", label: "Inventories", side: "ASSET" },
  { value: "TRADE_RECEIVABLES", label: "Trade receivables", side: "ASSET" },
  { value: "CASH_AND_BANK", label: "Cash and cash equivalents", side: "ASSET" },
  { value: "OTHER_CURRENT_ASSETS", label: "Other current assets", side: "ASSET" },
];

/** The Schedule III lines that belong on a statement side, in order. */
export function linesFor(type: AccountType): { value: ScheduleLine; label: string; side: AccountType }[] {
  return SCHEDULE_LINES.filter((line) => line.side === type);
}

/** The line an account of this type starts on. */
export function defaultLineFor(type: AccountType): ScheduleLine {
  switch (type) {
    case "INCOME":
      return "OTHER_INCOME";
    case "EXPENSE":
      return "OTHER_EXPENSES";
    case "ASSET":
      return "OTHER_CURRENT_ASSETS";
    case "LIABILITY":
      return "OTHER_CURRENT_LIABILITIES";
    case "EQUITY":
      return "RESERVES_SURPLUS";
  }
}

/** The label shown for a Schedule III line. */
export function scheduleLineLabel(value: ScheduleLine): string {
  return SCHEDULE_LINES.find((line) => line.value === value)?.label ?? value;
}
