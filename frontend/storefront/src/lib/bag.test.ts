import { afterEach, describe, expect, it, vi } from "vitest";
import { addToBag, bagCount, bagStore, MAX_QUANTITY, toggleWishlist, wishlistStore } from "./bag";

const line = {
  sku: "VB-RANI",
  slug: "saree",
  name: "Saree",
  colourName: "Rani pink",
  colourHex: "#C2185B",
  size: null,
  addonCode: "blouse-standard",
  addonName: "Stitched blouse",
  blouseSize: "36",
  unitPricePaise: 100,
  addonPricePaise: 20,
  imageKey: null,
};

describe("bag", () => {
  afterEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it("adds lines, merging the same choice up to the limit", () => {
    addToBag(line);
    addToBag(line);
    addToBag({ ...line, blouseSize: "38" });
    const lines = bagStore.read();
    expect(lines.map((l) => [l.blouseSize, l.quantity])).toEqual([
      ["36", 2],
      ["38", 1],
    ]);
    expect(bagCount(lines)).toBe(3);
    for (let i = 0; i < 10; i++) addToBag(line);
    expect(bagStore.read()[0]!.quantity).toBe(MAX_QUANTITY);
  });

  it("notifies subscribers in this tab and others", () => {
    const onChange = vi.fn();
    const unsubscribe = bagStore.subscribe(onChange);
    addToBag(line);
    window.dispatchEvent(new StorageEvent("storage", { key: "bag.v1" }));
    window.dispatchEvent(new StorageEvent("storage", { key: "other" }));
    expect(onChange).toHaveBeenCalledTimes(2);
    unsubscribe();
    addToBag(line);
    expect(onChange).toHaveBeenCalledTimes(2);
  });

  it("survives unavailable or corrupt storage", () => {
    localStorage.setItem("bag.v1", "{not json");
    expect(bagStore.read()).toEqual([]);
    expect(bagStore.serverValue()).toEqual([]);
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new Error("blocked");
    });
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new Error("blocked");
    });
    expect(bagStore.read()).toEqual([]);
    expect(() => addToBag(line)).not.toThrow();
  });

  it("toggles the wishlist", () => {
    expect(toggleWishlist("saree")).toBe(true);
    expect(wishlistStore.read()).toEqual(["saree"]);
    expect(toggleWishlist("saree")).toBe(false);
    expect(wishlistStore.read()).toEqual([]);
  });
});
