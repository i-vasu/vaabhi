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

const couponQuote = { code: "SAVE10", valid: true, reason: null, discountRupees: 500, itemsRupees: 18500 };

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
    http.post("*/api/auth/checkout/coupon", async ({ request }) => {
      sent.push({ path: "coupon", body: await request.json() });
      return HttpResponse.json(couponQuote);
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
    expect(await screen.findByRole("link", { name: "Login to check out" })).toHaveAttribute(
      "href",
      "/login?next=/checkout",
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
    serve({ checkout: HttpResponse.json({ detail: "Please log in again", errors: [] }, { status: 401 }) });
    render(<CheckoutView />);
    await fillAddress();
    fireEvent.click((await screen.findAllByRole("button", { name: "Pay ₹18,500" }))[0]!);
    expect(await screen.findByRole("link", { name: "Login to check out" })).toBeInTheDocument();
  });

  it("applies a coupon, shows the discount and sends its code to checkout", async () => {
    serve({ checkout: HttpResponse.json({ ...started, status: "PLACED", paymentMethod: "COD" }, { status: 201 }) });
    render(<CheckoutView />);
    await fillAddress();
    expect(await screen.findByText("Free")).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Coupon code"), { target: { value: "save10" } });
    fireEvent.click(screen.getByRole("button", { name: "Apply" }));

    expect(await screen.findByText("− ₹500")).toBeInTheDocument();
    expect(screen.getByText("SAVE10 applied")).toBeInTheDocument();
    expect(screen.getByText("₹18,000")).toBeInTheDocument();

    fireEvent.click(screen.getByText("Cash on delivery"));
    fireEvent.click(screen.getAllByRole("button", { name: /Place order · ₹18,000 on delivery/ })[0]!);

    expect(await screen.findByText("Thank you! Your order is placed.")).toBeInTheDocument();
    expect(sent.find((s) => s.path === "checkout")!.body).toMatchObject({ couponCode: "SAVE10" });
  });

  it("shows why a coupon does not apply, without a discount line", async () => {
    serve();
    server.use(
      http.post("*/api/auth/checkout/coupon", () =>
        HttpResponse.json({
          code: "SAVE10",
          valid: false,
          reason: "This coupon has expired",
          discountRupees: 0,
          itemsRupees: 18500,
        }),
      ),
    );
    render(<CheckoutView />);
    await fillAddress();

    fireEvent.change(screen.getByLabelText("Coupon code"), { target: { value: "SAVE10" } });
    fireEvent.click(screen.getByRole("button", { name: "Apply" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("This coupon has expired");
    expect(screen.queryByText(/Coupon \(/)).not.toBeInTheDocument();
    expect(screen.queryByText(/− ₹/)).not.toBeInTheDocument();
  });

  it("removes an applied coupon and clears the code from checkout", async () => {
    serve({ checkout: HttpResponse.json({ ...started, status: "PLACED", paymentMethod: "COD" }, { status: 201 }) });
    render(<CheckoutView />);
    await fillAddress();

    fireEvent.change(screen.getByLabelText("Coupon code"), { target: { value: "SAVE10" } });
    fireEvent.click(screen.getByRole("button", { name: "Apply" }));
    expect(await screen.findByText("− ₹500")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Remove" }));
    expect(screen.queryByText("− ₹500")).not.toBeInTheDocument();
    expect(screen.queryByText("SAVE10 applied")).not.toBeInTheDocument();

    fireEvent.click(screen.getByText("Cash on delivery"));
    fireEvent.click(screen.getAllByRole("button", { name: /Place order · ₹18,500 on delivery/ })[0]!);

    expect(await screen.findByText("Thank you! Your order is placed.")).toBeInTheDocument();
    expect(sent.find((s) => s.path === "checkout")!.body).toMatchObject({ couponCode: null });
  });

  it("re-quotes the applied coupon on a bag change and drops it with the reason", async () => {
    serve();
    render(<CheckoutView />);
    await fillAddress();

    fireEvent.change(screen.getByLabelText("Coupon code"), { target: { value: "save10" } });
    fireEvent.click(screen.getByRole("button", { name: "Apply" }));
    expect(await screen.findByText("− ₹500")).toBeInTheDocument();

    server.use(
      http.post("*/api/auth/checkout/coupon", () =>
        HttpResponse.json({
          code: "SAVE10",
          valid: false,
          reason: "Needs an order of at least ₹5000",
          discountRupees: 0,
          itemsRupees: 18500,
        }),
      ),
    );
    bagStore.write([{ ...line, sku: "VB-OTHER", name: "Another saree" }]);

    expect(await screen.findByText("Needs an order of at least ₹5000")).toBeInTheDocument();
    expect(screen.queryByText("− ₹500")).not.toBeInTheDocument();
    expect(screen.queryByText("SAVE10 applied")).not.toBeInTheDocument();
  });

  it("re-quotes a still-valid coupon to its new amount on a bag change", async () => {
    serve();
    render(<CheckoutView />);
    await fillAddress();

    fireEvent.change(screen.getByLabelText("Coupon code"), { target: { value: "save10" } });
    fireEvent.click(screen.getByRole("button", { name: "Apply" }));
    expect(await screen.findByText("− ₹500")).toBeInTheDocument();

    server.use(
      http.post("*/api/auth/checkout/coupon", () =>
        HttpResponse.json({ code: "SAVE10", valid: true, reason: null, discountRupees: 800, itemsRupees: 18500 }),
      ),
    );
    bagStore.write([{ ...line, quantity: 2 }]);

    expect(await screen.findByText("− ₹800")).toBeInTheDocument();
    expect(screen.queryByText("− ₹500")).not.toBeInTheDocument();
  });

  it("clears the coupon when placing fails because it has been used up", async () => {
    serve();
    render(<CheckoutView />);
    await fillAddress();

    fireEvent.change(screen.getByLabelText("Coupon code"), { target: { value: "save10" } });
    fireEvent.click(screen.getByRole("button", { name: "Apply" }));
    expect(await screen.findByText("− ₹500")).toBeInTheDocument();

    server.use(
      http.post("*/api/auth/checkout", () =>
        HttpResponse.json({ detail: "Coupon SAVE10: This coupon has been used up", errors: [] }, { status: 400 }),
      ),
    );
    fireEvent.click((await screen.findAllByRole("button", { name: "Pay ₹18,000" }))[0]!);

    expect(await screen.findByRole("alert")).toHaveTextContent("Coupon SAVE10: This coupon has been used up");
    expect(screen.queryByText("− ₹500")).not.toBeInTheDocument();
    expect(screen.queryByText("SAVE10 applied")).not.toBeInTheDocument();
  });

  it("returns to sign in when applying a coupon while signed out", async () => {
    serve();
    server.use(
      http.post("*/api/auth/checkout/coupon", () =>
        HttpResponse.json({ detail: "Please log in again", errors: [] }, { status: 401 }),
      ),
    );
    render(<CheckoutView />);
    await fillAddress();

    fireEvent.change(screen.getByLabelText("Coupon code"), { target: { value: "SAVE10" } });
    fireEvent.click(screen.getByRole("button", { name: "Apply" }));

    expect(await screen.findByRole("link", { name: "Login to check out" })).toBeInTheDocument();
  });

  it("does not request a coupon for an empty code", async () => {
    serve();
    render(<CheckoutView />);
    await fillAddress();

    const apply = screen.getByRole("button", { name: "Apply" });
    expect(apply).toBeDisabled();
    fireEvent.click(apply);

    expect(sent.filter((s) => s.path === "coupon")).toHaveLength(0);
  });
});
