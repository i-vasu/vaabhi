import { act, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { addToBag } from "@/lib/bag";
import { BagLink } from "./bag-link";

describe("BagLink", () => {
  afterEach(() => localStorage.clear());

  it("shows the item count and updates when the bag changes", () => {
    render(<BagLink />);
    expect(screen.getByRole("link", { name: "Shopping bag" })).toHaveAttribute("href", "/bag");

    const line = {
      sku: "A",
      slug: "a",
      name: "A",
      colourName: "Red",
      colourHex: "#B3261E",
      size: null,
      addonCode: null,
      addonName: null,
      blouseSize: null,
      unitPricePaise: 100,
      addonPricePaise: 0,
      imageKey: null,
    };
    act(() => {
      addToBag(line);
    });
    expect(screen.getByRole("link", { name: "Shopping bag, 1 item" })).toBeInTheDocument();
    act(() => {
      addToBag({ ...line, sku: "B" });
    });
    expect(screen.getByRole("link", { name: "Shopping bag, 2 items" })).toBeInTheDocument();
  });
});
