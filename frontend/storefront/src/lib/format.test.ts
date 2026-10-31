import { describe, expect, it } from "vitest";
import { discountLabel, formatPrice } from "./format";

describe("formatPrice", () => {
  it("uses rupees with Indian digit grouping", () => {
    expect(formatPrice(12499)).toBe("₹12,499");
    expect(formatPrice(124999)).toBe("₹1,24,999");
  });
});

describe("discountLabel", () => {
  it("rounds the percentage off MRP", () => {
    expect(discountLabel(12499, 15999)).toBe("22% off");
  });

  it("is empty without a real discount", () => {
    expect(discountLabel(999)).toBeUndefined();
    expect(discountLabel(999, 999)).toBeUndefined();
  });
});
