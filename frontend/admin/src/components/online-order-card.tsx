"use client";

import { useState } from "react";
import type { OrderReturn, RefundStatus, WebOrder } from "@vaabhi/api-client";
import { Button, cn } from "@vaabhi/ui";
import { api } from "@/lib/api";
import { describeError, rupees } from "@/lib/settings";

type Status = WebOrder["status"];

const STATUS: Record<Status, { label: string; tone: string }> = {
  PENDING_PAYMENT: { label: "Waiting for payment", tone: "bg-warn-soft text-warn" },
  FAILED: { label: "Payment failed", tone: "bg-bad-soft text-bad" },
  PLACED: { label: "To ship", tone: "bg-accent-soft text-accent-ink" },
  SHIPPED: { label: "Shipped", tone: "bg-panel-2 text-ink" },
  DELIVERED: { label: "Delivered", tone: "bg-ok-soft text-ok" },
  CANCELLED: { label: "Cancelled", tone: "bg-panel-2 text-ink-soft" },
};

const REFUND: Record<RefundStatus, string> = {
  PENDING: "on its way",
  PROCESSED: "credited",
  FAILED: "failed, check Razorpay",
};

const RETURN: Record<OrderReturn["status"], { label: string; tone: string }> = {
  REQUESTED: { label: "Return requested", tone: "bg-warn-soft text-warn" },
  APPROVED: { label: "Return approved, waiting for the items", tone: "bg-accent-soft text-accent-ink" },
  REJECTED: { label: "Return rejected", tone: "bg-panel-2 text-ink-soft" },
  REFUNDED: { label: "Returned and refunded", tone: "bg-ok-soft text-ok" },
};

const when = new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", hour: "numeric", minute: "2-digit" });
const input = "rounded-md border border-line-strong bg-field px-2.5 py-1.5 text-sm";

/**
 * An online order with everything the shop does to it: ship (Shiprocket or by hand), track, deliver, handle
 * returns and, for the owner (`canRefund`), cancel with a refund and refund returns.
 */
export function OnlineOrderCard({
  order,
  onChange,
  canRefund,
}: Readonly<{ order: WebOrder; onChange: (order: WebOrder) => void; canRefund: boolean }>) {
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
      {(order.status === "SHIPPED" || order.status === "DELIVERED") && (order.courier || order.trackingNumber) && (
        <p className="mt-1 text-sm text-ink-soft">
          Sent with {order.courier ?? "—"}
          {order.trackingNumber && `, tracking ${order.trackingNumber}`}
          {order.trackingStatus && (
            <>
              {" "}
              · <strong className="font-medium text-ink">{order.trackingStatus}</strong>
            </>
          )}
          {order.trackingUrl && (
            <>
              {" "}
              ·{" "}
              <a className="underline" href={order.trackingUrl} target="_blank" rel="noreferrer">
                Track
              </a>
            </>
          )}
          {order.shippingLabelUrl && (
            <>
              {" "}
              ·{" "}
              <a className="underline" href={order.shippingLabelUrl} target="_blank" rel="noreferrer">
                Print label
              </a>
            </>
          )}
        </p>
      )}
      {order.status === "CANCELLED" && (
        <div className="mt-1 text-sm text-ink-soft">
          <p>
            Cancelled{order.cancelReason ? `: ${order.cancelReason}` : ""}
            {order.refundedRupees != null &&
              ` · refund ${rupees(order.refundedRupees)} ${
                order.refundId == null && order.refundStatus !== "FAILED"
                  ? "not sent to Razorpay yet"
                  : REFUND[order.refundStatus ?? "PENDING"]
              }${order.refundId ? ` (${order.refundId})` : ""}`}
            {order.trackingStatus && ` · ${order.trackingStatus}`}
          </p>
          {order.refundStatus === "FAILED" && order.refundError && (
            <p className="mt-1 text-bad">Razorpay: {order.refundError}</p>
          )}
          {canRefund && order.refundedRupees != null && (order.refundStatus === "FAILED" || order.refundId == null) && (
            <Button
              className="mt-2"
              variant="secondary"
              disabled={busy}
              onClick={() => void act(() => api.admin.retryOnlineOrderRefund(order.number))}
            >
              {busy ? "Sending…" : "Retry refund"}
            </Button>
          )}
        </div>
      )}

      {order.returns.map((r) => (
        <ReturnCard key={r.number} order={order} request={r} onChange={onChange} canRefund={canRefund} />
      ))}

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
            <>
              <Button
                disabled={busy}
                onClick={() => void act(() => api.admin.shipOnlineOrderWithShiprocket(order.number))}
              >
                {busy ? "Booking courier…" : "Ship with Shiprocket"}
              </Button>
              <Button variant="secondary" disabled={busy} onClick={() => setShipping(true)}>
                Ship by hand
              </Button>
            </>
          )}
          {order.status === "SHIPPED" && order.trackingUrl && (
            <Button
              variant="secondary"
              disabled={busy}
              onClick={() => void act(() => api.admin.refreshOnlineOrderTracking(order.number))}
            >
              Refresh tracking
            </Button>
          )}
          <Button
            variant="secondary"
            disabled={busy}
            onClick={() => void act(() => api.admin.deliverOnlineOrder(order.number))}
          >
            {order.paymentMethod === "COD" ? "Delivered, cash collected" : "Delivered"}
          </Button>
          {canRefund && (
            <Button variant="secondary" disabled={busy} onClick={cancel}>
              Cancel{order.paymentMethod === "ONLINE" ? " & refund" : ""}
            </Button>
          )}
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

