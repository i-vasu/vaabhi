"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import type { TrackedOrder } from "@vaabhi/api-client";
import { Button } from "@vaabhi/ui";
import { formatPrice } from "@/lib/format";

const STATUS: Record<TrackedOrder["status"], string> = {
  PENDING_PAYMENT: "Payment pending",
  FAILED: "Payment failed",
  PLACED: "Order placed",
  SHIPPED: "Shipped",
  DELIVERED: "Delivered",
  CANCELLED: "Cancelled",
};

const day = new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", year: "numeric" });
const field =
  "w-full rounded-sm border border-border-strong bg-bg-surface px-3 py-2 text-base outline-none focus:border-brand-primary";

/** Order number + phone → the order's status, shipping and items. Signed-in shoppers also have /account/orders. */
export function TrackOrder({ initialNumber = "" }: Readonly<{ initialNumber?: string }>) {
  const [number, setNumber] = useState(initialNumber);
  const [phone, setPhone] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [order, setOrder] = useState<TrackedOrder | null>(null);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    setOrder(null);
    try {
      const response = await fetch("/api/track", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ number, phone }),
      });
      const json = (await response.json().catch(() => ({}))) as TrackedOrder & { detail?: string };
      if (!response.ok) throw new Error(json.detail ?? "Something went wrong. Please try again.");
      setOrder(json);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="text-left">
      <form onSubmit={submit} className="flex flex-col gap-4">
        <label className="flex flex-col gap-1 text-sm">
          Order number
          <input
            className={field}
            value={number}
            onChange={(e) => setNumber(e.target.value)}
            placeholder="WEB-1042"
            autoComplete="off"
            required
            maxLength={32}
          />
        </label>
        <label className="flex flex-col gap-1 text-sm">
          Mobile number used for the order
          <input
            className={field}
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            type="tel"
            inputMode="numeric"
            autoComplete="tel-national"
            required
            maxLength={20}
          />
        </label>
        <Button type="submit" disabled={busy}>
          {busy ? "Looking…" : "Track order"}
        </Button>
        <p role="alert" aria-live="polite" className="text-sm text-feedback-error">
          {error}
        </p>
      </form>

      {order && <OrderStatus order={order} />}

      <p className="mt-8 text-center text-sm text-text-muted">
        Ordered while logged in?{" "}
        <Link href="/account/orders" className="underline">
          See all your orders
        </Link>
      </p>
    </div>
  );
}

function OrderStatus({ order }: Readonly<{ order: TrackedOrder }>) {
  const steps: [string, string | null | undefined][] = [
    ["Placed", order.createdAt],
    ["Shipped", order.shippedAt],
    ["Delivered", order.deliveredAt],
  ];
  return (
    <section aria-labelledby="tracked-order" className="mt-6 rounded-md border border-border-default p-4">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 id="tracked-order" className="font-semibold">
          {order.number}
        </h2>
        <span className="text-sm text-text-muted">{day.format(new Date(order.createdAt))}</span>
      </div>
      <p className="mt-1 text-sm">
        {STATUS[order.status]} · <span className="tabular-nums">{formatPrice(order.totalRupees)}</span>
        {order.paymentMethod === "COD" && order.status !== "DELIVERED" && order.status !== "CANCELLED"
          ? " · pay on delivery"
          : ""}
        {` · to ${order.city}`}
      </p>

      {order.status === "CANCELLED" ? (
        <p className="mt-3 text-sm text-text-secondary">
          Cancelled{order.cancelledAt ? ` on ${day.format(new Date(order.cancelledAt))}` : ""}.
        </p>
      ) : (
        order.status !== "PENDING_PAYMENT" &&
        order.status !== "FAILED" && (
          <ol className="mt-3 flex gap-4 text-sm">
            {steps.map(([label, at]) => (
              <li key={label} className={at ? "text-text-primary" : "text-text-muted"}>
                <span aria-hidden="true">{at ? "● " : "○ "}</span>
                {label}
                {at && <span className="block text-xs text-text-muted">{day.format(new Date(at))}</span>}
              </li>
            ))}
          </ol>
        )
      )}

      {order.status === "SHIPPED" && (order.courier || order.trackingNumber) && (
        <p className="mt-3 text-sm text-text-secondary">
          Sent with {order.courier ?? "our delivery partner"}
          {order.trackingNumber && `, tracking ${order.trackingNumber}`}
          {order.trackingStatus && ` · ${order.trackingStatus.toLowerCase()}`}
          {order.trackingUrl && (
            <>
              {" · "}
              <a href={order.trackingUrl} target="_blank" rel="noreferrer" className="underline">
                Track your parcel
              </a>
            </>
          )}
        </p>
      )}

      <ul className="mt-3 text-sm text-text-secondary">
        {order.lines.map((line, i) => (
          <li key={`${line.productSlug}-${i}`}>
            <Link href={`/p/${line.productSlug}`} className="hover:underline">
              {line.name}
            </Link>
            {[line.colourName, line.size].filter(Boolean).length > 0 &&
              ` · ${[line.colourName, line.size].filter(Boolean).join(", ")}`}{" "}
            × {line.quantity}
          </li>
        ))}
      </ul>
    </section>
  );
}
