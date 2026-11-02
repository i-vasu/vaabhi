import type { Report, ReportColumn } from "@vaabhi/api-client";
import { rupees } from "./settings";
import { formatDate } from "./tailoring";

/** A cell as shown on screen: money in rupees, dates short, numbers grouped. */
export function formatCell(column: ReportColumn, value: unknown): string {
  if (value === null || value === undefined || value === "") return "—";
  switch (column.type) {
    case "MONEY":
      return rupees(Number(value));
    case "DATE":
      return formatDate(String(value));
    case "PERCENT":
      return `${Number(value)}%`;
    case "NUMBER":
      return Number(value).toLocaleString("en-IN", { maximumFractionDigits: 2 });
    default:
      return String(value).replace(/_/g, " ");
  }
}

/** A cell for CSV: plain numbers (money in rupees with two decimals), dates as yyyy-mm-dd. */
function csvCell(column: ReportColumn, value: unknown): string {
  if (value === null || value === undefined) return "";
  const text = column.type === "MONEY" ? (Number(value) / 100).toFixed(2) : String(value);
  return /[",\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

/** The report as CSV, with a totals line when it has totals. */
export function toCsv(report: Report): string {
  const header = report.columns.map((c) =>
    csvCell({ ...c, type: "TEXT" }, c.label + (c.type === "MONEY" ? " (₹)" : "")),
  );
  const lines = [header.join(",")];
  for (const row of report.rows) {
    lines.push(report.columns.map((c) => csvCell(c, (row as Record<string, unknown>)[c.key])).join(","));
  }
  const totals = report.totals as Record<string, unknown>;
  if (Object.keys(totals).length > 0) {
    lines.push(report.columns.map((c, i) => (i === 0 ? "Total" : csvCell(c, totals[c.key]))).join(","));
  }
  return lines.join("\n") + "\n";
}

/** Saves text as a file in the browser. */
export function download(filename: string, text: string, type = "text/csv;charset=utf-8") {
  const url = URL.createObjectURL(new Blob(["﻿" + text], { type }));
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}
