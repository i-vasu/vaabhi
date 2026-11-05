import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { BagQuote } from "@vaabhi/api-client";
import { bagStore, type BagLine } from "@/lib/bag";
import { quoteLine } from "@/lib/bag-quote.test";
import { server } from "@/test/server";
import { CheckoutView } from "./checkout-view";

const openRazorpay = vi.fn();
vi.mock("@/lib/razorpay", () => ({ openRazorpay: (...args: unknown[]) => openRazorpay(...args) }));

const line: BagLine = {
  sku: "VB-RANI",
  slug: "kanjeevaram",
  name: "Kanjeevaram silk saree",
  colourName: "Rani pink",
  colourHex: "#C2185B",
  size: null,
  addonCode: null,
  addonName: null,
  blouseSize: null,
  unitPriceRupees: 18500,
  addonPriceRupees: 0,
  imageKey: null,
  quantity: 1,
};

const quote: BagQuote = {
  lines: [quoteLine()],
  itemsRupees: 18500,
  addonsRupees: 0,
  mrpRupees: 22000,
  totalRupees: 18500,
};

const started = {
  orderNumber: "WEB-1001",
  status: "PENDING_PAYMENT",
  paymentMethod: "ONLINE",
  totalRupees: 18500,
  razorpayOrderId: "order_1",
  razorpayKeyId: "rzp_test",
  amountPaise: 1850000,
  currency: "INR",
  name: "Meera Iyer",
  phone: "+919876501234",
  email: null,
};

const sent: { path: string; body: unknown }[] = [];

function serve({ signedIn = true, checkout = HttpResponse.json(started, { status: 201 }) as Response } = {}) {
  server.use(
    http.get("*/api/auth/session", () =>
      HttpResponse.json({
        customer: signedIn ? { id: 1, name: "Meera Iyer", phone: "+919876501234", email: null } : null,
      }),
    ),
    http.post("*/api/bag/quote", () => HttpResponse.json(quote)),
    http.post("*/api/auth/checkout", async ({ request }) => {
      sent.push({ path: "checkout", body: await request.json() });
      return checkout.clone();
    }),
    http.post("*/api/auth/checkout/:number/payment", async ({ request }) => {
      sent.push({ path: "payment", body: await request.json() });
      return HttpResponse.json({ number: "WEB-1001", totalRupees: 18500 });
    }),
  );
}

async function fillAddress(pincode = "110001") {
  expect(await screen.findByLabelText("Full name")).toHaveValue("Meera Iyer");
  expect(screen.getByLabelText("Mobile number (10 digits)")).toHaveValue("9876501234");
  fireEvent.change(screen.getByLabelText("House, building, street"), { target: { value: "4 Lake View Road" } });
  fireEvent.change(screen.getByLabelText("City"), { target: { value: "New Delhi" } });
  fireEvent.change(screen.getByLabelText("State"), { target: { value: "Delhi" } });
  fireEvent.change(screen.getByLabelText("Pincode"), { target: { value: pincode } });
}

beforeEach(() => {
  sent.length = 0;
  openRazorpay.mockReset();
  bagStore.write([line]);
});
afterEach(() => localStorage.clear());

