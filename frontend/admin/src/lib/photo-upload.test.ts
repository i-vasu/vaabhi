import { http, HttpResponse } from "msw";
import { afterEach, describe, expect, it, vi } from "vitest";
import { API, server } from "@/test/server";
import { mediaUrl, uploadPhoto } from "./photo-upload";

const renditions = [400, 800, 1600].map((width) => ({
  width,
  pixelWidth: Math.min(width, 1200),
  pixelHeight: Math.round((Math.min(width, 1200) * 4) / 3),
  blob: new Blob([String(width)], { type: "image/webp" }),
}));
const resize = vi.fn().mockResolvedValue(renditions);
const media = {
  id: 9,
  kind: "IMAGE",
  key: "products/7/abc",
  alt: "Ruby",
  colourName: null,
  width: 1200,
  height: 1600,
  status: "PENDING",
};

describe("uploadPhoto", () => {
  afterEach(() => vi.unstubAllEnvs());

  it("resizes, uploads every size straight to storage, then completes", async () => {
    const puts: Array<{ url: string; type: string | null; cache: string | null; body: string }> = [];
    let started: unknown;
    server.use(
      http.post(`${API}/api/v1/admin/products/7/media/uploads`, async ({ request }) => {
        started = await request.json();
        return HttpResponse.json(
          {
            media,
            renditions: [400, 800, 1600].map((w) => ({ width: w, uploadUrl: `https://r2.test/k/${w}.webp?sig=1` })),
            expiresAt: "2026-10-31T10:15:00Z",
          },
          { status: 201 },
        );
      }),
      http.put("https://r2.test/k/:file", async ({ request }) => {
        puts.push({
          url: request.url,
          type: request.headers.get("content-type"),
          cache: request.headers.get("cache-control"),
          body: await request.text(),
        });
        return new HttpResponse(null, { status: 200 });
      }),
      http.post(`${API}/api/v1/admin/products/7/media/9/complete`, () =>
        HttpResponse.json([{ ...media, status: "READY" }]),
      ),
    );

    const result = await uploadPhoto(7, new Blob(["original"]), { alt: "Ruby", colourName: null }, resize);

    expect(started).toEqual({ alt: "Ruby", colourName: null, width: 1200, height: 1600 });
    expect(puts.map((p) => [p.url, p.type, p.body])).toEqual([
      ["https://r2.test/k/400.webp?sig=1", "image/webp", "400"],
      ["https://r2.test/k/800.webp?sig=1", "image/webp", "800"],
      ["https://r2.test/k/1600.webp?sig=1", "image/webp", "1600"],
    ]);
    expect(puts[0]!.cache).toBe("public, max-age=31536000, immutable");
    expect(result[0]!.status).toBe("READY");
  });

  it("fails when storage rejects an upload or a size is missing", async () => {
    server.use(
      http.post(`${API}/api/v1/admin/products/7/media/uploads`, () =>
        HttpResponse.json({
          media,
          renditions: [{ width: 400, uploadUrl: "https://r2.test/k/400.webp" }],
          expiresAt: "2026-10-31T10:15:00Z",
        }),
      ),
      http.put("https://r2.test/k/:file", () => new HttpResponse(null, { status: 403 })),
    );
    await expect(uploadPhoto(7, new Blob(["x"]), { alt: "a", colourName: null }, resize)).rejects.toThrow(
      "Uploading the 400 px version failed",
    );

    server.use(
      http.post(`${API}/api/v1/admin/products/7/media/uploads`, () =>
        HttpResponse.json({
          media,
          renditions: [{ width: 2400, uploadUrl: "https://r2.test/k/2400.webp" }],
          expiresAt: "2026-10-31T10:15:00Z",
        }),
      ),
    );
    await expect(uploadPhoto(7, new Blob(["x"]), { alt: "a", colourName: null }, resize)).rejects.toThrow(
      "No 2400 px version",
    );
  });

  it("builds media URLs on the public domain", () => {
    expect(mediaUrl("products/7/abc")).toBe("https://media.vaabhi.com/products/7/abc/400.webp");
    vi.stubEnv("NEXT_PUBLIC_MEDIA_URL", "https://cdn.example/");
    expect(mediaUrl("k", 1600)).toBe("https://cdn.example/k/1600.webp");
  });
});
