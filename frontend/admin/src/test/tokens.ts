import type { StaffProfile } from "@vaabhi/api-client";

const encode = (value: object) => btoa(JSON.stringify(value)).replace(/=+$/, "");

/** An unsigned JWT-shaped token with the given payload (the admin app never verifies signatures). */
export const tokenFor = (payload: object) => `${encode({ alg: "HS256" })}.${encode(payload)}.sig`;

export const owner: StaffProfile = { id: 1, name: "Asha Rao", email: "asha@vaabhi.local", role: "OWNER" };

/** A token valid for another hour. */
export const validToken = () => tokenFor({ sub: "staff:1", exp: Math.floor(Date.now() / 1000) + 3600 });
