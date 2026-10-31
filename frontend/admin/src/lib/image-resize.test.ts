import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { resizeToWebp, UnsupportedBrowserError } from "./image-resize";

describe("resizeToWebp", () => {
  const close = vi.fn();
  let encodeAs = "image/webp";
  let context: object | null = {};
  const drawn: number[][] = [];

  beforeEach(() => {
    encodeAs = "image/webp";
    context = { drawImage: (_: unknown, ...args: number[]) => drawn.push(args), imageSmoothingQuality: "low" };
    drawn.length = 0;
    vi.stubGlobal("createImageBitmap", vi.fn().mockResolvedValue({ width: 1200, height: 1600, close }));
    vi.spyOn(document, "createElement").mockImplementation(
      () =>
        ({
          width: 0,
          height: 0,
          getContext: () => context,
          toBlob: (resolve: (b: Blob | null) => void, type: string) =>
            resolve(new Blob(["x"], { type: encodeAs === "image/webp" ? type : encodeAs })),
        }) as unknown as HTMLCanvasElement,
    );
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("makes each size as WebP without upscaling", async () => {
    const renditions = await resizeToWebp(new Blob(["photo"]), [400, 800, 1600]);
    expect(renditions.map((r) => [r.width, r.pixelWidth, r.pixelHeight, r.blob.type])).toEqual([
      [400, 400, 533, "image/webp"],
      [800, 800, 1067, "image/webp"],
      [1600, 1200, 1600, "image/webp"],
    ]);
    expect(drawn.at(-1)).toEqual([0, 0, 1200, 1600]);
    expect(close).toHaveBeenCalled();
  });

  it("explains when the browser cannot encode WebP", async () => {
    encodeAs = "image/png";
    await expect(resizeToWebp(new Blob(["photo"]), [400])).rejects.toBeInstanceOf(UnsupportedBrowserError);
    context = null;
    await expect(resizeToWebp(new Blob(["photo"]), [400])).rejects.toThrow(/Chrome, Edge or Firefox/);
  });
});
