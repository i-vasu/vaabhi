import { describe, expect, it } from "vitest";
import type { ColumnMapping } from "@vaabhi/api-client";
import { billTotals, defaultAllocation, lineTax, shiprocketMappingProblem } from "./purchases";

const mapping = (columns: Record<string, number>): ColumnMapping => ({
  headerRow: 0,
  columns,
  dateFormat: "dd/MM/yyyy",
  amountStyle: "DEBIT_CREDIT",
});

describe("purchases helpers", () => {
  it("splits intra-state tax, SGST taking the odd paisa", () => {
    expect(lineTax("100.10", "5", false)).toEqual({
      cgst: "2.50",
      sgst: "2.51",
      igst: "0.00",
      total: "105.11",
    });
  });

  it("puts inter-state tax all in IGST", () => {
    expect(lineTax("100.10", "5", true)).toEqual({
      cgst: "0.00",
      sgst: "0.00",
      igst: "5.01",
      total: "105.11",
    });
  });

  it("adds the lines up into the bill's totals", () => {
    expect(
      billTotals(
        [
          { taxable: "100.10", rate: "5" },
          { taxable: "200.00", rate: "18" },
        ],
        false,
      ),
    ).toEqual({
      taxable: "300.10",
      cgst: "20.50",
      sgst: "20.51",
      igst: "0.00",
      tax: "41.01",
      total: "341.11",
    });
  });

  it("spreads a payment over the oldest bills first", () => {
    const bills = [
      { id: 2, billDate: "2026-10-10", dueDate: "2026-10-15", outstandingRupees: 500 },
      { id: 1, billDate: "2026-10-01", dueDate: "2026-10-01", outstandingRupees: 1000 },
    ];
    expect(defaultAllocation(bills, "1200")).toEqual([
      { billId: 1, amountRupees: "1000.00" },
      { billId: 2, amountRupees: "200.00" },
    ]);
  });

  it("leaves whatever is left over as an advance", () => {
    const bills = [
      { id: 1, billDate: "2026-10-01", dueDate: null, outstandingRupees: 1000 },
      { id: 2, billDate: "2026-10-02", dueDate: "2026-10-20", outstandingRupees: 500 },
    ];
    expect(defaultAllocation(bills, "2000")).toEqual([
      { billId: 1, amountRupees: "1000.00" },
      { billId: 2, amountRupees: "500.00" },
    ]);
  });

  it("asks for the Shiprocket invoice columns", () => {
    expect(shiprocketMappingProblem(mapping({ invoiceNo: 0, invoiceDate: 1, gst: 3 }))).toMatch(/Freight/);
    expect(shiprocketMappingProblem(mapping({ invoiceNo: 0, invoiceDate: 1, taxable: 2 }))).toBeNull();
  });
});