function ReturnCard({
  order,
  request,
  onChange,
  canRefund,
}: Readonly<{
  order: WebOrder;
  request: OrderReturn;
  onChange: (order: WebOrder) => void;
  canRefund: boolean;
}>) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [refunding, setRefunding] = useState(false);
  const [amount, setAmount] = useState(String(request.itemsRupees));
  const [reference, setReference] = useState("");
  const open = request.status === "REQUESTED" || request.status === "APPROVED";
  const cod = order.paymentMethod === "COD";

  async function act(action: () => Promise<WebOrder>) {
    setBusy(true);
    setError(null);
    try {
      onChange(await action());
      setRefunding(false);
    } catch (e) {
      setError(describeError(e).message);
    } finally {
      setBusy(false);
    }
  }

  function decide(approve: boolean) {
    const note = window.prompt(
      approve ? "Note for the customer (optional), e.g. how to send it back:" : "Why is the return not accepted?",
      "",
    );
    if (note === null) return;
    void act(() =>
      approve
        ? api.admin.approveReturn(request.number, { note: note || null })
        : api.admin.rejectReturn(request.number, { note: note || null }),
    );
  }

  return (
    <div className="mt-3 rounded-md border border-line p-3 text-sm">
      <div className="flex flex-wrap items-center gap-2">
        <strong>{request.number}</strong>
        <span className={cn("rounded-full px-2 py-0.5 text-xs font-medium", RETURN[request.status].tone)}>
          {RETURN[request.status].label}
        </span>
        <span className="text-ink-soft">{when.format(new Date(request.requestedAt))}</span>
      </div>
      <p className="mt-1">
        {request.lines.map((l) => `${l.quantity} × ${l.name}`).join(", ")} · worth {rupees(request.itemsRupees)}
      </p>
      <p className="text-ink-soft">Reason: {request.reason}</p>
      {request.shopNote && <p className="text-ink-soft">Your note: {request.shopNote}</p>}
      {request.pickupAwb && (
        <p className="text-ink-soft">
          Pickup with {request.pickupCourier ?? "courier"}, AWB {request.pickupAwb}
          {request.pickupStatus && ` · ${request.pickupStatus}`}
        </p>
      )}
      {request.status === "REFUNDED" && request.refundRupees != null && (
        <p className="text-ink-soft">
          Refunded {rupees(request.refundRupees)}
          {request.refundMethod === "MANUAL" ? " by hand" : " through Razorpay"}
          {request.refundReference && ` (${request.refundReference})`}
          {request.refundMethod === "RAZORPAY" && request.refundStatus && ` · ${REFUND[request.refundStatus]}`}
        </p>
      )}

      {refunding && (
        <form
          className="mt-2 flex flex-wrap items-end gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            void act(() =>
              api.admin.refundReturn(request.number, {
                amountRupees: Number(amount),
                reference: reference.trim() || null,
              }),
            );
          }}
        >
          <label className="flex flex-col gap-1 text-xs font-medium">
            Refund (₹)
            <input className={input} inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} />
          </label>
          {cod && (
            <label className="flex flex-col gap-1 text-xs font-medium">
              UPI / bank reference
              <input className={input} value={reference} onChange={(e) => setReference(e.target.value)} />
            </label>
          )}
          <Button type="submit" disabled={busy}>
            {busy ? "Refunding…" : cod ? "Record refund" : "Refund through Razorpay"}
          </Button>
          <Button variant="secondary" onClick={() => setRefunding(false)}>
            Back
          </Button>
          <p className="basis-full text-xs text-ink-soft">
            The returned items go back into stock.
            {cod && " Send the money by UPI or bank first, then record its reference here."}
          </p>
        </form>
      )}

      {open && !refunding && (
        <div className="mt-2 flex flex-wrap gap-2">
          {request.status === "REQUESTED" && (
            <>
              <Button disabled={busy} onClick={() => decide(true)}>
                Approve
              </Button>
              <Button variant="secondary" disabled={busy} onClick={() => decide(false)}>
                Reject
              </Button>
            </>
          )}
          {request.status === "APPROVED" && !request.pickupAwb && (
            <Button
              variant="secondary"
              disabled={busy}
              onClick={() => void act(() => api.admin.bookReturnPickup(request.number))}
            >
              {busy ? "Booking pickup…" : "Book pickup with Shiprocket"}
            </Button>
          )}
          {canRefund && (
            <Button variant="secondary" disabled={busy} onClick={() => setRefunding(true)}>
              Items received, refund
            </Button>
          )}
          {request.status === "APPROVED" && (
            <Button variant="secondary" disabled={busy} onClick={() => decide(false)}>
              Reject
            </Button>
          )}
        </div>
      )}
      {error && (
        <p role="alert" className="mt-2 text-bad">
          {error}
        </p>
      )}
    </div>
  );
}
