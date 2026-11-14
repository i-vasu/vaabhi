import type { ColumnMapping } from "@vaabhi/api-client";

/**
 * Purchases arithmetic shared by the supplier and bill pages: the GST split of a line, the totals of a bill and how a
 * payment spreads over a supplier's bills. Everything stays in whole paise, like the backend, so rupees never drift.
 */

/** The GST split of one line, as rupee strings with two decimals. */
export type LineTax = { cgst: string; sgst: string; igst: string; total: string };

/** One bill line as the form holds it: the strings a person typed. */
export type BillLineAmount = { taxable: string; rate: string };

/** A bill's totals, as rupee strings with two decimals. */
export type BillTotals = {
  taxable: string;
  cgst: string;
  sgst: string;
  igst: string;
  tax: string;
  total: string;
};

/** A bill a payment can be spread over: what it still owes, and when it was due. */
export type AllocationBill = {
  id: number;
  billDate: string;
  dueDate: string | null;
  outstandingRupees: number;
};

/** One bill's share of a payment. */
export type BillAllocation = { billId: number; amountRupees: string };

/** The kinds of line a supplier bill can carry. */
export const LINE_KINDS: { value: "EXPENSE" | "STOCK" | "ASSET"; label: string }[] = [
  { value: "EXPENSE", label: "Expense" },
  { value: "STOCK", label: "Stock" },
  { value: "ASSET", label: "Asset" },
];

/** The counter modes a supplier payment can use (the books take no Razorpay or COD here). */
export const PAYMENT_MODES = ["CASH", "UPI", "CARD", "BANK"] as const;

export const PAYMENT_MODE_OPTIONS: { value: string; label: string }[] = [
  { value: "CASH", label: "Cash" },
  { value: "UPI", label: "UPI" },
  { value: "CARD", label: "Card" },
  { value: "BANK", label: "Bank" },
];

/** Whole paise of a typed rupee amount; "" is 0 and null means it is not a rupee amount. */
function toPaise(text: string): number | null {
  const cleaned = text.replace(/[₹,\s]/g, "");
  if (cleaned === "") return 0;
  if (!/^\d+(\.\d{1,2})?$/.test(cleaned)) return null;
  return Math.round(Number(cleaned) * 100);
}

/** Paise → a rupee string with two decimals ("250" → "2.50"). */
export function formatPaise(paise: number): string {
  return (paise / 100).toFixed(2);
}

/**
 * GST on one line. An intra-state supply splits it into CGST and SGST, SGST taking the stray paisa; an inter-state
 * supply puts it all in IGST. Rounds HALF_UP to paise, as `BillMath` does.
 */
export function lineTax(taxable: string, rate: string, interState: boolean): LineTax {
  const taxablePaise = toPaise(taxable) ?? 0;
  const percent = Number(rate);
  const tax = Math.round((taxablePaise * (Number.isFinite(percent) ? percent : 0)) / 100);
  const total = formatPaise(taxablePaise + tax);
  if (interState) {
    return { cgst: formatPaise(0), sgst: formatPaise(0), igst: formatPaise(tax), total };
  }
  const cgst = Math.floor(tax / 2);
  return { cgst: formatPaise(cgst), sgst: formatPaise(tax - cgst), igst: formatPaise(0), total };
}

/** The bill's taxable, tax split and total across its lines, computed the way the API will. */
export function billTotals(lines: BillLineAmount[], interState: boolean): BillTotals {
  let taxable = 0;
  let cgst = 0;
  let sgst = 0;
  let igst = 0;
  for (const line of lines) {
    const split = lineTax(line.taxable, line.rate, interState);
    taxable += toPaise(line.taxable) ?? 0;
    cgst += toPaise(split.cgst) ?? 0;
    sgst += toPaise(split.sgst) ?? 0;
    igst += toPaise(split.igst) ?? 0;
  }
  const tax = cgst + sgst + igst;
  return {
    taxable: formatPaise(taxable),
    cgst: formatPaise(cgst),
    sgst: formatPaise(sgst),
    igst: formatPaise(igst),
    tax: formatPaise(tax),
    total: formatPaise(taxable + tax),
  };
}

/** How a payment should spread over bills when nobody picks: oldest due first, each up to what it owes. */
export function defaultAllocation(bills: AllocationBill[], amount: string): BillAllocation[] {
  const remainingStart = toPaise(amount);
  if (remainingStart === null || remainingStart <= 0) return [];
  const ordered = [...bills].sort((a, b) => {
    const dueA = a.dueDate ?? a.billDate;
    const dueB = b.dueDate ?? b.billDate;
    if (dueA !== dueB) return dueA < dueB ? -1 : 1;
    if (a.billDate !== b.billDate) return a.billDate < b.billDate ? -1 : 1;
    return a.id - b.id;
  });
  const allocations: BillAllocation[] = [];
  let remaining = remainingStart;
  for (const bill of ordered) {
    if (remaining <= 0) break;
    const outstanding = Math.round(bill.outstandingRupees * 100);
    if (outstanding <= 0) continue;
    const take = Math.min(outstanding, remaining);
    allocations.push({ billId: bill.id, amountRupees: formatPaise(take) });
    remaining -= take;
  }
  return allocations;
}

/** The columns of a Shiprocket freight invoice report, in the order the mapping sheet shows them. */
export const SHIPROCKET_FIELDS: { key: string; label: string }[] = [
  { key: "invoiceNo", label: "Invoice number" },
  { key: "invoiceDate", label: "Invoice date" },
  { key: "taxable", label: "Freight (taxable)" },
  { key: "gst", label: "GST" },
  { key: "cgst", label: "CGST" },
  { key: "sgst", label: "SGST" },
  { key: "igst", label: "IGST" },
  { key: "total", label: "Total" },
];

const REQUIRED_SHIPROCKET = ["invoiceNo", "invoiceDate", "taxable"];

/** What is wrong with the Shiprocket mapping, for the import button; null when it can be submitted. */
export function shiprocketMappingProblem(mapping: ColumnMapping): string | null {
  if (REQUIRED_SHIPROCKET.some((key) => mapping.columns[key] === undefined)) {
    return "Map the Invoice number, Invoice date and Freight (taxable) columns.";
  }
  return null;
}
