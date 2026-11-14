import type { ColumnMapping } from "@vaabhi/api-client";

/** The statement columns the importer can be told about, in the order the mapping sheet shows them. */
export const BANK_FIELDS: { key: string; label: string }[] = [
  { key: "date", label: "Date" },
  { key: "description", label: "Description" },
  { key: "reference", label: "Reference / UTR" },
  { key: "debit", label: "Debit (money out)" },
  { key: "credit", label: "Credit (money in)" },
  { key: "amount", label: "Amount (signed)" },
  { key: "balance", label: "Balance" },
];

/** The columns of a courier's COD remittance report. */
export const COD_FIELDS: { key: string; label: string }[] = [
  { key: "remittanceRef", label: "Remittance ID" },
  { key: "remittedOn", label: "Remittance date" },
  { key: "utr", label: "UTR" },
  { key: "awb", label: "AWB" },
  { key: "orderNumber", label: "Order number" },
  { key: "cod", label: "COD amount" },
  { key: "deduction", label: "Deduction" },
  { key: "net", label: "Net remitted" },
];

export const REQUIRED_BANK = ["date", "description"];

/** What is wrong with the bank mapping, for the import button; null when it can be submitted. */
export function bankMappingProblem(mapping: ColumnMapping): string | null {
  if (REQUIRED_BANK.some((key) => mapping.columns[key] === undefined)) {
    return "Map both the Date and Description columns.";
  }
  if (
    mapping.amountStyle === "DEBIT_CREDIT" &&
    mapping.columns.debit === undefined &&
    mapping.columns.credit === undefined
  ) {
    return "Map at least one of the Debit and Credit columns, or use a signed Amount column.";
  }
  if (mapping.amountStyle === "SIGNED" && mapping.columns.amount === undefined) {
    return "Map the Amount column, or switch to Debit and Credit columns.";
  }
  return null;
}

/** What is wrong with the COD remittance mapping, for the import button; null when it can be submitted. */
export function codMappingProblem(mapping: ColumnMapping): string | null {
  const required = ["remittanceRef", "remittedOn", "awb", "cod"];
  if (required.some((key) => mapping.columns[key] === undefined)) {
    return "Map the Remittance ID, Remittance date, AWB and COD amount columns.";
  }
  return null;
}

export const LINE_STATUSES = [
  { value: "", label: "All" },
  { value: "UNMATCHED", label: "Unmatched" },
  { value: "MATCHED", label: "Matched" },
  { value: "POSTED", label: "Posted" },
  { value: "IGNORED", label: "Ignored" },
];

/** Header words each field's column usually carries; the first match wins when no saved profile exists. */
const COLUMN_KEYWORDS: Record<string, string[]> = {
  date: ["date"],
  description: ["narration", "description", "particulars", "remarks"],
  reference: ["ref", "utr", "cheque"],
  debit: ["debit", "withdrawal"],
  credit: ["credit", "deposit"],
  amount: ["amount"],
  balance: ["balance"],
  remittanceRef: ["remittance id", "remittance"],
  remittedOn: ["date"],
  utr: ["utr"],
  awb: ["awb"],
  orderNumber: ["order"],
  cod: ["cod"],
  deduction: ["deduction", "charges"],
  net: ["net"],
  invoiceNo: ["invoice no", "invoice number", "invoice"],
  invoiceDate: ["invoice date", "date"],
  taxable: ["taxable", "freight", "base"],
  gst: ["gst", "tax amount"],
  cgst: ["cgst"],
  sgst: ["sgst"],
  igst: ["igst"],
  total: ["total", "invoice value"],
};

/** Maps each field to the first header cell whose text contains one of its keywords. */
export function guessColumns(header: string[], fields: { key: string; label: string }[]): Record<string, number> {
  const cells = header.map((cell) => cell.trim().toLowerCase());
  const columns: Record<string, number> = {};
  for (const field of fields) {
    const keywords = COLUMN_KEYWORDS[field.key] ?? [];
    const index = cells.findIndex((cell) => keywords.some((keyword) => cell.includes(keyword)));
    if (index >= 0) columns[field.key] = index;
  }
  return columns;
}