describe("CheckoutView", () => {
  it("sends an empty bag back to shopping", () => {
    bagStore.write([]);
    serve();
    render(<CheckoutView />);
    expect(screen.getByText("Your bag is empty.")).toBeInTheDocument();
  });

  it("asks a signed-out shopper to sign in first", async () => {
    serve({ signedIn: false });
    render(<CheckoutView />);
    expect(await screen.findByRole("link", { name: "Sign in to check out" })).toHaveAttribute(
      "href",
      "/signup?next=/checkout",
    );
  });

  it("places a cash-on-delivery order and empties the bag", async () => {
    serve({ checkout: HttpResponse.json({ ...started, status: "PLACED", paymentMethod: "COD" }, { status: 201 }) });
    render(<CheckoutView />);
    await fillAddress();
    expect(await screen.findByText("Free")).toBeInTheDocument();

    fireEvent.click(screen.getByText("Cash on delivery"));
    fireEvent.click(screen.getAllByRole("button", { name: /Place order · ₹18,500 on delivery/ })[0]!);

    expect(await screen.findByText("Thank you! Your order is placed.")).toBeInTheDocument();
    expect(screen.getByText(/to pay on delivery/)).toBeInTheDocument();
    expect(bagStore.read()).toEqual([]);
    expect(sent[0]!.body).toMatchObject({
      lines: [{ sku: "VB-RANI", addonCode: null, blouseSize: null, quantity: 1 }],
      address: { name: "Meera Iyer", phone: "9876501234", city: "New Delhi", pincode: "110001", line2: null },
      paymentMethod: "COD",
    });
    expect(openRazorpay).not.toHaveBeenCalled();
  });

  it("pays online through Razorpay and confirms the signed payment", async () => {
    serve();
    openRazorpay.mockImplementation(async (_order, onPaid: (r: unknown) => void) =>
      onPaid({ razorpay_order_id: "order_1", razorpay_payment_id: "pay_1", razorpay_signature: "sig" }),
    );
    render(<CheckoutView />);
    await fillAddress();
    fireEvent.click((await screen.findAllByRole("button", { name: "Pay ₹18,500" }))[0]!);

    expect(await screen.findByText("Thank you! Your order is placed.")).toBeInTheDocument();
    expect(openRazorpay.mock.calls[0]![0]).toMatchObject({ razorpayOrderId: "order_1", amountPaise: 1850000 });
    expect(sent[1]).toEqual({
      path: "payment",
      body: { razorpayOrderId: "order_1", razorpayPaymentId: "pay_1", razorpaySignature: "sig" },
    });
  });

  it("reuses the placed order when the payment window is closed and Pay is pressed again", async () => {
    serve();
    openRazorpay.mockImplementation(async (_o, _paid, onClosed: () => void) => onClosed());
    render(<CheckoutView />);
    await fillAddress();
    fireEvent.click((await screen.findAllByRole("button", { name: "Pay ₹18,500" }))[0]!);
    expect(await screen.findByRole("alert")).toHaveTextContent("Payment was not completed. You can try again.");

    fireEvent.click(screen.getAllByRole("button", { name: "Pay ₹18,500" })[0]!);
    await waitFor(() => expect(openRazorpay).toHaveBeenCalledTimes(2));
    expect(sent.filter((s) => s.path === "checkout")).toHaveLength(1);
  });

  it("explains a failed confirmation without losing the order", async () => {
    serve();
    server.use(
      http.post("*/api/auth/checkout/:number/payment", () =>
        HttpResponse.json({ detail: "The payment could not be verified" }, { status: 400 }),
      ),
    );
    openRazorpay.mockImplementation(async (_order, onPaid: (r: unknown) => void) =>
      onPaid({ razorpay_order_id: "order_1", razorpay_payment_id: "pay_1", razorpay_signature: "bad" }),
    );
    render(<CheckoutView />);
    await fillAddress();
    fireEvent.click((await screen.findAllByRole("button", { name: "Pay ₹18,500" }))[0]!);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      /could not be verified.*your order WEB-1001 will be confirmed shortly/,
    );
  });

  it("shows why the shop refused the order, next to the field when there is one", async () => {
    serve({
      checkout: HttpResponse.json(
        {
          detail: "We cannot deliver to pincode 999999 yet.",
          errors: [{ field: "address.pincode", message: "Enter a 6-digit pincode" }],
        },
        { status: 409 },
      ),
    });
    render(<CheckoutView />);
    await fillAddress("999999");
    fireEvent.click((await screen.findAllByRole("button", { name: "Pay ₹18,500" }))[0]!);
    expect(await screen.findByRole("alert")).toHaveTextContent("We cannot deliver to pincode 999999 yet.");
    expect(screen.getByText("Enter a 6-digit pincode")).toBeInTheDocument();
    expect(openRazorpay).not.toHaveBeenCalled();
  });

  it("sends a shopper whose session ended back to sign in", async () => {
    serve({ checkout: HttpResponse.json({ detail: "Please sign in again", errors: [] }, { status: 401 }) });
    render(<CheckoutView />);
    await fillAddress();
    fireEvent.click((await screen.findAllByRole("button", { name: "Pay ₹18,500" }))[0]!);
    expect(await screen.findByRole("link", { name: "Sign in to check out" })).toBeInTheDocument();
  });
});
