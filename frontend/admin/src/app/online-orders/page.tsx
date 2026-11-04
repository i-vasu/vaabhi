"use client";

import { useState } from "react";
import type { WebOrder } from "@vaabhi/api-client";
import { Button, cn } from "@vaabhi/ui";
import { api } from "@/lib/api";
import { describeError, rupees } from "@/lib/settings";
import { useLoad } from "@/lib/tailoring";

type Status = WebOrder["status"];

const STATUS: Record<Status, { label: string; tone: string }> = {
  PENDING_PAYMENT: { label: "Waiting for payment", tone: "bg-warn-soft text-warn" },
  FAILED: { label: "Payment failed", tone: "bg-bad-soft text-bad" },
  PLACED: { label: "To ship", tone: "bg-accent-soft text-accent-ink" },
  SHIPPED: { label: "Shipped", tone: "bg-panel-2 text-ink" },
  DELIVERED: { label: "Delivered", tone: "bg-ok-soft text-ok" },
  CANCELLED: { label: "Cancelled", tone: "bg-panel-2 text-ink-soft" },
};

const FILTERS: { label: string; status?: Status }[] = [
  { label: "All" },
  { label: "To ship", status: "PLACED" },
  { label: "Shipped", status: "SHIPPED" },
  { label: "Delivered", status: "DELIVERED" },
  { label: "Cancelled", status: "CANCELLED" },
];

const when = new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", hour: "numeric", minute: "2-digit" });
const input = "rounded-md border border-line-strong bg-field px-2.5 py-1.5 text-sm";

/** Orders placed on the website, newest first: what to send and where, then ship, deliver or cancel them. */
export default function OnlineOrdersPage() {
  const [filter, setFilter] = useState<Status | undefined>("PLACED");
  const orders = useLoad(() => api.admin.onlineOrders(filter), [filter]);

  const replace = (changed: WebOrder) =>
    orders.setValue((orders.value ?? []).map((o) => (o.number === changed.number ? changed : o)));

  return (
    <div className="flex max-w-4xl flex-col gap-4">
      <h1 className="text-2xl font-semibold">Online orders</h1>
      <nav aria-label="Filter" className="flex flex-wrap gap-2">
        {FILTERS.map((f) => (
          <button
            key={f.label}
            type="button"
            aria-pressed={filter === f.status}
            onClick={() => setFilter(f.status)}
            className={cn(
              "rounded-full border px-3 py-1 text-sm",
              filter === f.status ? "border-accent bg-accent text-white" : "border-line-strong",
            )}
          >
            {f.label}
          </button>
        ))}
      </nav>
      {orders.error && <p className="text-sm text-bad">{orders.error}</p>}
      {orders.loading && !orders.value && <p className="text-sm text-ink-soft">Loading…</p>}
      {orders.value?.length === 0 && <p className="text-sm text-ink-soft">No orders here.</p>}
      <ul className="flex flex-col gap-3">
        {orders.value?.map((order) => (
          <OrderCard key={order.number} order={order} onChange={replace} />
        ))}
      </ul>
    </div>
  );
}

