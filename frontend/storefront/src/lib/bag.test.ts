import { afterEach, describe, expect, it, vi } from "vitest";
import {
  addToBag,
  addToWishlist,
  bagCount,
  bagStore,
  lineKey,
  MAX_QUANTITY,
  removeLine,
  restoreLine,
  setQuantity,
  toggleWishlist,
  wishlistStore,
} from "./bag";

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
    expect(addToBag(line).capped).toBe(false);
    expect(addToBag(line).capped).toBe(true);
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

  it("changes quantities within 1–3, removes and restores lines", () => {
    addToBag(line);
    addToBag({ ...line, sku: "VB-MUST" });
    const key = lineKey(line);
    setQuantity(key, 9);
    expect(bagStore.read()[0]!.quantity).toBe(3);
    setQuantity(key, 0);
    expect(bagStore.read()[0]!.quantity).toBe(1);
    const removed = removeLine(key)!;
    expect(removed.index).toBe(0);
    expect(bagStore.read().map((l) => l.sku)).toEqual(["VB-MUST"]);
    expect(removeLine("missing")).toBeUndefined();
    restoreLine(removed);
    expect(bagStore.read().map((l) => l.sku)).toEqual(["VB-RANI", "VB-MUST"]);
  });

  it("toggles the wishlist", () => {
    addToWishlist("kurta");
    addToWishlist("kurta");
    expect(wishlistStore.read()).toEqual(["kurta"]);
    toggleWishlist("kurta");
    expect(toggleWishlist("saree")).toBe(true);
    expect(wishlistStore.read()).toEqual(["saree"]);
    expect(toggleWishlist("saree")).toBe(false);
    expect(wishlistStore.read()).toEqual([]);
  });
});
