import { afterEach, describe, expect, it, vi } from "vitest";
import type { CheckoutStart } from "@vaabhi/api-client";
import { loadRazorpay, openRazorpay } from "./razorpay";

const order = {
  orderNumber: "WEB-1001",
  razorpayOrderId: "order_1",
  razorpayKeyId: "rzp_test",
  amountPaise: 1850000,
  currency: "INR",
  name: "Meera Iyer",
  phone: "+919876501234",
  email: null,
} as CheckoutStart;

afterEach(() => {
  delete window.Razorpay;
  document.body.innerHTML = "";
});

describe("razorpay", () => {
  it("loads Checkout once and reports when the script cannot load", async () => {
    const loading = loadRazorpay();
    const script = document.querySelector("script")!;
    expect(script.src).toBe("https://checkout.razorpay.com/v1/checkout.js");
    script.onerror?.(new Event("error"));
    expect(await loading).toBe(false);

    const loaded = loadRazorpay();
    document.querySelectorAll("script")[1]!.onload?.(new Event("load"));
    expect(await loaded).toBe(true);

    window.Razorpay = vi.fn() as never;
    expect(await loadRazorpay()).toBe(true);
    expect(document.querySelectorAll("script")).toHaveLength(2);
  });

  it("opens Checkout for the order and hands back the signed payment", async () => {
    const open = vi.fn();
    let options: Record<string, unknown> = {};
    window.Razorpay = vi.fn(function (this: unknown, given: Record<string, unknown>) {
      options = given;
      return { open };
    }) as never;
    const onPaid = vi.fn();
    const onClosed = vi.fn();

    await openRazorpay(order, onPaid, onClosed);

    expect(open).toHaveBeenCalled();
    expect(options).toMatchObject({
      key: "rzp_test",
      amount: 1850000,
      order_id: "order_1",
      description: "Order WEB-1001",
      prefill: { name: "Meera Iyer", contact: "+919876501234", email: undefined },
    });
    (options.handler as (r: unknown) => void)({ razorpay_payment_id: "pay_1" });
    expect(onPaid).toHaveBeenCalledWith({ razorpay_payment_id: "pay_1" });
    (options.modal as { ondismiss: () => void }).ondismiss();
    expect(onClosed).toHaveBeenCalled();
  });

  it("refuses an order without Razorpay details", async () => {
    window.Razorpay = vi.fn() as never;
    await expect(openRazorpay({ ...order, razorpayOrderId: null }, vi.fn(), vi.fn())).rejects.toThrow(
      "Online payment is not available right now",
    );
  });
});
