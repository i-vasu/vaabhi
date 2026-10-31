import { afterEach, describe, expect, it, vi } from "vitest";
import { getToken, rolesFromToken, setToken } from "./auth";

const encode = (value: object) => btoa(JSON.stringify(value)).replace(/=+$/, "");

describe("token storage", () => {
  afterEach(() => {
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  it("stores, reads and clears the token", () => {
    expect(getToken()).toBeUndefined();
    setToken("abc");
    expect(getToken()).toBe("abc");
    setToken(null);
    expect(getToken()).toBeUndefined();
  });

  it("treats unavailable storage as logged out", () => {
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new Error("blocked");
    });
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new Error("blocked");
    });

    expect(() => setToken("abc")).not.toThrow();
    expect(getToken()).toBeUndefined();
  });
});

describe("rolesFromToken", () => {
  it("reads the roles claim", () => {
    const token = `${encode({ alg: "HS256" })}.${encode({ sub: "1", roles: ["ADMIN"] })}.sig`;
    expect(rolesFromToken(token)).toEqual(["ADMIN"]);
  });

  it("returns no roles when the claim is missing or the token is malformed", () => {
    expect(rolesFromToken(`${encode({})}.${encode({ sub: "1" })}.sig`)).toEqual([]);
    expect(rolesFromToken("garbage")).toEqual([]);
  });
});
