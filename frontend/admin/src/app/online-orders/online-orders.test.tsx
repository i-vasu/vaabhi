import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { OrderPage, OrderReturn, OrderSummary, WebOrder } from "@vaabhi/api-client";
import { API, server } from "@/test/server";
import OrdersPage from "../orders/page";
import OnlineOrdersPage from "./page";
import OnlineOrderPage from "./view/page";

let search = new URLSearchParams("number=WEB-1001");
const replaceRoute = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: replaceRoute }),
  useSearchParams: () => search,
}));

let role: "OWNER" | "STAFF" = "OWNER";
vi.mock("@/components/app-shell", () => ({ useStaff: () => ({ role }) }));

const BASE = `${API}/api/v1/admin/online-orders`;

function order(patch: Partial<WebOrder> = {}): WebOrder {
  return {
    number: "WEB-1001",
    status: "PLACED",
    paymentMethod: "ONLINE",
    contactName: "Meera Iyer",
    contactPhone: "+919876501234",
    contactEmail: "meera@example.com",
    addressLine1: "4 Lake View Road",
    addressLine2: null,
    city: "New Delhi",
    state: "Delhi",
    pincode: "110001",
    itemsRupees: 18500,
    shippingRupees: 0,
    couponCode: null,
    discountRupees: 0,
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
        addonName: "Unstitched blouse piece",
        blouseSize: null,
        quantity: 1,
        unitRupees: 18500,
        addonRupees: 0,
        amountRupees: 18500,
        imageKey: null,
      },
    ],
    returns: [],
    ...patch,
  };
}

function returned(patch: Partial<OrderReturn> = {}): OrderReturn {
  return {
    number: "WEB-1001-R1",
    status: "REQUESTED",
    reason: "Colour differs",
    shopNote: null,
    itemsRupees: 18500,
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
  };
}

type Sent = { method: string; path: string; query: string; body: unknown };
let sent: Sent[];
let current: WebOrder;

/** Serves the order being looked at; each action answers with the given order (or a 503 like Shiprocket's). */
function serve(answers: Record<string, WebOrder>) {
  sent = [];
  const record = async (request: Request) => {
    const url = new URL(request.url);
    const text = request.method === "GET" ? "" : await request.text();
    sent.push({ method: request.method, path: url.pathname, query: url.search, body: text ? JSON.parse(text) : null });
  };
  server.use(
    http.get(`${BASE}/:number`, async ({ request }) => {
      await record(request);
      return HttpResponse.json(current);
    }),
    http.post(`${BASE}/*`, async ({ request }) => {
      await record(request);
      const action = new URL(request.url).pathname.split("/").at(-1)!;
      const answer = answers[action];
      return answer
        ? HttpResponse.json(answer)
        : HttpResponse.json({ detail: "Shiprocket is not set up yet" }, { status: 503 });
    }),
  );
}

const lastPost = (action: string) => sent.filter((s) => s.method === "POST" && s.path.endsWith(`/${action}`)).at(-1);

beforeEach(() => {
  current = order();
  role = "OWNER";
  search = new URLSearchParams("number=WEB-1001");
  replaceRoute.mockReset();
  vi.restoreAllMocks();
});

