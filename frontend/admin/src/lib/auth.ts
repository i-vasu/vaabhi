const TOKEN_KEY = "vaabhi.admin.token";

export function getToken(): string | undefined {
  try {
    return sessionStorage.getItem(TOKEN_KEY) ?? undefined;
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

/** Reads the `roles` claim without verifying it; the API is the authority. */
export function rolesFromToken(token: string): string[] {
  try {
    const payload = JSON.parse(atob(token.split(".")[1]!.replace(/-/g, "+").replace(/_/g, "/")));
    return Array.isArray(payload.roles) ? payload.roles : [];
  } catch {
    return [];
  }
}
