import type { CustomerProfile, FieldError, OtpSent } from "@vaabhi/api-client";
import { localStore } from "./local-store";

/**
 * What the header shows about the signed-in customer, cached on this device so it appears instantly. The real
 * session is the httpOnly cookie; {@link syncSession} reconciles the two on every page load.
 */
export type Session = { name: string | null; phone: string | null; email: string | null } | null;

export const sessionStore = localStore<Session>("session.v1", null);

export const toSession = (customer: CustomerProfile): NonNullable<Session> => ({
  name: customer.name ?? null,
  phone: customer.phone ?? null,
  email: customer.email ?? null,
});

/** "Priya", else the email's name part, else the last digits of the phone number. */
export function displayName(session: NonNullable<Session>): string {
  if (session.name) return session.name;
  if (session.email) return session.email.split("@")[0]!;
  if (session.phone) return `••${session.phone.slice(-4)}`;
  return "there";
}

/** A refused auth call, with the API's message and per-field errors. */
export class AuthError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly errors: FieldError[] = [],
    readonly retryAfterSeconds?: number,
  ) {
    super(message);
  }

  /** The message for one input, if the API named it. */
  forField(field: string): string | undefined {
    return this.errors.find((e) => e.field === field)?.message;
  }
}

type SignedIn = { customer: CustomerProfile; newCustomer: boolean };

async function call<T>(path: string, method: "GET" | "POST" | "PUT", body?: unknown, fetcher = fetch): Promise<T> {
  let response: Response;
  try {
    response = await fetcher(path, {
      method,
      headers: body === undefined ? undefined : { "Content-Type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new AuthError(0, "You seem to be offline. Check your connection and try again.");
  }
  if (response.status === 204) return undefined as T;
  const json = (await response.json().catch(() => ({}))) as {
    detail?: string;
    errors?: FieldError[];
    retryAfterSeconds?: number;
  };
  if (!response.ok) {
    throw new AuthError(
      response.status,
      json.detail ?? "Something went wrong. Please try again.",
      json.errors ?? [],
      json.retryAfterSeconds,
    );
  }
  return json as T;
}

export const auth = {
  requestOtp: (phone: string, fetcher?: typeof fetch) =>
    call<OtpSent>("/api/auth/otp/request", "POST", { phone }, fetcher),
  verifyOtp: (phone: string, code: string, fetcher?: typeof fetch) =>
    call<SignedIn>("/api/auth/otp/verify", "POST", { phone, code }, fetcher),
  google: (credential: string, fetcher?: typeof fetch) =>
    call<SignedIn>("/api/auth/google", "POST", { credential }, fetcher),
  updateName: (name: string, fetcher?: typeof fetch) =>
    call<{ customer: CustomerProfile }>("/api/auth/profile", "PUT", { name }, fetcher),
};

/** Asks the server who is signed in (renewing the session) and updates the cached copy. Offline: no change. */
export async function syncSession(fetcher: typeof fetch = fetch): Promise<Session> {
  try {
    const { customer } = await call<{ customer: CustomerProfile | null }>("/api/auth/session", "GET", undefined, fetcher);
    const session = customer ? toSession(customer) : null;
    sessionStore.write(session);
    return session;
  } catch {
    return sessionStore.read();
  }
}

/** Ends the session on the server and forgets it on this device, even if the server cannot be reached. */
export async function signOut(fetcher: typeof fetch = fetch): Promise<void> {
  await call("/api/auth/logout", "POST", undefined, fetcher).catch(() => undefined);
  sessionStore.write(null);
}

/** Only same-site paths, so a crafted ?next= link cannot send the customer to another site after sign-in. */
export function safeNext(next: string | null | undefined): string {
  return next && next.startsWith("/") && !next.startsWith("//") && !next.startsWith("/\\") ? next : "/account";
}
