import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@vaabhi/api-client";
import { API, server } from "@/test/server";
import { uploadSiteImage } from "./storefront";

const UPLOAD = "https://r2.example/site";
const widths = [400, 800, 1600];

function renditionsFor(sizes: number[]) {
  return sizes.map((width) => ({
    width,
    pixelWidth: width,
    pixelHeight: width,
    blob: new Blob([String(width)], { type: "image/webp" }),
  }));
}

function failingResize(error: unknown) {
  return vi.fn().mockRejectedValue(error);
}

function startUpload(sizes: number[] = widths) {
  server.use(
    http.post(`${API}/api/v1/admin/storefront/uploads`, () =>
      HttpResponse.json({
        key: "site/abc",
        renditions: sizes.map((width) => ({ width, uploadUrl: `${UPLOAD}/${width}.webp` })),
        expiresAt: "2026-01-01T00:00:00Z",
      }),
    ),
  );
}

describe("uploadSiteImage", () => {
  beforeEach(() => {
    startUpload();
    server.use(http.put(`${UPLOAD}/:file`, () => new HttpResponse(null, { status: 200 })));
  });

  it("resizes, uploads every size as WebP and returns the key", async () => {
    const puts: Array<{ url: string; type: string | null; body: string }> = [];
    server.use(
      http.put(`${UPLOAD}/:file`, async ({ request }) => {
        puts.push({ url: request.url, type: request.headers.get("content-type"), body: await request.text() });
        return new HttpResponse(null, { status: 200 });
      }),
    );
    const file = new File(["photo"], "shop.jpg", { type: "image/jpeg" });
    const resize = vi.fn().mockResolvedValue(renditionsFor(widths));

    await expect(uploadSiteImage(file, resize)).resolves.toBe("site/abc");
    expect(resize).toHaveBeenCalledWith(file, widths);
    expect(puts.map((p) => [p.url, p.type, p.body])).toEqual([
      [`${UPLOAD}/400.webp`, "image/webp", "400"],
      [`${UPLOAD}/800.webp`, "image/webp", "800"],
      [`${UPLOAD}/1600.webp`, "image/webp", "1600"],
    ]);
  });

  it("reports a HEIC photo by its type", async () => {
    const file = new Blob(["heic"], { type: "image/heic" });

    await expect(uploadSiteImage(file, failingResize(new Error("decode failed")))).rejects.toThrow(
      "This is an iPhone HEIC photo",
    );
  });

  it("reports a HEIC photo by its file name", async () => {
    const file = new File(["heic"], "IMG_2041.HEIC");

    await expect(uploadSiteImage(file, failingResize(new TypeError("decode failed")))).rejects.toThrow(
      "This is an iPhone HEIC photo",
    );
  });

  it("rethrows a plain error that explains the problem", async () => {
    const file = new File(["x"], "shop.png", { type: "image/png" });

    await expect(
      uploadSiteImage(file, failingResize(new Error("This browser cannot create WebP images."))),
    ).rejects.toThrow("This browser cannot create WebP images.");
  });

  it("says the browser could not open the photo for other failures", async () => {
    const file = new File(["x"], "shop.png", { type: "image/png" });
    const generic = "This browser could not open the photo. Try a JPEG or PNG.";

    await expect(uploadSiteImage(file, failingResize("corrupt"))).rejects.toThrow(generic);
    await expect(uploadSiteImage(file, failingResize(new TypeError("bad image")))).rejects.toThrow(generic);
    await expect(uploadSiteImage(file, failingResize(new DOMException("bad", "NotSupportedError")))).rejects.toThrow(
      generic,
    );
    await expect(uploadSiteImage(file, failingResize(new Error("")))).rejects.toThrow(generic);
  });

  it("says storage did not accept the upload when the PUT never answers", async () => {
    server.use(http.put(`${UPLOAD}/:file`, () => HttpResponse.error()));
    const file = new File(["x"], "shop.jpg", { type: "image/jpeg" });

    await expect(uploadSiteImage(file, vi.fn().mockResolvedValue(renditionsFor(widths)))).rejects.toThrow(
      `Photo storage did not accept the upload from ${window.location.origin}`,
    );
  });

  it("fails with an API error when storage refuses a size", async () => {
    server.use(http.put(`${UPLOAD}/:file`, () => new HttpResponse(null, { status: 403 })));
    const file = new File(["x"], "shop.jpg", { type: "image/jpeg" });

    const resize = vi.fn().mockResolvedValue(renditionsFor(widths));
    const error = await uploadSiteImage(file, resize).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toHaveProperty("message", "Uploading the 400 px version failed");
  });

  it("fails when a size was not made", async () => {
    const file = new File(["x"], "shop.jpg", { type: "image/jpeg" });

    await expect(uploadSiteImage(file, vi.fn().mockResolvedValue(renditionsFor([400])))).rejects.toThrow(
      "No 800 px version was made",
    );
  });
});
