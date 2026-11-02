import { cookies } from "next/headers";
import { ApiError, type AuthSession, type CustomerProfile } from "@vaabhi/api-client";
import { api } from "./api";

/**
 * Customer sessions for the storefront's own /api/auth routes. Both tokens live in httpOnly cookies scoped to
 * /api/auth, so page scripts can never read them and they are sent only to these routes. The refresh cookie
 * lasts as long as the backend's session (60 days, renewed on every visit): that is "stay signed in".
 */
export const ACCESS_COOKIE = "vb_at";
export const REFRESH_COOKIE = "vb_rt";

const base = () =>
  ({
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    path: "/api/auth",
  }) as const;

export async function saveSession(session: AuthSession): Promise<void> {
  const jar = await cookies();
  // Expire the access cookie a little early so a token is never sent in its last seconds.
  jar.set(ACCESS_COOKIE, session.accessToken, {
    ...base(),
    maxAge: Math.max(session.accessTokenExpiresIn - 30, 0),
  });
  jar.set(REFRESH_COOKIE, session.refreshToken, { ...base(), maxAge: session.refreshTokenExpiresIn });
}

export async function clearSession(): Promise<void> {
  const jar = await cookies();
  jar.delete({ name: ACCESS_COOKIE, path: "/api/auth" });
  jar.delete({ name: REFRESH_COOKIE, path: "/api/auth" });
}

const unauthorized = (error: unknown) => error instanceof ApiError && error.status === 401;

/** A valid access token, refreshing the session if the old one expired; null when signed out. */
async function accessToken(): Promise<string | null> {
  const jar = await cookies();
  const access = jar.get(ACCESS_COOKIE)?.value;
  if (access) return access;
  const refresh = jar.get(REFRESH_COOKIE)?.value;
  if (!refresh) return null;
  try {
    const session = await api.auth.refresh(refresh);
    await saveSession(session);
    return session.accessToken;
  } catch (error) {
    if (!unauthorized(error)) throw error;
    await clearSession();
    return null;
  }
}

/**
 * Runs a call with the customer's access token. If the token was revoked or expired early, refreshes once and
 * retries. Returns null when the customer is signed out.
 */
export async function withCustomer<T>(call: (accessToken: string) => Promise<T>): Promise<T | null> {
  const token = await accessToken();
  if (!token) return null;
  try {
    return await call(token);
  } catch (error) {
    if (!unauthorized(error)) throw error;
    const jar = await cookies();
    jar.delete({ name: ACCESS_COOKIE, path: "/api/auth" });
    const retry = await accessToken();
    if (!retry) return null;
    try {
      return await call(retry);
    } catch (again) {
      if (!unauthorized(again)) throw again;
      await clearSession();
      return null;
    }
  }
}

export const currentCustomer = (): Promise<CustomerProfile | null> =>
  withCustomer((token) => api.customer.getProfile(token));

export async function endSession(): Promise<void> {
  const refresh = (await cookies()).get(REFRESH_COOKIE)?.value;
  if (refresh) {
    // Signing out locally must work even if the API is unreachable.
    await api.auth.logout(refresh).catch(() => undefined);
  }
  await clearSession();
}

/**
 * Refuses cross-site form posts. SameSite=Lax cookies already block them in modern browsers; this also covers
 * older ones. Requests without an Origin header (same-origin GETs, some older browsers) are allowed.
 */
export function crossSite(request: Request): boolean {
  const origin = request.headers.get("origin");
  if (!origin) return false;
  const host = request.headers.get("x-forwarded-host") ?? request.headers.get("host");
  try {
    return new URL(origin).host !== host;
  } catch {
    return true;
  }
}

/** The API's problem (status, message, field errors) for the browser; network failures become 502. */
export function problemResponse(error: unknown, fallback = "Something went wrong. Please try again."): Response {
  if (error instanceof ApiError) {
    const status = error.status >= 400 && error.status < 600 ? error.status : 502;
    return Response.json(
      {
        detail: status >= 500 && status !== 503 && !error.detail ? fallback : (error.detail ?? fallback),
        errors: error.errors,
        retryAfterSeconds: error.retryAfterSeconds,
      },
      { status },
    );
  }
  return Response.json({ detail: fallback, errors: [] }, { status: 502 });
}

export const forbidden = () => Response.json({ detail: "Cross-site request refused", errors: [] }, { status: 403 });

/** Reads a JSON body, or null if it is not JSON. */
export async function jsonBody<T>(request: Request): Promise<T | null> {
  try {
    return (await request.json()) as T;
  } catch {
    return null;
  }
}
