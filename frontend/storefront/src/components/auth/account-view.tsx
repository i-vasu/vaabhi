"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState, useSyncExternalStore } from "react";
import { Button, buttonClasses } from "@vaabhi/ui";
import { displayName, sessionStore, signOut, syncSession } from "@/lib/session";

/** The account page: the customer's details and log out, or a sign-in prompt for guests. */
export function AccountView() {
  const router = useRouter();
  const session = useSyncExternalStore(sessionStore.subscribe, sessionStore.read, sessionStore.serverValue);
  const [checked, setChecked] = useState(false);

  useEffect(() => {
    void syncSession().then(() => setChecked(true));
  }, []);

  if (!session) {
    return (
      <div className="text-center">
        <p className="mx-auto mb-6 mt-2 max-w-[40ch] text-text-muted">
          {checked ? "Sign in to see your orders and saved details." : "Checking your account…"}
        </p>
        {checked && (
          <Link href="/signup?next=%2Faccount" className={buttonClasses("primary")}>
            Sign in / Sign up
          </Link>
        )}
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-md">
      <p className="mb-6 text-center text-text-muted">Hi, {displayName(session)}</p>
      <dl className="divide-y divide-border-default rounded-sm border border-border-default bg-bg-surface">
        {[
          ["Name", session.name ?? "Not added"],
          ["Mobile", session.phone ? `+91 ${session.phone.slice(3)}` : "Not added"],
          ["Email", session.email ?? "Not added"],
        ].map(([label, value]) => (
          <div key={label} className="flex justify-between gap-4 px-4 py-3 text-sm">
            <dt className="text-text-muted">{label}</dt>
            <dd className="truncate text-right">{value}</dd>
          </div>
        ))}
      </dl>
      <div className="mt-6 flex flex-col gap-3">
        <Link href="/account/orders" className={buttonClasses("secondary")}>
          Your orders
        </Link>
        <Button
          variant="primary"
          onClick={() => {
            void signOut().then(() => router.replace("/"));
          }}
        >
          Log out
        </Button>
      </div>
    </div>
  );
}
