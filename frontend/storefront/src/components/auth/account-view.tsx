"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useId, useState, useSyncExternalStore } from "react";
import { Button, buttonClasses } from "@vaabhi/ui";
import { AuthError, auth, displayName, sessionStore, signOut, syncSession, toSession } from "@/lib/session";

const field = "flex flex-col gap-1.5";
const label = "text-xs uppercase tracking-[0.08em] text-text-muted";
const input =
  "w-full rounded-sm border border-border-strong bg-bg-surface px-3.5 py-3 text-base text-text-primary outline-none focus:border-brand-primary disabled:bg-bg-muted";

/**
 * The profile page: the customer's name (editable), the email and mobile they sign in with (shown, not editable:
 * they come from Google and from the mobile OTP), links to orders and the wishlist, and log out.
 * Guests get a login prompt.
 */
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
          {checked ? "Log in to see your profile, orders and saved details." : "Checking your account…"}
        </p>
        {checked && (
          <Link href="/login?next=%2Faccount" className={buttonClasses("primary")}>
            Login
          </Link>
        )}
      </div>
    );
  }

  return (
    <div className="mx-auto flex max-w-md flex-col gap-6">
      <p className="text-center text-text-muted">Hi, {displayName(session)}</p>
      <ProfileForm session={session} />
      <div className="flex flex-col gap-3">
        <Link href="/account/orders" className={buttonClasses("secondary")}>
          Your orders
        </Link>
        <Link href="/wishlist" className={buttonClasses("secondary")}>
          Your wishlist
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

function ProfileForm({ session }: Readonly<{ session: NonNullable<ReturnType<typeof sessionStore.read>> }>) {
  const id = useId();
  const [name, setName] = useState(session.name ?? "");
  const [state, setState] = useState<{ status: "idle" | "saving" | "saved" } | { status: "error"; message: string }>({
    status: "idle",
  });
  const changed = name.trim() !== (session.name ?? "");

  return (
    <form
      className="flex flex-col gap-4 rounded-sm border border-border-default bg-bg-surface p-4"
      onSubmit={async (event) => {
        event.preventDefault();
        if (!name.trim()) return setState({ status: "error", message: "Enter your name." });
        setState({ status: "saving" });
        try {
          const { customer } = await auth.updateName(name.trim());
          sessionStore.write(toSession(customer));
          setState({ status: "saved" });
        } catch (error) {
          setState({
            status: "error",
            message:
              error instanceof AuthError ? (error.forField("name") ?? error.message) : "Could not save. Try again.",
          });
        }
      }}
    >
      <h2 className="font-serif text-[22px] font-semibold text-text-primary">Your details</h2>
      <div className={field}>
        <label htmlFor={`${id}-name`} className={label}>
          Name
        </label>
        <input
          id={`${id}-name`}
          name="name"
          autoComplete="name"
          maxLength={80}
          value={name}
          onChange={(event) => {
            setName(event.target.value);
            if (state.status !== "idle") setState({ status: "idle" });
          }}
          placeholder="Your name"
          className={input}
        />
      </div>
      <div className={field}>
        <label htmlFor={`${id}-phone`} className={label}>
          Mobile
        </label>
        <input
          id={`${id}-phone`}
          value={session.phone ? `+91 ${session.phone.slice(3)}` : "Not added"}
          disabled
          className={input}
        />
        <p className="text-xs text-text-muted">
          {session.phone ? "Your login number, verified by OTP." : "Log in with your mobile number to add it."}
        </p>
      </div>
      <div className={field}>
        <label htmlFor={`${id}-email`} className={label}>
          Email
        </label>
        <input id={`${id}-email`} value={session.email ?? "Not added"} disabled className={input} />
        <p className="text-xs text-text-muted">
          {session.email ? "From your Google login." : "Log in with Google to add your email."}
        </p>
      </div>
      <div className="flex items-center gap-3">
        <Button type="submit" variant="primary" disabled={!changed || state.status === "saving"}>
          {state.status === "saving" ? "Saving…" : "Save"}
        </Button>
        <p role="status" className="text-sm">
          {state.status === "saved" && <span className="text-feedback-success">Saved</span>}
          {state.status === "error" && <span className="text-feedback-error">{state.message}</span>}
        </p>
      </div>
    </form>
  );
}
