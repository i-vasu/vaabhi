import type { WorkPiece } from "@vaabhi/api-client";
import { describe, expect, it, vi } from "vitest";
import { ShareBlockedError, fetchShareFiles, pieceMessage, sharePiece, waNumber } from "./piece-share";

const piece: WorkPiece = {
  pieceId: 7,
  productId: 70,
  productName: "Kalamkari kurti – peacock",
  productSlug: "kalamkari-kurti-peacock",
  productStatus: "DRAFT",
  stockItemId: 12,
  stockItemName: "Kalamkari cotton",
  quantity: 3,
  unit: "M",
  photoKey: "stock/fabric",
  stage: { id: 2, name: "Stitching", kind: "WORK" },
  stageSince: "2026-11-09T10:00:00Z",
  assignedTo: { id: 4, name: "Lakshmi" },
  assignedPhone: "98765 43210",
  note: "Peacock panel on the front yoke, piping in gold",
  photos: [
    { id: 1, key: "workshop/pieces/a" },
    { id: 2, key: "workshop/pieces/b" },
  ],
  fabric: { material: "Kalamkari cotton", colour: "maroon", widthIn: 44, gsm: 120, threadCount: 60 },
  dimensions: [
    { label: "Length", value: 44, unit: "IN" },
    { label: "Chest", value: 38, unit: "IN" },
  ],
};

// A downloaded photo. Not a real Response: on Node 22 its blob() is Node's Blob, which jsdom's File will not take.
const ok = () =>
  Promise.resolve({
    ok: true,
    blob: () => Promise.resolve(new Blob(["x"], { type: "image/webp" })),
  } as unknown as Response);

describe("pieceMessage", () => {
  it("reads like the spec example", () => {
    expect(pieceMessage(piece)).toBe(
      [
        "🧵 Vaabhi – Stitching",
        "Kalamkari kurti – peacock",
        'Fabric: Kalamkari cotton, maroon · 3 metres · 44" wide · 120 GSM',
        "Measurements: Length 44 in · Chest 38 in",
        "Note: Peacock panel on the front yoke, piping in gold",
      ].join("\n"),
    );
  });

  it("leaves out what is not known", () => {
    const bare = {
      ...piece,
      note: null,
      dimensions: [],
      fabric: { material: null, colour: null, widthIn: null, gsm: null, threadCount: null },
    } as unknown as WorkPiece;
    expect(pieceMessage(bare)).toBe("🧵 Vaabhi – Stitching\nKalamkari kurti – peacock\nFabric: 3 metres");
  });
});

describe("sharePiece", () => {
  const opened = () => vi.fn((url: string) => url.length > 0);

  it("shares the photos as files where the phone can", async () => {
    const share = vi.fn(() => Promise.resolve());
    const prepared = await fetchShareFiles(piece, { fetch: ok });
    const result = await sharePiece(piece, prepared, {
      nav: { canShare: () => true, share },
      fetch: ok,
      open: opened(),
    });
    expect(result).toBe("shared");
    expect((share.mock.calls[0] as unknown as [ShareData])[0].files).toHaveLength(3);
  });

  it("sends a photo it could not fetch as a link", async () => {
    const share = vi.fn(() => Promise.resolve());
    let call = 0;
    const prepared = await fetchShareFiles(piece, {
      fetch: (url) => (call++ === 0 ? Promise.reject(new TypeError(url)) : ok()),
    });
    await sharePiece(piece, prepared, { nav: { canShare: () => true, share }, fetch: ok, open: opened() });
    const data = (share.mock.calls[0] as unknown as [ShareData])[0];
    expect(data.files).toHaveLength(2);
    expect(data.text).toContain("workshop/pieces/a/400.webp");
  });

  it("does not wait on the network when the photos are not ready yet: links instead", async () => {
    const share = vi.fn(() => Promise.resolve());
    const fetch = vi.fn(ok);
    await sharePiece(piece, undefined, { nav: { canShare: () => true, share }, fetch, open: opened() });
    expect(fetch).not.toHaveBeenCalled();
    const data = (share.mock.calls[0] as unknown as [ShareData])[0];
    expect(data.files).toBeUndefined();
    expect(data.text).toContain("stock/fabric/400.webp");
  });

  it("opens wa.me with the tailor's number where files cannot be shared", async () => {
    const open = opened();
    const result = await sharePiece(piece, undefined, { nav: {}, fetch: ok, open });
    expect(result).toBe("link");
    expect(open.mock.calls[0]![0]).toMatch(/^https:\/\/wa\.me\/919876543210\?text=/);
  });

  it("says so when the browser blocks the WhatsApp window", async () => {
    await expect(sharePiece(piece, undefined, { nav: {}, fetch: ok, open: () => false })).rejects.toBeInstanceOf(
      ShareBlockedError,
    );
  });

  it("does nothing more when the share sheet is closed", async () => {
    const open = opened();
    const result = await sharePiece(piece, [], {
      nav: { canShare: () => true, share: () => Promise.reject(new DOMException("closed", "AbortError")) },
      fetch: ok,
      open,
    });
    expect(result).toBe("cancelled");
    expect(open).not.toHaveBeenCalled();
  });
});

describe("waNumber", () => {
  it("adds India's code to a bare mobile number", () => {
    expect(waNumber("98765 43210")).toBe("919876543210");
    expect(waNumber("+91 98765 43210")).toBe("919876543210");
    expect(waNumber(null)).toBe("");
  });
});
