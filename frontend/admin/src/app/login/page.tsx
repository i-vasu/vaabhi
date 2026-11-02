"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { ApiError } from "@vaabhi/api-client";
import { Button } from "@vaabhi/ui";
import { TextField } from "@/components/ui";
import { api } from "@/lib/api";
import { saveSession } from "@/lib/auth";

/** Where to go after signing in: the page that sent us here, but only paths inside this app. */
export function nextPath(search: string): string {
  const next = new URLSearchParams(search).get("next");
  return next && next.startsWith("/") && !next.startsWith("//") && !next.startsWith("/login") ? next : "/";
}

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function signIn() {
    setBusy(true);
    setError(null);
    try {
      saveSession(await api.auth.staffLogin({ email: email.trim(), password }));
      router.replace(nextPath(window.location.search));
    } catch (e) {
      setError(
        e instanceof ApiError
          ? (e.detail ?? (e.status === 400 ? "Enter your email and password." : "Could not sign in."))
          : "Could not reach the server. Check your connection and try again.",
      );
      setBusy(false);
    }
  }

  return (
    <main className="grid min-h-screen place-items-center px-4">
      <section className="w-full max-w-sm rounded-3xl border border-line bg-panel p-8">
        <p className="text-sm font-semibold tracking-[0.3em] text-ink-soft">VAABHI</p>
        <h1 className="mt-2 text-2xl font-semibold">Sign in to the shop</h1>
        <p className="mt-1 text-sm text-ink-soft">Use the email and password the owner gave you.</p>
        <form
          className="mt-8 flex flex-col gap-4"
          onSubmit={(event) => {
            event.preventDefault();
            void signIn();
          }}
        >
          <TextField
            label="Email"
            type="email"
            autoComplete="username"
            required
            value={email}
            onChange={(event) => setEmail(event.target.value)}
          />
          <TextField
            label="Password"
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
          {error && (
            <p role="alert" className="rounded-xl bg-bad-soft px-4 py-3 text-sm text-bad">
              {error}
            </p>
          )}
          <Button type="submit" disabled={busy} className="mt-2 rounded-xl normal-case tracking-normal">
            {busy ? "Signing in…" : "Sign in"}
          </Button>
        </form>
      </section>
    </main>
  );
}
