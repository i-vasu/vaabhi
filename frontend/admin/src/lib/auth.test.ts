import { describe, expect, it } from "vitest";
import { rolesFromToken } from "./auth";

const encode = (value: object) => btoa(JSON.stringify(value)).replace(/=+$/, "");

describe("rolesFromToken", () => {
  it("reads the roles claim", () => {
    const token = `${encode({ alg: "HS256" })}.${encode({ sub: "1", roles: ["ADMIN"] })}.sig`;
    expect(rolesFromToken(token)).toEqual(["ADMIN"]);
  });

  it("returns no roles for malformed tokens", () => {
    expect(rolesFromToken("garbage")).toEqual([]);
  });
});
