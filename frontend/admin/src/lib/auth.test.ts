import { afterEach, describe, expect, it, vi } from "vitest";
import { owner, tokenFor } from "@/test/tokens";
import { clearSession, getStaff, getToken, isExpired, rolesFromToken, saveSession, setToken } from "./auth";

describe("token storage", () => {
  afterEach(() => {
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  it("stores, reads and clears the token", () => {
    const token = tokenFor({ sub: "staff:1" });
    expect(getToken()).toBeUndefined();
    setToken(token);
    expect(getToken()).toBe(token);
    setToken(null);
    expect(getToken()).toBeUndefined();
  });

  it("ignores an expired token", () => {
    setToken(tokenFor({ exp: Math.floor(Date.now() / 1000) - 60 }));
    expect(getToken()).toBeUndefined();
  });

  it("treats unavailable storage as logged out", () => {
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new Error("blocked");
    });
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new Error("blocked");
    });
    vi.spyOn(Storage.prototype, "removeItem").mockImplementation(() => {
      throw new Error("blocked");
    });

    expect(() => setToken("abc")).not.toThrow();
    expect(() => saveSession({ accessToken: "abc", expiresIn: 60, staff: owner })).not.toThrow();
    expect(() => clearSession()).not.toThrow();
    expect(getToken()).toBeUndefined();
    expect(getStaff()).toBeUndefined();
  });
});

describe("sessions", () => {
  afterEach(() => sessionStorage.clear());

  it("keeps the staff profile while the token is valid", () => {
    const exp = Math.floor(Date.now() / 1000) + 3600;
    saveSession({ accessToken: tokenFor({ exp }), expiresIn: 3600, staff: owner });

    expect(getStaff()).toEqual(owner);

    clearSession();
    expect(getStaff()).toBeUndefined();
    expect(getToken()).toBeUndefined();
  });

  it("forgets the profile when there is a token but no stored profile", () => {
    setToken(tokenFor({ sub: "staff:1" }));
    expect(getStaff()).toBeUndefined();
  });
});

describe("isExpired", () => {
  it("compares exp with the clock", () => {
    const token = tokenFor({ exp: 1_000 });
    expect(isExpired(token, 999_000)).toBe(false);
    expect(isExpired(token, 1_000_000)).toBe(true);
    expect(isExpired(tokenFor({ sub: "x" }))).toBe(false);
    expect(isExpired("garbage")).toBe(true);
  });
});

describe("rolesFromToken", () => {
  it("reads the roles claim", () => {
    expect(rolesFromToken(tokenFor({ sub: "1", roles: ["ADMIN"] }))).toEqual(["ADMIN"]);
  });

  it("returns no roles when the claim is missing or the token is malformed", () => {
    expect(rolesFromToken(tokenFor({ sub: "1" }))).toEqual([]);
    expect(rolesFromToken("garbage")).toEqual([]);
  });
});
