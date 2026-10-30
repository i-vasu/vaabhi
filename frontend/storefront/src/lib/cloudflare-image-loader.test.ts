import { describe, expect, it, vi } from "vitest";
import cloudflareImageLoader from "./cloudflare-image-loader";

describe("cloudflareImageLoader", () => {
  it("builds a flexible-variant URL for an image id", () => {
    vi.stubEnv("NEXT_PUBLIC_CF_IMAGES_ACCOUNT_HASH", "abc123");
    expect(cloudflareImageLoader({ src: "img-1", width: 640 })).toBe(
      "https://imagedelivery.net/abc123/img-1/w=640,q=75,f=auto",
    );
  });

  it("passes absolute and local URLs through", () => {
    expect(cloudflareImageLoader({ src: "https://x.test/a.jpg", width: 640 })).toBe("https://x.test/a.jpg");
    expect(cloudflareImageLoader({ src: "/logo.png", width: 64 })).toBe("/logo.png");
  });
});
