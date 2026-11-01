import { localStore } from "./local-store";

/** The signed-in customer, or null for a guest. Filled in by the login flow once customer auth exists. */
export type Session = { name: string } | null;

export const sessionStore = localStore<Session>("session.v1", null);

export function signOut(): void {
  sessionStore.write(null);
}
