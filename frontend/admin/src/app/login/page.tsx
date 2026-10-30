"use client";

import { useState } from "react";
import { Button } from "@vaabhi/ui";
import { setToken } from "@/lib/auth";

/** Placeholder until the auth module issues tokens: paste a JWT to call admin APIs. */
export default function LoginPage() {
  const [token, setTokenInput] = useState("");
  const [saved, setSaved] = useState(false);

  return (
    <section className="max-w-md">
      <h1 className="text-2xl font-semibold">Sign in</h1>
      <p className="mt-2 text-sm text-neutral-600">Email/password login arrives with the auth module.</p>
      <form
        className="mt-6 flex flex-col gap-3"
        onSubmit={(event) => {
          event.preventDefault();
          setToken(token.trim() || null);
          setSaved(true);
        }}
      >
        <label className="text-sm font-medium" htmlFor="token">
          Access token
        </label>
        <textarea
          id="token"
          className="h-28 rounded-md border border-neutral-300 p-2 font-mono text-xs"
          value={token}
          onChange={(event) => setTokenInput(event.target.value)}
        />
        <Button type="submit">Save token</Button>
        {saved && <p className="text-sm text-green-700">Token saved for this browser session.</p>}
      </form>
    </section>
  );
}
