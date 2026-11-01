import { describe, expect, it } from "vitest";
import { deliveryWindow, formatDeliveryWindow, isValidPincode, stitchingDays } from "./delivery";

describe("delivery estimate", () => {
  it("accepts Indian pincodes", () => {
    expect(isValidPincode("600001")).toBe(true);
    expect(isValidPincode(" 110001 ")).toBe(true);
    expect(isValidPincode("012345")).toBe(false);
    expect(isValidPincode("12345")).toBe(false);
  });

  it("adds stitching time to 4–6 days", () => {
    const from = new Date(2026, 10, 1);
    expect(stitchingDays(undefined)).toEqual({ min: 0, max: 0 });
    expect(stitchingDays("MEASUREMENTS")).toEqual({ min: 7, max: 10 });
    const window = deliveryWindow(from, stitchingDays("SIZE"));
    expect(window.earliest.getDate()).toBe(10);
    expect(window.latest.getDate()).toBe(14);
    expect(formatDeliveryWindow(deliveryWindow(from, stitchingDays("NONE")))).toBe("Thu, 5 Nov – Sat, 7 Nov");
  });
});
