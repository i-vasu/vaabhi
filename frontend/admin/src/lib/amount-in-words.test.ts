import { describe, expect, it } from "vitest";
import { amountInWords } from "./amount-in-words";

describe("amountInWords", () => {
  it("writes whole rupees", () => {
    expect(amountInWords(1050)).toBe("Rupees One Thousand Fifty only");
  });

  it("uses lakh and adds paise", () => {
    expect(amountInWords(125000.5)).toBe("Rupees One Lakh Twenty Five Thousand and Fifty Paise only");
  });

  it("uses crore", () => {
    expect(amountInWords(10000000)).toBe("Rupees One Crore only");
  });

  it("writes paise alone", () => {
    expect(amountInWords(0.5)).toBe("Fifty Paise only");
  });

  it("writes zero", () => {
    expect(amountInWords(0)).toBe("Rupees Zero only");
  });
});
