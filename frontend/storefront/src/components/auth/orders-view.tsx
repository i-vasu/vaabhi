"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import type { CheckoutStart, OrderReturn, WebOrder } from "@vaabhi/api-client";
import { Button, buttonClasses } from "@vaabhi/ui";
import { formatPrice } from "@/lib/format";
import { openRazorpay } from "@/lib/razorpay";

type Load = { state: "loading" } | { state: "signedOut" } | { state: "error" } | { state: "ready"; orders: WebOrder[] };

const STATUS: Record<WebOrder["status"], string> = {
  PENDING_PAYMENT: "Payment pending",
  FAILED: "Payment failed",
  PLACED: "Order placed",
  SHIPPED: "Shipped",
  DELIVERED: "Delivered",
  CANCELLED: "Cancelled",
};

const RETURN_STATUS: Record<OrderReturn["status"], string> = {
  REQUESTED: "Return requested, we will get back to you shortly",
  APPROVED: "Return approved",
  REJECTED: "Return not accepted",
  REFUNDED: "Return received",
};

const day = new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", year: "numeric" });
const field =
  "w-full rounded-sm border border-border-strong bg-bg-surface px-3 py-2 text-base outline-none focus:border-brand-primary";

async function post<T>(path: string, body?: unknown): Promise<T> {
  const response = await fetch(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const json = (await response.json().catch(() => ({}))) as T & { detail?: string };
  if (!response.ok) {
    throw Object.assign(new Error(json.detail ?? "Something went wrong. Please try again."), {
      status: response.status,
    });
  }
  return json;
}

/** The signed-in customer's online orders, newest first (read through /api/auth, where the session cookie goes). */
export function OrdersView() {
  const [load, setLoad] = useState<Load>({ state: "loading" });

  const reload = () =>
    fetch("/api/auth/orders")
      .then(async (response) => {
        if (response.status === 401) return setLoad({ state: "signedOut" });
        if (!response.ok) return setLoad({ state: "error" });
        const { orders } = (await response.json()) as { orders: WebOrder[] };
        setLoad({ state: "ready", orders });
      })
      .catch(() => setLoad({ state: "error" }));

  useEffect(() => {
    void reload();
  }, []);

  if (load.state === "loading") return <p className="text-text-muted">Loading…</p>;
  if (load.state === "signedOut") {
    return (
      <Link href="/login?next=/account/orders" className={buttonClasses("primary")}>
        Login to see your orders
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
  const replace = (changed: WebOrder) =>
    setLoad({ state: "ready", orders: load.orders.map((o) => (o.number === changed.number ? changed : o)) });
  return (
    <ul className="flex flex-col gap-4 text-left">
      {load.orders.map((order) => (
        <OrderItem key={order.number} order={order} onChange={replace} onPaid={() => void reload()} />
      ))}
    </ul>
  );
}

function OrderItem({
  order,
  onChange,
  onPaid,
}: Readonly<{ order: WebOrder; onChange: (order: WebOrder) => void; onPaid: () => void }>) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [returning, setReturning] = useState(false);

  async function act(action: () => Promise<void>) {
    setBusy(true);
    setError(null);
    try {
      await action();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  const payNow = () =>
    act(async () => {
      const started = await post<CheckoutStart>(`/api/auth/orders/${encodeURIComponent(order.number)}/pay`);
      await openRazorpay(
        started,
        (response) => {
          void post<WebOrder>(`/api/auth/checkout/${encodeURIComponent(order.number)}/payment`, {
            razorpayOrderId: response.razorpay_order_id,
            razorpayPaymentId: response.razorpay_payment_id,
            razorpaySignature: response.razorpay_signature,
          })
            .then(onPaid)
            .catch((e: Error) =>
              setError(`${e.message}. If money left your account, it is safe: your order will be confirmed shortly.`),
            );
        },
        () => setError("Payment was not completed. You can try again."),
      );
    });

  const cancel = () => {
    const reason = window.prompt(
      `Cancel order ${order.number}?${order.paymentMethod === "ONLINE" ? " Your payment will be refunded in full." : ""}\nReason (optional):`,
      "",
    );
    if (reason === null) return;
    void act(async () =>
      onChange(await post<WebOrder>(`/api/auth/orders/${encodeURIComponent(order.number)}/cancel`, { reason })),
    );
  };

  const unpaid = order.paymentMethod === "ONLINE" && (order.status === "PENDING_PAYMENT" || order.status === "FAILED");
  const canReturn =
    order.status === "DELIVERED" &&
    order.returnableUntil != null &&
    new Date(order.returnableUntil) > new Date() &&
    returnable(order).some((l) => l.left > 0);

  return (
    <li className="rounded-md border border-border-default p-4">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <strong>{order.number}</strong>
        <span className="text-sm text-text-muted">{day.format(new Date(order.createdAt))}</span>
      </div>
      <p className="mt-1 text-sm">
        {STATUS[order.status]} · <span className="tabular-nums">{formatPrice(order.totalRupees)}</span>
        {order.paymentMethod === "COD" && order.status !== "DELIVERED" && order.status !== "CANCELLED"
          ? " · pay on delivery"
          : ""}
      </p>
      {order.status === "SHIPPED" && (order.courier || order.trackingNumber) && (
        <p className="mt-1 text-sm text-text-secondary">
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
      {order.status === "CANCELLED" && order.refundedRupees != null && (
        <p className="mt-1 text-sm text-text-secondary">{refundText(order.refundedRupees, order.refundStatus)}</p>
      )}
      <ul className="mt-2 text-sm text-text-secondary">
        {order.lines.map((line) => (
          <li key={line.position}>
            <Link href={`/p/${line.productSlug}`} className="hover:underline">
              {line.name}
            </Link>
            {[line.colourName, line.size].filter(Boolean).length > 0 &&
              ` · ${[line.colourName, line.size].filter(Boolean).join(", ")}`}
            {line.addonName && ` · ${line.addonName}`} × {line.quantity}
          </li>
        ))}
      </ul>

      {order.returns.map((r) => (
        <div key={r.number} className="mt-3 rounded-sm bg-bg-muted p-3 text-sm">
          <p>
            <strong>{RETURN_STATUS[r.status]}</strong> · {r.lines.map((l) => `${l.name} × ${l.quantity}`).join(", ")}
          </p>
          {r.shopNote && <p className="text-text-secondary">{r.shopNote}</p>}
          {r.status === "APPROVED" && (
            <p className="text-text-secondary">
              {r.pickupAwb
                ? `Pickup booked with ${r.pickupCourier ?? "our courier"} (${r.pickupAwb})${r.pickupStatus ? ` · ${r.pickupStatus.toLowerCase()}` : ""}`
                : "We will arrange the pickup and let you know."}
            </p>
          )}
          {r.status === "REFUNDED" && r.refundRupees != null && Number(r.refundRupees) > 0 && (
            <p className="text-text-secondary">
              {r.refundMethod === "MANUAL"
                ? `${formatPrice(r.refundRupees)} refunded to you (reference ${r.refundReference ?? "—"})`
                : refundText(r.refundRupees, r.refundStatus)}
            </p>
          )}
        </div>
      ))}

      {returning && (
        <ReturnForm
          order={order}
          onCancel={() => setReturning(false)}
          onSent={(changed) => {
            setReturning(false);
            onChange(changed);
          }}
        />
      )}

      {!returning && (unpaid || order.status === "PLACED" || canReturn) && (
        <div className="mt-3 flex flex-wrap gap-2">
          {unpaid && (
            <Button disabled={busy} onClick={() => void payNow()}>
              {busy ? "Opening payment…" : `Pay ${formatPrice(order.totalRupees)} now`}
            </Button>
          )}
          {order.status === "PLACED" && (
            <Button variant="secondary" disabled={busy} onClick={cancel}>
              Cancel order
            </Button>
          )}
          {canReturn && order.returnableUntil && (
            <Button variant="secondary" disabled={busy} onClick={() => setReturning(true)}>
              Return items (until {day.format(new Date(order.returnableUntil))})
            </Button>
          )}
        </div>
      )}
      {error && (
        <p role="alert" className="mt-2 text-sm text-feedback-error">
          {error}
        </p>
      )}
    </li>
  );
}

/** Each line with how many can still be returned (not already in a return that was not rejected). */
function returnable(order: WebOrder) {
  return order.lines.map((line) => {
    const taken = order.returns
      .filter((r) => r.status !== "REJECTED")
      .flatMap((r) => r.lines)
      .filter((l) => l.position === line.position)
      .reduce((sum, l) => sum + l.quantity, 0);
    return { line, left: line.quantity - taken };
  });
}

function refundText(amount: number, status: WebOrder["refundStatus"]) {
  if (status === "PROCESSED") return `${formatPrice(amount)} refunded to your original payment method`;
  if (status === "FAILED") return `Your refund of ${formatPrice(amount)} did not go through. We will contact you.`;
  return `Refund of ${formatPrice(amount)} on its way to your original payment method (5–7 working days)`;
}

function ReturnForm({
  order,
  onCancel,
  onSent,
}: Readonly<{ order: WebOrder; onCancel: () => void; onSent: (order: WebOrder) => void }>) {
  const lines = returnable(order).filter((l) => l.left > 0);
  const [quantities, setQuantities] = useState<Record<number, number>>({});
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const items = Object.entries(quantities)
    .filter(([, quantity]) => quantity > 0)
    .map(([position, quantity]) => ({ position: Number(position), quantity }));

  async function send() {
    setBusy(true);
    setError(null);
    try {
      onSent(
        await post<WebOrder>(`/api/auth/orders/${encodeURIComponent(order.number)}/returns`, {
          reason: reason.trim(),
          items,
        }),
      );
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form
      className="mt-3 flex flex-col gap-3 rounded-sm border border-border-default p-3 text-sm"
      onSubmit={(e) => {
        e.preventDefault();
        void send();
      }}
    >
      <p className="font-medium">Which items would you like to return?</p>
      {lines.map(({ line, left }) => (
        <label key={line.position} className="flex items-center justify-between gap-3">
          <span>
            {line.name}
            {line.size && ` · ${line.size}`}
          </span>
          <select
            className="rounded-sm border border-border-strong bg-bg-surface px-2 py-1"
            value={quantities[line.position] ?? 0}
            onChange={(e) => setQuantities((q) => ({ ...q, [line.position]: Number(e.target.value) }))}
          >
            {Array.from({ length: left + 1 }, (_, n) => (
              <option key={n} value={n}>
                {n}
              </option>
            ))}
          </select>
        </label>
      ))}
      <label className="flex flex-col gap-1">
        Why are you returning them?
        <textarea
          className={field}
          rows={3}
          maxLength={500}
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          required
        />
      </label>
      {error && (
        <p role="alert" className="text-feedback-error">
          {error}
        </p>
      )}
      <div className="flex flex-wrap gap-2">
        <Button type="submit" disabled={busy || items.length === 0 || reason.trim() === ""}>
          {busy ? "Sending…" : "Request return"}
        </Button>
        <Button variant="secondary" onClick={onCancel}>
          Back
        </Button>
      </div>
    </form>
  );
}
