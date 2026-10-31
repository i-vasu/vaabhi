import { afterEach, describe, expect, it, vi } from "vitest";
import { appVersion } from "./version";

describe("appVersion", () => {
  afterEach(() => vi.unstubAllEnvs());

  it("combines the release version and the commit", () => {
    vi.stubEnv("NEXT_PUBLIC_APP_VERSION", "0.2.0");
    vi.stubEnv("NEXT_PUBLIC_COMMIT_SHA", "fc31d29");
    expect(appVersion()).toEqual({ version: "0.2.0", commit: "fc31d29", label: "v0.2.0 (fc31d29)" });
  });

  it("falls back to dev when not built by CI", () => {
    vi.stubEnv("NEXT_PUBLIC_APP_VERSION", "");
    vi.stubEnv("NEXT_PUBLIC_COMMIT_SHA", "");
    expect(appVersion().label).toBe("vdev (dev)");
  });
});
