import { describe, expect, it } from "vitest";
import { client } from "@/test/tailoring-fixtures";
import { addDays, formatDate, formatPhone, idParam, orderTotals, parsePercent, summaryOf, today } from "./tailoring";

describe("order totals", () => {
  const items = [
    { pricePaise: 50000, quantity: 2 },
    { pricePaise: 10000, quantity: 1 },
  ];

  it("takes a percentage off the items by default", () => {
    expect(orderTotals(items, 20000, "PERCENT", 1000, "ITEMS_BEFORE_TAX")).toEqual({
      itemsPaise: 110000,
      deliveryPaise: 20000,
      discountPaise: 11000,
      totalPaise: 119000,
    });
  });

  it("can discount the delivery charge too", () => {
    expect(orderTotals(items, 20000, "PERCENT", 1000, "ORDER_TOTAL").discountPaise).toBe(13000);
  });

  it("never takes off more than there is", () => {
    expect(orderTotals(items, 0, "AMOUNT", 500000, "ITEMS_BEFORE_TAX").totalPaise).toBe(0);
    expect(orderTotals(items, 0, "PERCENT", 20000, "ITEMS_BEFORE_TAX").totalPaise).toBe(0);
  });
});

describe("parsing and formatting", () => {
  it("reads percentages as basis points", () => {
    expect(parsePercent("12.5")).toBe(1250);
    expect(parsePercent("10 %")).toBe(1000);
    expect(parsePercent("101")).toBeNull();
    expect(parsePercent("ten")).toBeNull();
  });

  it("formats dates without the year when it is this year", () => {
    const now = new Date("2026-11-02T10:00:00");
    expect(formatDate("2026-11-08", now)).toBe("8 Nov");
    expect(formatDate("2025-01-20", now)).toBe("20 Jan 2025");
    expect(formatDate("2026-11-01T11:00:00+05:30", now)).toBe("1 Nov");
  });

  it("adds days across month ends", () => {
    expect(addDays("2026-11-28", 7)).toBe("2026-12-05");
    expect(today(new Date(2026, 10, 2, 23, 30))).toBe("2026-11-02");
  });

  it("groups Indian mobile numbers", () => {
    expect(formatPhone("+919876500001")).toBe("98765 00001");
    expect(formatPhone("+447700900123")).toBe("+447700900123");
  });

  it("reads ids from the query string", () => {
    expect(idParam("42")).toBe(42);
    expect(idParam("0")).toBeNull();
    expect(idParam("abc")).toBeNull();
    expect(idParam(null)).toBeNull();
  });

  it("summarises a customer for pickers", () => {
    expect(summaryOf(client)).toMatchObject({ id: 7, name: "Meera Iyer", phone: "+919876500001" });
  });
});
