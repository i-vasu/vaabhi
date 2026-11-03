"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import type { WebOrder } from "@vaabhi/api-client";
import { buttonClasses } from "@vaabhi/ui";
import { formatPrice } from "@/lib/format";

type Load = { state: "loading" } | { state: "signedOut" } | { state: "error" } | { state: "ready"; orders: WebOrder[] };

const STATUS: Record<WebOrder["status"], string> = {
  PAID: "Paid",
  PENDING_PAYMENT: "Payment pending",
  FAILED: "Payment failed",
};

const day = new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", year: "numeric" });

/** The signed-in customer's online orders, newest first (read through /api/auth, where the session cookie goes). */
export function OrdersView() {
  const [load, setLoad] = useState<Load>({ state: "loading" });

  useEffect(() => {
    fetch("/api/auth/orders")
      .then(async (response) => {
        if (response.status === 401) return setLoad({ state: "signedOut" });
        if (!response.ok) return setLoad({ state: "error" });
        const { orders } = (await response.json()) as { orders: WebOrder[] };
        setLoad({ state: "ready", orders });
      })
      .catch(() => setLoad({ state: "error" }));
  }, []);

  if (load.state === "loading") return <p className="text-text-muted">Loading…</p>;
  if (load.state === "signedOut") {
    return (
      <Link href="/signup?next=/account/orders" className={buttonClasses("primary")}>
        Sign in to see your orders
      </Link>
    );
  }
  if (load.state === "error")
    return <p className="text-feedback-error">Your orders could not be loaded. Please try again.</p>;
  if (load.orders.length === 0) {
    return (
      <>
        <p className="mb-6 text-text-muted">No orders yet.</p>
        <Link href="/" className={buttonClasses("primary")}>
          Start shopping
        </Link>
      </>
    );
  }
  return (
    <ul className="flex flex-col gap-4 text-left">
      {load.orders.map((order) => (
        <li key={order.number} className="rounded-md border border-border-default p-4">
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <strong>{order.number}</strong>
            <span className="text-sm text-text-muted">{day.format(new Date(order.createdAt))}</span>
          </div>
          <p className="mt-1 text-sm">
            {STATUS[order.status]} · <span className="tabular-nums">{formatPrice(order.totalRupees)}</span>
          </p>
          <ul className="mt-2 text-sm text-text-secondary">
            {order.lines.map((line) => (
              <li key={`${line.sku}|${line.addonName ?? ""}`}>
                <Link href={`/p/${line.productSlug}`} className="hover:underline">
                  {line.name}
                </Link>
                {[line.colourName, line.size].filter(Boolean).length > 0 &&
                  ` · ${[line.colourName, line.size].filter(Boolean).join(", ")}`}
                {line.addonName && ` · ${line.addonName}`} × {line.quantity}
              </li>
            ))}
          </ul>
        </li>
      ))}
    </ul>
  );
}
