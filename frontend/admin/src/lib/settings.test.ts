import { http, HttpResponse } from "msw";
import { describe, expect, it, vi } from "vitest";
import { API, server } from "@/test/server";
import { describeError, orNull, parseRupees, rupees, uploadSettingsImage } from "./settings";
import { ApiError } from "@vaabhi/api-client";

const fakeResize = vi.fn(async () => [{ width: 400, pixelWidth: 400, pixelHeight: 400, blob: new Blob(["webp"]) }]);

describe("settings helpers", () => {
  it("formats and parses rupees", () => {
    expect(rupees(1250.5)).toBe("₹ 1,250.5");
    expect(rupees(200)).toBe("₹ 200");
    expect(parseRupees("₹ 1,250.50")).toBe(1250.5);
    expect(parseRupees("200")).toBe(200);
    expect(parseRupees("12.345")).toBeNull();
    expect(parseRupees("abc")).toBeNull();
  });

  it("turns blank text into null", () => {
    expect(orNull("  ")).toBeNull();
    expect(orNull(" Vaabhi ")).toBe("Vaabhi");
  });

  it("describes API and network errors", () => {
    expect(describeError(new ApiError(400, "x", [{ field: "a", message: "b" }], "Bad"))).toEqual({
      message: "Bad",
      fields: [{ field: "a", message: "b" }],
    });
    expect(describeError(new ApiError(500, "x")).message).toBe("Could not save. Try again.");
    expect(describeError(new TypeError("fetch failed")).message).toMatch(/Could not reach the server/);
  });

  it("uploads a settings image and returns its key", async () => {
    let uploaded: string | null = null;
    server.use(
      http.post(`${API}/api/v1/admin/settings/images`, () =>
        HttpResponse.json({
          key: "settings/logo/abc",
          uploadUrl: "https://r2.example/put",
          expiresAt: "2026-01-01T00:00:00Z",
        }),
      ),
      http.put("https://r2.example/put", async ({ request }) => {
        uploaded = request.headers.get("Content-Type");
        return new HttpResponse(null, { status: 200 });
      }),
    );

    await expect(uploadSettingsImage("LOGO", new Blob(["png"]), fakeResize)).resolves.toBe("settings/logo/abc");
    expect(uploaded).toBe("image/webp");
  });

  it("fails when storage rejects the upload", async () => {
    server.use(
      http.post(`${API}/api/v1/admin/settings/images`, () =>
        HttpResponse.json({ key: "k", uploadUrl: "https://r2.example/put", expiresAt: "2026-01-01T00:00:00Z" }),
      ),
      http.put("https://r2.example/put", () => new HttpResponse(null, { status: 403 })),
    );

    await expect(uploadSettingsImage("UPI_QR", new Blob(["png"]), fakeResize)).rejects.toThrow(
      "Uploading the image failed",
    );
  });
});
