import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { OrderReturn, WebOrder } from "@vaabhi/api-client";
import { API, server } from "@/test/server";
import OnlineOrdersPage from "./page";

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
let listed: WebOrder[];

function serve(answers: Record<string, WebOrder>) {
  sent = [];
  const record = async (request: Request) => {
    const url = new URL(request.url);
    const text = request.method === "GET" ? "" : await request.text();
    sent.push({ method: request.method, path: url.pathname, query: url.search, body: text ? JSON.parse(text) : null });
  };
  server.use(
    http.get(BASE, async ({ request }) => {
      await record(request);
      return HttpResponse.json(listed);
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
  listed = [order()];
  vi.restoreAllMocks();
});

describe("online orders", () => {
  it("lists orders to ship and ships one with Shiprocket", async () => {
    const shipped = order({
      status: "SHIPPED",
      courier: "Delhivery Surface",
      trackingNumber: "AWB505",
      trackingUrl: "https://shiprocket.co/tracking/AWB505",
      shippingLabelUrl: "https://labels.example/1.pdf",
      trackingStatus: "PICKUP SCHEDULED",
    });
    serve({ shiprocket: shipped, tracking: { ...shipped, trackingStatus: "IN TRANSIT" } });
    render(<OnlineOrdersPage />);

    expect(await screen.findByText("WEB-1001")).toBeInTheDocument();
    expect(sent[0]!.query).toBe("?status=PLACED");
    expect(screen.getByText("To ship", { selector: "span" })).toBeInTheDocument();
    expect(screen.getByText(/Ship to Meera Iyer/)).toBeInTheDocument();

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
    render(<OnlineOrdersPage />);
    await screen.findByText("WEB-1001");

    fireEvent.click(screen.getByRole("button", { name: "Ship with Shiprocket" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Shiprocket is not set up yet");

    fireEvent.click(screen.getByRole("button", { name: "Ship by hand" }));
    fireEvent.change(screen.getByLabelText("Courier"), { target: { value: "Local" } });
    fireEvent.change(screen.getByLabelText("Tracking number"), { target: { value: "T-9" } });
    fireEvent.click(screen.getByRole("button", { name: "Mark shipped" }));
    expect(await screen.findByText(/Sent with Local, tracking T-9/)).toBeInTheDocument();
    expect(lastPost("ship")?.body).toEqual({ courier: "Local", trackingNumber: "T-9" });
  });

  it("cancels with a full refund after confirming, and filters by status", async () => {
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
    render(<OnlineOrdersPage />);
    await screen.findByText("WEB-1001");

    fireEvent.click(screen.getByRole("button", { name: "Cancel & refund" }));
    expect(prompt.mock.calls[0]![0]).toContain("₹ 18,500 will be refunded through Razorpay");
    expect(lastPost("cancel")).toBeUndefined();
    fireEvent.click(screen.getByRole("button", { name: "Cancel & refund" }));
    expect(await screen.findByText(/refund ₹ 18,500 on its way \(rfnd_1\)/)).toBeInTheDocument();
    expect(lastPost("cancel")?.body).toEqual({ reason: "Customer called" });

    listed = [];
    fireEvent.click(screen.getByRole("button", { name: "Cancelled" }));
    expect(await screen.findByText("No orders here.")).toBeInTheDocument();
    expect(sent.at(-1)!.query).toBe("?status=CANCELLED");
    fireEvent.click(screen.getByRole("button", { name: "Returns" }));
    await waitFor(() => expect(sent.at(-1)!.query).toBe("?openReturns=true"));
  });

  it("delivers a cash-on-delivery order as cash collected", async () => {
    listed = [order({ paymentMethod: "COD", razorpayPaymentId: null, paidAt: null })];
    serve({ deliver: order({ paymentMethod: "COD", status: "DELIVERED" }) });
    render(<OnlineOrdersPage />);
    expect(await screen.findByText(/collect on delivery/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Delivered, cash collected" }));
    expect(await screen.findByText("Delivered", { selector: "span" })).toBeInTheDocument();
  });

  it("approves a return, books the pickup and records a refund for cash on delivery", async () => {
    const delivered = { status: "DELIVERED" as const, paymentMethod: "COD" as const, razorpayPaymentId: null };
    listed = [order({ ...delivered, returns: [returned()] })];
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
    render(<OnlineOrdersPage />);
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
    listed = [order({ status: "DELIVERED", returns: [returned()] })];
    serve({ reject: order({ status: "DELIVERED", returns: [returned({ status: "REJECTED", shopNote: "Worn" })] }) });
    vi.spyOn(window, "prompt").mockReturnValue("Worn");
    render(<OnlineOrdersPage />);
    await screen.findByText("WEB-1001-R1");
    fireEvent.click(screen.getByRole("button", { name: "Reject" }));
    expect(await screen.findByText("Return rejected")).toBeInTheDocument();
    expect(lastPost("reject")?.body).toEqual({ note: "Worn" });
  });
});
