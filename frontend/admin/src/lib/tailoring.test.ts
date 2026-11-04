import { describe, expect, it } from "vitest";
import { client } from "@/test/tailoring-fixtures";
import { addDays, formatDate, formatPhone, idParam, orderTotals, parsePercent, summaryOf, today } from "./tailoring";

describe("order totals", () => {
  const items = [
    { priceRupees: 500, quantity: 2 },
    { priceRupees: 100, quantity: 1 },
  ];

  it("takes a percentage off the items by default", () => {
    expect(orderTotals(items, 200, "PERCENT", 10, "ITEMS_BEFORE_TAX")).toEqual({
      itemsRupees: 1100,
      deliveryRupees: 200,
      discountRupees: 110,
      totalRupees: 1190,
    });
  });

  it("can discount the delivery charge too", () => {
    expect(orderTotals(items, 200, "PERCENT", 10, "ORDER_TOTAL").discountRupees).toBe(130);
  });

  it("never takes off more than there is", () => {
    expect(orderTotals(items, 0, "AMOUNT", 5000, "ITEMS_BEFORE_TAX").totalRupees).toBe(0);
    expect(orderTotals(items, 0, "PERCENT", 200, "ITEMS_BEFORE_TAX").totalRupees).toBe(0);
  });
});

describe("parsing and formatting", () => {
  it("reads percentages typed by a person", () => {
    expect(parsePercent("12.5")).toBe(12.5);
    expect(parsePercent("10 %")).toBe(10);
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