function OrderCard({ order, onChange }: Readonly<{ order: WebOrder; onChange: (order: WebOrder) => void }>) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [shipping, setShipping] = useState(false);
  const [courier, setCourier] = useState("");
  const [tracking, setTracking] = useState("");

  async function act(action: () => Promise<WebOrder>) {
    setBusy(true);
    setError(null);
    try {
      onChange(await action());
      setShipping(false);
    } catch (e) {
      setError(describeError(e).message);
    } finally {
      setBusy(false);
    }
  }

  function cancel() {
    const refund =
      order.paymentMethod === "ONLINE" ? ` ${rupees(order.totalRupees)} will be refunded through Razorpay.` : "";
    const reason = window.prompt(`Cancel ${order.number}?${refund}\nReason (optional):`, "");
    if (reason === null) return;
    void act(() => api.admin.cancelOnlineOrder(order.number, { reason: reason || null }));
  }

  const open = order.status === "PLACED" || order.status === "SHIPPED";
  return (
    <li className="rounded-lg border border-line p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <strong>{order.number}</strong>
          <span className={cn("rounded-full px-2 py-0.5 text-xs font-medium", STATUS[order.status].tone)}>
            {STATUS[order.status].label}
          </span>
          <span className="text-xs text-ink-soft">
            {order.paymentMethod === "COD" ? "Cash on delivery" : "Paid online"}
          </span>
        </div>
        <span className="text-sm text-ink-soft">{when.format(new Date(order.createdAt))}</span>
      </div>
      <p className="mt-2 text-sm">
        <strong className="tabular-nums">{rupees(order.totalRupees)}</strong>
        <span className="text-ink-soft">
          {" "}
          · items {rupees(order.itemsRupees)} · shipping{" "}
          {order.shippingRupees === 0 ? "free" : rupees(order.shippingRupees)}
          {order.paymentMethod === "COD" && open && " · collect on delivery"}
        </span>
        {order.razorpayPaymentId && <span className="text-ink-faint"> · {order.razorpayPaymentId}</span>}
      </p>
      <ul className="mt-2 text-sm">
        {order.lines.map((line) => (
          <li key={`${line.sku}|${line.addonName ?? ""}|${line.blouseSize ?? ""}`}>
            {line.quantity} × {line.name}
            <span className="text-ink-soft">
              {" "}
              · {[line.colourName, line.size].filter(Boolean).join(", ") || "one size"} ·{" "}
              <span className="font-mono text-xs">{line.sku}</span>
              {line.addonName && ` · ${line.addonName}`}
              {line.blouseSize && ` (blouse ${line.blouseSize})`}
            </span>
          </li>
        ))}
      </ul>
      <p className="mt-2 text-sm text-ink-soft">
        Ship to {order.contactName}, {order.contactPhone} — {order.addressLine1}
        {order.addressLine2 ? `, ${order.addressLine2}` : ""}, {order.city}, {order.state} {order.pincode}
      </p>
      {order.status === "SHIPPED" && (order.courier || order.trackingNumber) && (
        <p className="mt-1 text-sm text-ink-soft">
          Sent with {order.courier ?? "—"}
          {order.trackingNumber && `, tracking ${order.trackingNumber}`}
        </p>
      )}
      {order.status === "CANCELLED" && (
        <p className="mt-1 text-sm text-ink-soft">
          Cancelled{order.cancelReason ? `: ${order.cancelReason}` : ""}
          {order.refundedRupees != null && ` · refunded ${rupees(order.refundedRupees)} (${order.refundId})`}
        </p>
      )}

      {shipping && (
        <form
          className="mt-3 flex flex-wrap items-end gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            void act(() =>
              api.admin.shipOnlineOrder(order.number, { courier: courier || null, trackingNumber: tracking || null }),
            );
          }}
        >
          <label className="flex flex-col gap-1 text-xs font-medium">
            Courier
            <input
              className={input}
              value={courier}
              onChange={(e) => setCourier(e.target.value)}
              placeholder="Delhivery"
            />
          </label>
          <label className="flex flex-col gap-1 text-xs font-medium">
            Tracking number
            <input className={input} value={tracking} onChange={(e) => setTracking(e.target.value)} />
          </label>
          <Button type="submit" disabled={busy}>
            {busy ? "Saving…" : "Mark shipped"}
          </Button>
          <Button variant="secondary" onClick={() => setShipping(false)}>
            Back
          </Button>
        </form>
      )}

      {open && !shipping && (
        <div className="mt-3 flex flex-wrap gap-2">
          {order.status === "PLACED" && (
            <Button disabled={busy} onClick={() => setShipping(true)}>
              Ship
            </Button>
          )}
          <Button
            variant="secondary"
            disabled={busy}
            onClick={() => void act(() => api.admin.deliverOnlineOrder(order.number))}
          >
            {order.paymentMethod === "COD" ? "Delivered, cash collected" : "Delivered"}
          </Button>
          <Button variant="secondary" disabled={busy} onClick={cancel}>
            Cancel{order.paymentMethod === "ONLINE" ? " & refund" : ""}
          </Button>
        </div>
      )}
      {error && (
        <p role="alert" className="mt-2 text-sm text-bad">
          {error}
        </p>
      )}
    </li>
  );
}
