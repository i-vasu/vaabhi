import { render } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

const initializeFaro = vi.fn();
vi.mock("@grafana/faro-web-sdk", () => ({
  initializeFaro: (...args: unknown[]) => initializeFaro(...args),
  getWebInstrumentations: () => [],
}));

// Faro keeps module-level state, so each test loads a fresh copy of the component.
async function loadFaro() {
  vi.resetModules();
  return (await import("./faro")).Faro;
}

describe("Faro", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
    initializeFaro.mockClear();
  });

  it("does nothing when no collector URL is configured", async () => {
    vi.stubEnv("NEXT_PUBLIC_FARO_URL", "");
    const Faro = await loadFaro();

    render(<Faro appName="test-app" />);

    expect(initializeFaro).not.toHaveBeenCalled();
  });

  it("initialises once with the app name and environment", async () => {
    vi.stubEnv("NEXT_PUBLIC_FARO_URL", "https://faro.example/collect/abc");
    vi.stubEnv("NEXT_PUBLIC_DEPLOY_ENV", "production");
    const Faro = await loadFaro();

    const { rerender } = render(<Faro appName="test-app" />);
    rerender(<Faro appName="test-app" />);

    expect(initializeFaro).toHaveBeenCalledTimes(1);
    expect(initializeFaro).toHaveBeenCalledWith(
      expect.objectContaining({
        url: "https://faro.example/collect/abc",
        app: expect.objectContaining({ name: "test-app", environment: "production" }),
      }),
    );
  });
});
