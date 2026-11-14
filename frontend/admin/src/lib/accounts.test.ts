import type { JournalSourceLink, LedgerStatement } from "@vaabhi/api-client";
import { describe, expect, it, vi } from "vitest";
import {
  csvCell,
  downloadText,
  fyLabel,
  fyRange,
  ledgerCsv,
  monthTitle,
  relativeTime,
  sourceHref,
  sourceLabel,
  toPaise,
} from "./accounts";

const link = (patch: Partial<JournalSourceLink>): JournalSourceLink => ({
  type: "INVOICE",
  id: 5,
  label: "INV-305",
  orderId: null,
  alterationId: null,
  orderNumber: null,
  ...patch,
});

describe("sourceHref", () => {
  it("opens the page each source lives on", () => {
    expect(sourceHref(link({ type: "INVOICE" }))).toBe("/money/invoices/view/?id=5");
    expect(sourceHref(link({ type: "CREDIT_NOTE", id: 7 }))).toBe("/money/credit-notes/view/?id=7");
    expect(sourceHref(link({ type: "ORDER_PAYMENT", orderId: 42 }))).toBe("/orders/view/?id=42");
    expect(sourceHref(link({ type: "ALTERATION_PAYMENT", alterationId: 9 }))).toBe("/alterations/view/?id=9");
    expect(sourceHref(link({ type: "WEB_ORDER", orderNumber: "WEB 1" }))).toBe("/online-orders/view/?number=WEB%201");
    expect(sourceHref(link({ type: "WEB_RETURN", orderNumber: "WEB-2" }))).toBe("/online-orders/view/?number=WEB-2");
    expect(sourceHref(link({ type: "EXPENSE" }))).toBe("/money/expenses/");
  });

  it("has no page when the source is missing or unknown", () => {
    expect(sourceHref(link({ type: "ORDER_PAYMENT" }))).toBeNull();
    expect(sourceHref(link({ type: "ALTERATION_PAYMENT" }))).toBeNull();
    expect(sourceHref(link({ type: "WEB_ORDER" }))).toBeNull();
    expect(sourceHref(link({ type: "MANUAL" }))).toBeNull();
  });
});

describe("sourceLabel", () => {
  it("names every source and passes unknown ones through", () => {
    expect(
      ["INVOICE", "CREDIT_NOTE", "ORDER_PAYMENT", "ALTERATION_PAYMENT", "WEB_ORDER", "WEB_RETURN", "EXPENSE"].map(
        sourceLabel,
      ),
    ).toEqual(["Invoice", "Credit note", "Order payment", "Alteration payment", "Web order", "Web return", "Expense"]);
    expect(sourceLabel("MANUAL")).toBe("Manual entry");
    expect(sourceLabel("OPENING")).toBe("Opening balances");
    expect(sourceLabel("YEAR_END")).toBe("YEAR_END");
  });
});

describe("relativeTime", () => {
  const now = new Date("2026-11-14T12:00:00Z");

  it("rounds to the largest whole unit", () => {
    expect(relativeTime("2026-11-14T11:59:30Z", now)).toBe("just now");
    expect(relativeTime("2026-11-14T11:59:00Z", now)).toBe("1 minute ago");
    expect(relativeTime("2026-11-14T11:30:00Z", now)).toBe("30 minutes ago");
    expect(relativeTime("2026-11-14T11:00:00Z", now)).toBe("1 hour ago");
    expect(relativeTime("2026-11-14T07:00:00Z", now)).toBe("5 hours ago");
    expect(relativeTime("2026-11-13T12:00:00Z", now)).toBe("1 day ago");
    expect(relativeTime("2026-11-09T12:00:00Z", now)).toBe("5 days ago");
  });

  it("treats a time in the future as just now", () => {
    expect(relativeTime("2026-11-14T13:00:00Z", now)).toBe("just now");
  });
});

describe("csvCell", () => {
  it("quotes commas, quotes and line breaks", () => {
    expect(csvCell("plain")).toBe("plain");
    expect(csvCell("a, b")).toBe('"a, b"');
    expect(csvCell('say "hi"')).toBe('"say ""hi"""');
    expect(csvCell("two\nlines")).toBe('"two\nlines"');
  });

  it("defuses text that a spreadsheet would run as a formula, but not numbers", () => {
    expect(csvCell("=SUM(A1)")).toBe("'=SUM(A1)");
    expect(csvCell("@cmd")).toBe("'@cmd");
    expect(csvCell(-25)).toBe("-25");
  });
});

describe("toPaise", () => {
  it("reads rupees as whole paise", () => {
    expect(toPaise("")).toBe(0);
    expect(toPaise("12.5")).toBe(1250);
    expect(toPaise("₹1,250.75")).toBe(125075);
    expect(toPaise("12.345")).toBeNull();
    expect(toPaise("abc")).toBeNull();
  });
});

describe("financial years", () => {
  it("runs from April to March", () => {
    expect(fyRange("2026-04-01")).toEqual({ from: "2026-04-01", to: "2027-03-31" });
    expect(fyRange("2027-03-31")).toEqual({ from: "2026-04-01", to: "2027-03-31" });
    expect(fyLabel("2026-04-01")).toBe("FY 2026–27");
    expect(fyLabel("2099-04-01")).toBe("FY 2099–00");
  });

  it("names a month", () => {
    expect(monthTitle("2026-04-15")).toBe("April 2026");
  });
});

describe("ledgerCsv", () => {
  it("lists the opening, each line and the closing", () => {
    const statement: LedgerStatement = {
      accountId: 1,
      accountCode: "1000",
      accountName: "Cash in hand",
      type: "ASSET",
      from: "2026-11-01",
      to: "2026-11-30",
      openingRupees: 1000,
      totalDebitRupees: 500,
      totalCreditRupees: 200,
      closingRupees: 1300,
      lines: [
        {
          entryId: 3,
          number: "JV-3",
          date: "2026-11-05",
          narration: "Fabric, paid",
          partyName: null,
          memo: null,
          debitRupees: 0,
          creditRupees: 200,
          balanceRupees: 800,
        },
      ],
    };
    expect(ledgerCsv(statement).split("\r\n")).toEqual([
      "Date,Entry,Narration,Party,Memo,Debit,Credit,Balance",
      "2026-11-01,,Opening balance,,,,,1000",
      '2026-11-05,JV-3,"Fabric, paid",,,,200,800',
      "2026-11-30,,Closing balance,,,500,200,1300",
    ]);
  });
});

describe("downloadText", () => {
  it("saves the text through a temporary link", () => {
    vi.useFakeTimers();
    const create = vi.fn(() => "blob:ledger");
    const revoke = vi.fn();
    Object.assign(URL, { createObjectURL: create, revokeObjectURL: revoke });
    const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});

    downloadText("ledger.csv", "a,b");

    expect(create).toHaveBeenCalledTimes(1);
    expect(click).toHaveBeenCalledTimes(1);
    const anchor = click.mock.contexts[0] as HTMLAnchorElement;
    expect(anchor.download).toBe("ledger.csv");
    vi.runAllTimers();
    expect(revoke).toHaveBeenCalledWith("blob:ledger");
    click.mockRestore();
    vi.useRealTimers();
  });
});
