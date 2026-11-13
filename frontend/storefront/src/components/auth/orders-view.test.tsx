import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { CheckoutStart, OrderReturn, WebOrder } from "@vaabhi/api-client";
import { server } from "@/test/server";
import { OrdersView } from "./orders-view";

const openRazorpay = vi.fn();
vi.mock("@/lib/razorpay", () => ({ openRazorpay: (...args: unknown[]) => openRazorpay(...args) }));

const order = (patch: Partial<WebOrder> = {}): WebOrder => ({
  number: "WEB-1001",
  status: "PLACED",
  paymentMethod: "ONLINE",
  contactName: "Meera Iyer",
  contactPhone: "+919876501234",
  contactEmail: null,
  addressLine1: "4 Lake View Road",
  addressLine2: null,
  city: "New Delhi",
  state: "Delhi",
  pincode: "110001",
  itemsRupees: 18500,
  shippingRupees: 0,
  totalRupees: 18500,
  razorpayPaymentId: "pay_A1",
  paidAt: "2026-11-04T05:00:00Z",
  courier: null,
  trackingNumber: null,
  shippedAt: null,
  deliveredAt: null,
  cancelledAt: null,
  cancelReason: null,
  refundedRupees: null,
  refundId: null,
  refundStatus: null,
  refundError: null,
  trackingStatus: null,
  trackingUrl: null,
  shippingLabelUrl: null,
  returnableUntil: null,
  createdAt: "2026-11-04T05:00:00Z",
  lines: [
    {
      position: 0,
      sku: "KAN-1",
      productSlug: "kanjeevaram",
      name: "Kanjeevaram silk saree",
      colourName: "Rani pink",
      size: null,
      addonName: null,
      blouseSize: null,
      quantity: 2,
      unitRupees: 9250,
      addonRupees: 0,
      amountRupees: 18500,
      imageKey: null,
    },
  ],
  returns: [],
  ...patch,
});

const returned = (patch: Partial<OrderReturn> = {}): OrderReturn => ({
  number: "WEB-1001-R1",
  status: "REQUESTED",
  reason: "Too long",
  shopNote: null,
  itemsRupees: 9250,
  refundRupees: null,
  refundMethod: null,
  refundReference: null,
  refundStatus: null,
  pickupCourier: null,
  pickupAwb: null,
  pickupStatus: null,
  requestedAt: "2026-11-05T05:00:00Z",
  refundedAt: null,
  lines: [{ position: 0, name: "Kanjeevaram silk saree", quantity: 1 }],
  ...patch,
});

let orders: WebOrder[];
const posted: { path: string; body: unknown }[] = [];

function serve(answers: Record<string, unknown> = {}) {
  server.use(
    http.get("*/api/auth/orders", () => HttpResponse.json({ orders })),
    http.post("*/api/auth/*", async ({ request }) => {
      const path = new URL(request.url).pathname;
      const text = await request.text();
      posted.push({ path, body: text ? JSON.parse(text) : null });
      const key = Object.keys(answers).find((k) => path.endsWith(k));
      return key
        ? HttpResponse.json(answers[key] as never)
        : HttpResponse.json({ detail: "This order cannot be cancelled" }, { status: 409 });
    }),
  );
}

const card = (number: string) => screen.getByText(number).closest("li") as HTMLElement;

beforeEach(() => {
  posted.length = 0;
  openRazorpay.mockReset();
});
afterEach(() => vi.restoreAllMocks());