describe("online order page", () => {
  it("ships an order with Shiprocket and refreshes its tracking", async () => {
    const shipped = order({
      status: "SHIPPED",
      courier: "Delhivery Surface",
      trackingNumber: "AWB505",
      trackingUrl: "https://shiprocket.co/tracking/AWB505",
      shippingLabelUrl: "https://labels.example/1.pdf",
      trackingStatus: "PICKUP SCHEDULED",
    });
    serve({ shiprocket: shipped, tracking: { ...shipped, trackingStatus: "IN TRANSIT" } });
    render(<OnlineOrderPage />);

    expect(await screen.findByText("To ship")).toBeInTheDocument();
    expect(sent[0]!.path).toBe("/api/v1/admin/online-orders/WEB-1001");
    expect(screen.getByText(/Ship to Meera Iyer/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Back/ })).toHaveAttribute("href", "/orders/?source=ONLINE");

    fireEvent.click(screen.getByRole("button", { name: "Ship with Shiprocket" }));
    expect(await screen.findByText(/Sent with Delhivery Surface, tracking AWB505/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Track" })).toHaveAttribute(
      "href",
      "https://shiprocket.co/tracking/AWB505",
    );
    expect(screen.getByRole("link", { name: "Print label" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Refresh tracking" }));
    expect(await screen.findByText("IN TRANSIT")).toBeInTheDocument();
    expect(lastPost("tracking")).toBeDefined();
  });

  it("ships by hand with a courier and tracking number, and shows API errors", async () => {
    serve({ ship: order({ status: "SHIPPED", courier: "Local", trackingNumber: "T-9" }) });
    render(<OnlineOrderPage />);
    await screen.findByText("To ship");

    fireEvent.click(screen.getByRole("button", { name: "Ship with Shiprocket" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Shiprocket is not set up yet");

    fireEvent.click(screen.getByRole("button", { name: "Ship by hand" }));
    fireEvent.change(screen.getByLabelText("Courier"), { target: { value: "Local" } });
    fireEvent.change(screen.getByLabelText("Tracking number"), { target: { value: "T-9" } });
    fireEvent.click(screen.getByRole("button", { name: "Mark shipped" }));
    expect(await screen.findByText(/Sent with Local, tracking T-9/)).toBeInTheDocument();
    expect(lastPost("ship")?.body).toEqual({ courier: "Local", trackingNumber: "T-9" });
  });

  it("lets the owner cancel with a full refund after confirming", async () => {
    serve({
      cancel: order({
        status: "CANCELLED",
        cancelReason: "Customer called",
        refundedRupees: 18500,
        refundId: "rfnd_1",
        refundStatus: "PENDING",
      }),
    });
    const prompt = vi.spyOn(window, "prompt").mockReturnValueOnce(null).mockReturnValueOnce("Customer called");
    render(<OnlineOrderPage />);
    await screen.findByText("To ship");

    fireEvent.click(screen.getByRole("button", { name: "Cancel & refund" }));
    expect(prompt.mock.calls[0]![0]).toContain("₹ 18,500 will be refunded through Razorpay");
    expect(lastPost("cancel")).toBeUndefined();
    fireEvent.click(screen.getByRole("button", { name: "Cancel & refund" }));
    expect(await screen.findByText(/refund ₹ 18,500 on its way \(rfnd_1\)/)).toBeInTheDocument();
    expect(lastPost("cancel")?.body).toEqual({ reason: "Customer called" });
  });

  it("shows a cancelled refund that never reached Razorpay with a Retry button", async () => {
    current = order({
      status: "CANCELLED",
      refundedRupees: 18500,
      refundId: null,
      refundStatus: "PENDING",
    });
    serve({});
    render(<OnlineOrderPage />);

    expect(await screen.findByText(/not sent to Razorpay yet/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Retry refund" })).toBeInTheDocument();
  });

  it("retries a failed Razorpay refund and shows the new refund id", async () => {
    current = order({
      status: "CANCELLED",
      refundedRupees: 18500,
      refundId: null,
      refundStatus: "FAILED",
      refundError: "The amount is invalid",
    });
    serve({
      refund: order({
        status: "CANCELLED",
        refundedRupees: 18500,
        refundId: "rfnd_2",
        refundStatus: "PENDING",
      }),
    });
    render(<OnlineOrderPage />);

    expect(await screen.findByText("Razorpay: The amount is invalid")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Retry refund" }));
    expect(await screen.findByText(/\(rfnd_2\)/)).toBeInTheDocument();
    expect(lastPost("refund")?.path).toBe("/api/v1/admin/online-orders/WEB-1001/refund");
    expect(screen.queryByRole("button", { name: "Retry refund" })).not.toBeInTheDocument();
  });

  it("shows a pending refund id without a Retry button", async () => {
    current = order({
      status: "CANCELLED",
      refundedRupees: 18500,
      refundId: "rfnd_1",
      refundStatus: "PENDING",
    });
    serve({});
    render(<OnlineOrderPage />);

    expect(await screen.findByText(/\(rfnd_1\)/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Retry refund" })).not.toBeInTheDocument();
  });

  it("does not offer the Retry refund button to counter staff", async () => {
    role = "STAFF";
    current = order({
      status: "CANCELLED",
      refundedRupees: 18500,
      refundId: null,
      refundStatus: "FAILED",
      refundError: "The amount is invalid",
    });
    serve({});
    render(<OnlineOrderPage />);

    expect(await screen.findByText("Razorpay: The amount is invalid")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Retry refund" })).not.toBeInTheDocument();
  });

  it("does not offer cancelling or refunds to counter staff", async () => {
    role = "STAFF";
    current = order({ status: "DELIVERED", returns: [returned({ status: "APPROVED" })] });
    serve({});
    render(<OnlineOrderPage />);
    expect(await screen.findByText("WEB-1001-R1")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Cancel/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Items received, refund" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Book pickup with Shiprocket" })).toBeInTheDocument();
  });

  it("delivers a cash-on-delivery order as cash collected", async () => {
    current = order({ paymentMethod: "COD", razorpayPaymentId: null, paidAt: null });
    serve({ deliver: order({ paymentMethod: "COD", status: "DELIVERED" }) });
    render(<OnlineOrderPage />);
    expect(await screen.findByText(/collect on delivery/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Delivered, cash collected" }));
    expect(await screen.findByText("Delivered", { selector: "span" })).toBeInTheDocument();
  });

  it("approves a return, books the pickup and records a refund for cash on delivery", async () => {
    const delivered = { status: "DELIVERED" as const, paymentMethod: "COD" as const, razorpayPaymentId: null };
    current = order({ ...delivered, returns: [returned()] });
    serve({
      approve: order({ ...delivered, returns: [returned({ status: "APPROVED", shopNote: "Pickup tomorrow" })] }),
      pickup: order({
        ...delivered,
        returns: [
          returned({
            status: "APPROVED",
            pickupCourier: "Delhivery",
            pickupAwb: "AWB9",
            pickupStatus: "Pickup booked",
          }),
        ],
      }),
      refund: order({
        ...delivered,
        returns: [
          returned({
            status: "REFUNDED",
            refundRupees: 18000,
            refundMethod: "MANUAL",
            refundReference: "UPI-1",
            refundStatus: "PROCESSED",
          }),
        ],
      }),
    });
    vi.spyOn(window, "prompt").mockReturnValue("Pickup tomorrow");
    render(<OnlineOrderPage />);
    const card = await screen.findByText("WEB-1001-R1").then((el) => el.closest("div.mt-3") as HTMLElement);
    expect(within(card).getByText("Return requested")).toBeInTheDocument();
    expect(within(card).getByText("Reason: Colour differs")).toBeInTheDocument();

    fireEvent.click(within(card).getByRole("button", { name: "Approve" }));
    expect(await screen.findByText("Your note: Pickup tomorrow")).toBeInTheDocument();
    expect(lastPost("approve")?.body).toEqual({ note: "Pickup tomorrow" });

    fireEvent.click(screen.getByRole("button", { name: "Book pickup with Shiprocket" }));
    expect(await screen.findByText(/Pickup with Delhivery, AWB AWB9 · Pickup booked/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Items received, refund" }));
    expect(screen.getByLabelText("Refund (₹)")).toHaveValue("18500");
    fireEvent.change(screen.getByLabelText("Refund (₹)"), { target: { value: "18000" } });
    fireEvent.change(screen.getByLabelText("UPI / bank reference"), { target: { value: "UPI-1" } });
    fireEvent.click(screen.getByRole("button", { name: "Record refund" }));
    expect(await screen.findByText(/Refunded ₹ 18,000 by hand \(UPI-1\)/)).toBeInTheDocument();
    expect(lastPost("refund")?.body).toEqual({ amountRupees: 18000, reference: "UPI-1" });
  });

  it("rejects a return with a reason", async () => {
    current = order({ status: "DELIVERED", returns: [returned()] });
    serve({ reject: order({ status: "DELIVERED", returns: [returned({ status: "REJECTED", shopNote: "Worn" })] }) });
    vi.spyOn(window, "prompt").mockReturnValue("Worn");
    render(<OnlineOrderPage />);
    await screen.findByText("WEB-1001-R1");
    fireEvent.click(screen.getByRole("button", { name: "Reject" }));
    expect(await screen.findByText("Return rejected")).toBeInTheDocument();
    expect(lastPost("reject")?.body).toEqual({ note: "Worn" });
  });

  it("sends the old Online orders address to Orders, filtered to online", () => {
    render(<OnlineOrdersPage />);
    expect(replaceRoute).toHaveBeenCalledWith("/orders/?source=ONLINE");
  });
});

describe("orders list with shop and online orders", () => {
  const row = (patch: Partial<OrderSummary>): OrderSummary => ({
    id: 1,
    number: "ORD-1001",
    clientId: 7,
    clientName: "Asha Rao",
    clientPhone: "+919876543210",
    status: "BOOKED",
    orderDate: "2026-11-04",
    deliveryDate: "2026-11-10",
    trialDate: null,
    itemCount: 1,
    itemsLabel: "3 dart blouse",
    totalRupees: 1200,
    balanceRupees: 0,
    overdue: false,
    source: "SHOP",
    onlineStatus: null,
    openReturn: false,
    ...patch,
  });
  let queries: string[];

  function serveList(items: OrderSummary[]) {
    queries = [];
    server.use(
      http.get(`${API}/api/v1/admin/orders`, ({ request }) => {
        queries.push(new URL(request.url).search);
        return HttpResponse.json({ items, total: items.length, page: 1, pageSize: 20 } satisfies OrderPage);
      }),
    );
  }

  it("tags each order Shop or Online and links online orders to their page", async () => {
    search = new URLSearchParams();
    serveList([
      row({}),
      row({
        id: 9,
        number: "WEB-1002",
        clientId: null,
        clientName: "Meera Iyer",
        clientPhone: "+919876501234",
        deliveryDate: null,
        itemsLabel: "Kanjeevaram silk saree",
        totalRupees: 24900,
        source: "ONLINE",
        onlineStatus: "PLACED",
        openReturn: true,
      }),
    ]);
    render(<OrdersPage />);

    const online = (await screen.findByText(/WEB-1002/)).closest("a")!;
    expect(online).toHaveAttribute("href", "/online-orders/view/?number=WEB-1002");
    expect(within(online).getByText("Online")).toBeInTheDocument();
    expect(within(online).getByText("To ship")).toBeInTheDocument();
    expect(within(online).getByText("Return")).toBeInTheDocument();
    expect(within(online).getByText(/ordered 4 Nov/)).toBeInTheDocument();

    const shop = screen.getByText(/ORD-1001/).closest("a")!;
    expect(shop).toHaveAttribute("href", "/orders/view/?id=1");
    expect(within(shop).getByText("Shop")).toBeInTheDocument();
    expect(within(shop).getByText(/due 10 Nov/)).toBeInTheDocument();
    expect(queries[0]).toBe("?filter=OPEN&page=1&pageSize=20");
  });

  it("filters by where orders came from and shows returns to handle", async () => {
    search = new URLSearchParams("source=ONLINE&filter=OPEN");
    serveList([]);
    render(<OrdersPage />);
    expect(await screen.findByText("No orders here")).toBeInTheDocument();
    expect(queries[0]).toBe("?filter=OPEN&page=1&pageSize=20&source=ONLINE");

    fireEvent.click(screen.getByRole("radio", { name: "Shop" }));
    await waitFor(() => expect(queries.at(-1)).toBe("?filter=OPEN&page=1&pageSize=20&source=SHOP"));
    fireEvent.click(screen.getByRole("radio", { name: "Returns" }));
    await waitFor(() => expect(queries.at(-1)).toBe("?filter=ALL&openReturns=true&page=1&pageSize=20&source=ONLINE"));
    expect(await screen.findByText("No returns to handle.")).toBeInTheDocument();
  });
});
