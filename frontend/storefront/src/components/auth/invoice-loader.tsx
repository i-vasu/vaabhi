"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import type { StoreInvoice } from "@vaabhi/api-client";
import { buttonClasses } from "@vaabhi/ui";
import { InvoiceView } from "./invoice-view";
import { PrintButton } from "@/app/account/orders/[number]/invoice/print-button";

type Load =
  | { state: "loading" }
  | { state: "signedOut" }
  | { state: "missing" }
  | { state: "error" }
  | { state: "ready"; invoice: StoreInvoice };

/** Loads one invoice through /api/auth (where the session cookie goes) and shows it. */
export function InvoiceLoader({ number }: Readonly<{ number: string }>) {
  const [load, setLoad] = useState<Load>({ state: "loading" });

  useEffect(() => {
    fetch(`/api/auth/orders/${encodeURIComponent(number)}/invoice`)
      .then(async (response) => {
        if (response.status === 401) return setLoad({ state: "signedOut" });
        if (response.status === 404) return setLoad({ state: "missing" });
        if (!response.ok) return setLoad({ state: "error" });
        setLoad({ state: "ready", invoice: (await response.json()) as StoreInvoice });
      })
      .catch(() => setLoad({ state: "error" }));
  }, [number]);

  if (load.state === "loading") return <p className="text-text-muted">Loading…</p>;
  if (load.state === "signedOut") {
    return (
      <Link
        href={`/login?next=${encodeURIComponent(`/account/orders/${number}/invoice`)}`}
        className={buttonClasses("primary")}
      >
        Login to see your invoice
      </Link>
    );
  }
  if (load.state === "missing") return <p className="text-text-muted">We couldn&apos;t find this invoice.</p>;
  if (load.state === "error")
    return <p className="text-feedback-error">Your invoice could not be loaded. Please try again.</p>;
  return (
    <>
      <InvoiceView invoice={load.invoice} />
      <div className="mt-8">
        <PrintButton />
      </div>
    </>
  );
}
