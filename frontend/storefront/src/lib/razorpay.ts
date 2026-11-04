import type { CheckoutStart } from "@vaabhi/api-client";

const RAZORPAY_SCRIPT = "https://checkout.razorpay.com/v1/checkout.js";

export type RazorpayResponse = { razorpay_order_id: string; razorpay_payment_id: string; razorpay_signature: string };
type RazorpayOptions = {
  key: string;
  amount: number;
  currency: string;
  order_id: string;
  name: string;
  description: string;
  prefill: { name: string; contact: string; email?: string };
  theme: { color: string };
  handler: (response: RazorpayResponse) => void;
  modal: { ondismiss: () => void };
};
declare global {
  interface Window {
    Razorpay?: new (options: RazorpayOptions) => { open: () => void };
  }
}

/** Loads Razorpay Checkout once; resolves false when it cannot be loaded (offline, blocked). */
export function loadRazorpay(): Promise<boolean> {
  if (window.Razorpay) return Promise.resolve(true);
  return new Promise((resolve) => {
    const script = document.createElement("script");
    script.src = RAZORPAY_SCRIPT;
    script.onload = () => resolve(true);
    script.onerror = () => resolve(false);
    document.body.appendChild(script);
  });
}

/**
 * Opens Razorpay Checkout for an order the API started (checkout or "Pay now"). {@code onPaid} gets the signed
 * payment to confirm on the server; {@code onClosed} runs when the customer closes the window without paying.
 */
export async function openRazorpay(
  order: CheckoutStart,
  onPaid: (response: RazorpayResponse) => void,
  onClosed: () => void,
): Promise<void> {
  if (!(await loadRazorpay()) || !window.Razorpay) {
    throw new Error("Razorpay could not be loaded. Check your connection and try again.");
  }
  if (!order.razorpayKeyId || !order.razorpayOrderId) throw new Error("Online payment is not available right now");
  new window.Razorpay({
    key: order.razorpayKeyId,
    amount: order.amountPaise,
    currency: order.currency,
    order_id: order.razorpayOrderId,
    name: "Vaabhi",
    description: `Order ${order.orderNumber}`,
    prefill: { name: order.name, contact: order.phone, email: order.email ?? undefined },
    theme: { color: "#6E2A35" },
    handler: onPaid,
    modal: { ondismiss: onClosed },
  }).open();
}
