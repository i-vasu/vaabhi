"use client";

import type { WebOrder } from "@vaabhi/api-client";
import { cn } from "@vaabhi/ui";
import { api } from "@/lib/api";
import { rupees } from "@/lib/settings";
import { useLoad } from "@/lib/tailoring";

const STATUS: Record<WebOrder["status"], { label: string; tone: string }> = {
  PAID: { label: "Paid", tone: "bg-ok-soft text-ok" },
  PENDING_PAYMENT: { label: "Waiting for payment", tone: "bg-warn-soft text-warn" },
  FAILED: { label: "Payment failed", tone: "bg-bad-soft text-bad" },
};

const when = new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", hour: "numeric", minute: "2-digit" });

/** Orders placed on the website, newest first, with what to send and where. */
export default function OnlineOrdersPage() {
  const orders = useLoad(() => api.admin.onlineOrders(), []);

  return (
    <div className="flex max-w-4xl flex-col gap-4">
      <h1 className="text-2xl font-semibold">Online orders</h1>
      {orders.error && <p className="text-sm text-bad">{orders.error}</p>}
      {orders.loading && !orders.value && <p className="text-sm text-ink-soft">Loading…</p>}
      {orders.value?.length === 0 && <p className="text-sm text-ink-soft">No online orders yet.</p>}
      <ul className="flex flex-col gap-3">
        {orders.value?.map((order) => (
          <li key={order.number} className="rounded-lg border border-line p-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="flex items-center gap-2">
                <strong>{order.number}</strong>
                <span className={cn("rounded-full px-2 py-0.5 text-xs font-medium", STATUS[order.status].tone)}>
                  {STATUS[order.status].label}
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
          </li>
        ))}
      </ul>
    </div>
  );
}
