import { describe, expect, it } from "vitest";
import { leftAfter } from "./make-products";

describe("leftAfter", () => {
  it("takes the pieces out of what is on hand", () => {
    expect(leftAfter(10, [{ quantity: 3 }, { quantity: 3 }, { quantity: 3 }])).toBe(1);
  });

  it("goes below zero when the pieces need more than there is", () => {
    expect(leftAfter(10, [{ quantity: 3 }, { quantity: 3 }, { quantity: 3 }, { quantity: 3 }])).toBe(-2);
  });

  it("ignores rows without a quantity yet", () => {
    expect(leftAfter(2.5, [{ quantity: null }, { quantity: 0.75 }])).toBe(1.75);
  });
});
