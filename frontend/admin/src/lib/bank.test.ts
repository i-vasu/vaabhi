import { describe, expect, it } from "vitest";
import type { ColumnMapping } from "@vaabhi/api-client";
import { BANK_FIELDS, COD_FIELDS, bankMappingProblem, codMappingProblem, guessColumns } from "./bank";

const mapping = (
  columns: Record<string, number>,
  amountStyle: ColumnMapping["amountStyle"] = "DEBIT_CREDIT",
): ColumnMapping => ({ headerRow: 0, columns, dateFormat: "dd/MM/yyyy", amountStyle });

describe("bank helpers", () => {
  it("asks for the date and description columns", () => {
    expect(bankMappingProblem(mapping({ date: 0, debit: 2, credit: 3 }))).toMatch(/Description/);
    expect(bankMappingProblem(mapping({ description: 1, debit: 2, credit: 3 }))).toMatch(/Date/);
  });

  it("asks for the amount columns the style needs", () => {
    expect(bankMappingProblem(mapping({ date: 0, description: 1 }))).toMatch(/Debit and Credit/);
    expect(bankMappingProblem(mapping({ date: 0, description: 1 }, "SIGNED"))).toMatch(/Amount/);
    expect(bankMappingProblem(mapping({ date: 0, description: 1, amount: 2 }, "SIGNED"))).toBeNull();
    expect(bankMappingProblem(mapping({ date: 0, description: 1, credit: 3 }))).toBeNull();
  });

  it("asks for the COD report columns", () => {
    expect(codMappingProblem(mapping({ remittanceRef: 0, remittedOn: 1, awb: 2 }))).toMatch(/COD amount/);
    expect(codMappingProblem(mapping({ remittanceRef: 0, remittedOn: 1, awb: 2, cod: 3 }))).toBeNull();
  });

  it("guesses bank columns from the header row", () => {
    const header = ["Txn Date", "Narration", "Ref No", "Withdrawal Amt", "Deposit Amt", "Closing Balance"];
    expect(guessColumns(header, BANK_FIELDS)).toEqual({
      date: 0,
      description: 1,
      reference: 2,
      debit: 3,
      credit: 4,
      balance: 5,
    });
  });

  it("guesses COD report columns from the header row", () => {
    const header = ["Remittance ID", "Remittance Date", "UTR", "AWB", "Order ID", "COD", "Charges", "Net"];
    expect(guessColumns(header, COD_FIELDS)).toEqual({
      remittanceRef: 0,
      remittedOn: 1,
      utr: 2,
      awb: 3,
      orderNumber: 4,
      cod: 5,
      deduction: 6,
      net: 7,
    });
  });

  it("leaves unmatched fields out", () => {
    expect(guessColumns(["Date", "Description"], BANK_FIELDS)).toEqual({ date: 0, description: 1 });
  });
});
