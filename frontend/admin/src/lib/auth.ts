import type { StaffProfile, StaffSession } from "@vaabhi/api-client";

const TOKEN_KEY = "vaabhi.admin.token";
const STAFF_KEY = "vaabhi.admin.staff";

/** Reads the token's payload without verifying it; the API is the authority. */
function claimsOf(token: string): Record<string, unknown> {
  try {
    return JSON.parse(atob(token.split(".")[1]!.replace(/-/g, "+").replace(/_/g, "/")));
  } catch {
    return {};
  }
}

/** True once the token's `exp` has passed (or it cannot be read). Tokens without `exp` never expire here. */
export function isExpired(token: string, now: number = Date.now()): boolean {
  const claims = claimsOf(token);
  if (Object.keys(claims).length === 0) return true;
  return typeof claims.exp === "number" && claims.exp * 1000 <= now;
}

/** The access token for API calls, if signed in and not expired. Kept per browser tab (sessionStorage). */
export function getToken(): string | undefined {
  try {
    const token = sessionStorage.getItem(TOKEN_KEY) ?? undefined;
    return token && !isExpired(token) ? token : undefined;
  } catch {
    return undefined;
  }
}

export function setToken(token: string | null): void {
  try {
    if (token) sessionStorage.setItem(TOKEN_KEY, token);
    else sessionStorage.removeItem(TOKEN_KEY);
  } catch {
    // storage unavailable (private mode); user simply stays logged out
  }
}

/** Stores a fresh sign-in: the token for the API and the profile for the header. */
export function saveSession(session: StaffSession): void {
  setToken(session.accessToken);
  try {
    sessionStorage.setItem(STAFF_KEY, JSON.stringify(session.staff));
  } catch {
    // see setToken
  }
}

/** The signed-in staff member, or undefined when signed out or the sign-in expired. */
export function getStaff(): StaffProfile | undefined {
  if (!getToken()) return undefined;
  try {
    const raw = sessionStorage.getItem(STAFF_KEY);
    return raw ? (JSON.parse(raw) as StaffProfile) : undefined;
  } catch {
    return undefined;
  }
}

export function clearSession(): void {
  setToken(null);
  try {
    sessionStorage.removeItem(STAFF_KEY);
  } catch {
    // see setToken
  }
}

/** Reads the `roles` claim without verifying it; the API is the authority. */
export function rolesFromToken(token: string): string[] {
  const roles = claimsOf(token).roles;
  return Array.isArray(roles) ? roles : [];
}