describe("OrdersView", () => {
  it("asks a signed-out visitor to sign in, and explains a failed load", async () => {
    server.use(http.get("*/api/auth/orders", () => new HttpResponse(null, { status: 401 })));
    const { unmount } = render(<OrdersView />);
    expect(await screen.findByRole("link", { name: "Login to see your orders" })).toBeInTheDocument();
    unmount();
    server.use(http.get("*/api/auth/orders", () => new HttpResponse(null, { status: 500 })));
    render(<OrdersView />);
    expect(await screen.findByText(/could not be loaded/)).toBeInTheDocument();
  });

  it("shows an honest empty state", async () => {
    orders = [];
    serve();
    render(<OrdersView />);
    expect(await screen.findByText("No orders yet.")).toBeInTheDocument();
  });

  it("shows shipping, tracking and refund states", async () => {
    orders = [
      order({
        number: "WEB-1",
        status: "SHIPPED",
        courier: "Delhivery",
        trackingNumber: "AWB1",
        trackingStatus: "IN TRANSIT",
        trackingUrl: "https://shiprocket.co/tracking/AWB1",
      }),
      order({ number: "WEB-2", status: "CANCELLED", refundedRupees: 18500, refundStatus: "PENDING" }),
      order({ number: "WEB-3", status: "CANCELLED", refundedRupees: 18500, refundStatus: "PROCESSED" }),
      order({ number: "WEB-4", status: "CANCELLED", refundedRupees: 18500, refundStatus: "FAILED" }),
      order({ number: "WEB-5", paymentMethod: "COD", razorpayPaymentId: null }),
    ];
    serve();
    render(<OrdersView />);
    await screen.findByText("WEB-1");
    expect(within(card("WEB-1")).getByText(/Sent with Delhivery, tracking AWB1 · in transit/)).toBeInTheDocument();
    expect(within(card("WEB-1")).getByRole("link", { name: "Track your parcel" })).toHaveAttribute(
      "href",
      "https://shiprocket.co/tracking/AWB1",
    );
    expect(within(card("WEB-2")).getByText(/on its way to your original payment method/)).toBeInTheDocument();
    expect(within(card("WEB-3")).getByText(/refunded to your original payment method/)).toBeInTheDocument();
    expect(within(card("WEB-4")).getByText(/did not go through/)).toBeInTheDocument();
    expect(within(card("WEB-5")).getByText(/pay on delivery/)).toBeInTheDocument();
  });

  it("pays for an unpaid order again and reloads once the payment is confirmed", async () => {
    orders = [order({ status: "FAILED", razorpayPaymentId: null, paidAt: null })];
    const started = { orderNumber: "WEB-1001", razorpayOrderId: "order_1" } as CheckoutStart;
    serve({ "/pay": started, "/payment": order() });
    openRazorpay.mockImplementation(async (_order, onPaid: (r: unknown) => void) => {
      orders = [order()];
      onPaid({ razorpay_order_id: "order_1", razorpay_payment_id: "pay_9", razorpay_signature: "sig" });
    });
    render(<OrdersView />);
    fireEvent.click(await screen.findByRole("button", { name: /Pay ₹18,500 now/ }));

    expect(await screen.findByText(/Order placed/)).toBeInTheDocument();
    expect(openRazorpay.mock.calls[0]![0]).toEqual(started);
    expect(posted.find((p) => p.path.endsWith("/payment"))?.body).toEqual({
      razorpayOrderId: "order_1",
      razorpayPaymentId: "pay_9",
      razorpaySignature: "sig",
    });
  });

  it("says so when the payment window is closed without paying", async () => {
    orders = [order({ status: "PENDING_PAYMENT", razorpayPaymentId: null, paidAt: null })];
    serve({ "/pay": { orderNumber: "WEB-1001" } });
    openRazorpay.mockImplementation(async (_o, _paid, onClosed: () => void) => onClosed());
    render(<OrdersView />);
    fireEvent.click(await screen.findByRole("button", { name: /now$/ }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Payment was not completed");
  });

  it("cancels an order that has not shipped, after confirming", async () => {
    orders = [order()];
    serve({ "/cancel": order({ status: "CANCELLED", refundedRupees: 18500, refundStatus: "PENDING" }) });
    const prompt = vi.spyOn(window, "prompt").mockReturnValueOnce(null).mockReturnValueOnce("Ordered twice");
    render(<OrdersView />);
    const cancel = await screen.findByRole("button", { name: "Cancel order" });
    fireEvent.click(cancel);
    expect(prompt.mock.calls[0]![0]).toContain("Your payment will be refunded in full");
    expect(posted).toHaveLength(0);
    fireEvent.click(cancel);
    expect(await screen.findByText(/Refund of ₹18,500 on its way/)).toBeInTheDocument();
    expect(posted[0]).toEqual({ path: "/api/auth/orders/WEB-1001/cancel", body: { reason: "Ordered twice" } });
  });

  it("shows the reason when a cancel is refused", async () => {
    orders = [order()];
    serve();
    vi.spyOn(window, "prompt").mockReturnValue("");
    render(<OrdersView />);
    fireEvent.click(await screen.findByRole("button", { name: "Cancel order" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("This order cannot be cancelled");
  });

  it("asks to return some of a delivered order and shows where the return is", async () => {
    const until = new Date(Date.now() + 3 * 86_400_000).toISOString();
    orders = [order({ status: "DELIVERED", returnableUntil: until })];
    serve({ "/returns": order({ status: "DELIVERED", returnableUntil: until, returns: [returned()] }) });
    render(<OrdersView />);

    fireEvent.click(await screen.findByRole("button", { name: /Return items \(until/ }));
    const send = screen.getByRole("button", { name: "Request return" });
    expect(send).toBeDisabled();
    fireEvent.change(screen.getByRole("combobox"), { target: { value: "1" } });
    fireEvent.change(screen.getByLabelText("Why are you returning them?"), { target: { value: "Too long" } });
    fireEvent.click(send);

    expect(await screen.findByText(/Return requested, we will get back to you shortly/)).toBeInTheDocument();
    expect(posted[0]!.body).toEqual({ reason: "Too long", items: [{ position: 0, quantity: 1 }] });
    // One of two is being returned, so the other can still go back.
    expect(screen.getByRole("button", { name: /Return items/ })).toBeInTheDocument();
  });

  it("shows an approved pickup and a refund made by hand", async () => {
    orders = [
      order({
        status: "DELIVERED",
        returns: [
          returned({ status: "APPROVED", shopNote: "Pickup tomorrow", pickupAwb: "AWB9", pickupCourier: "Delhivery" }),
          returned({
            number: "WEB-1001-R2",
            status: "REFUNDED",
            refundRupees: 9250,
            refundMethod: "MANUAL",
            refundReference: "UPI-1",
          }),
        ],
      }),
    ];
    serve();
    render(<OrdersView />);
    expect(await screen.findByText("Pickup tomorrow")).toBeInTheDocument();
    expect(screen.getByText(/Pickup booked with Delhivery \(AWB9\)/)).toBeInTheDocument();
    expect(screen.getByText(/₹9,250 refunded to you \(reference UPI-1\)/)).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole("button", { name: /Return items/ })).not.toBeInTheDocument());
  });
});
