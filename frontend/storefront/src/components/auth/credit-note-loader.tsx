"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import type { StoreCreditNote } from "@vaabhi/api-client";
import { buttonClasses } from "@vaabhi/ui";
import { CreditNoteView } from "./credit-note-view";
import { PrintButton } from "@/app/account/orders/[number]/invoice/print-button";

type Load =
  | { state: "loading" }
  | { state: "signedOut" }
  | { state: "missing" }
  | { state: "error" }
  | { state: "ready"; note: StoreCreditNote };

/** Loads an order's credit notes through /api/auth (where the session cookie goes) and shows the one asked for. */
export function CreditNoteLoader({ number, noteNumber }: Readonly<{ number: string; noteNumber: string }>) {
  const [load, setLoad] = useState<Load>({ state: "loading" });

  useEffect(() => {
    let cancelled = false;
    const set = (next: Load) => {
      if (!cancelled) setLoad(next);
    };
    fetch(`/api/auth/orders/${encodeURIComponent(number)}/credit-notes`)
      .then(async (response) => {
        if (response.status === 401) return set({ state: "signedOut" });
        if (response.status === 404) return set({ state: "missing" });
        if (!response.ok) return set({ state: "error" });
        const notes = (await response.json()) as StoreCreditNote[];
        const note = notes.find((n) => n.number === noteNumber);
        set(note ? { state: "ready", note } : { state: "missing" });
      })
      .catch(() => set({ state: "error" }));
    return () => {
      cancelled = true;
    };
  }, [number, noteNumber]);

  if (load.state === "loading") return <p className="text-text-muted">Loading…</p>;
  if (load.state === "signedOut") {
    return (
      <Link
        href={`/login?next=${encodeURIComponent(`/account/orders/${encodeURIComponent(number)}/credit-notes/${encodeURIComponent(noteNumber)}`)}`}
        className={buttonClasses("primary")}
      >
        Login to see your credit note
      </Link>
    );
  }
  if (load.state === "missing") return <p className="text-text-muted">This credit note isn&apos;t available.</p>;
  if (load.state === "error")
    return <p className="text-feedback-error">Your credit note could not be loaded. Please try again.</p>;
  return (
    <>
      <CreditNoteView note={load.note} />
      <div className="mt-8">
        <PrintButton label="Print credit note" />
      </div>
    </>
  );
}
