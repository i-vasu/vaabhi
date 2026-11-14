import { describe, expect, it } from "vitest";
import { SCHEDULE_LINES, defaultLineFor, linesFor } from "./statements";

describe("statements helpers", () => {
  it("lists the income lines in statement order", () => {
    expect(linesFor("INCOME").map((line) => line.value)).toEqual(["REVENUE_FROM_OPERATIONS", "OTHER_INCOME"]);
  });

  it("puts an asset on other current assets by default", () => {
    expect(defaultLineFor("ASSET")).toBe("OTHER_CURRENT_ASSETS");
  });

  it("has every Schedule III line", () => {
    expect(SCHEDULE_LINES).toHaveLength(20);
  });
});
