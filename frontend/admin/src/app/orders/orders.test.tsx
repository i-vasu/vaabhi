import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { blankDraft, blankItem, formFromDraft } from "@/components/order-form";
import { lastSent, serveTailoring, type Sent } from "@/test/tailoring-fixtures";
import NewOrderPage from "./new/page";
import OrdersPage from "./page";
import OrderPage from "./view/page";

const replace = vi.fn();
let search = new URLSearchParams();
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace }), useSearchParams: () => search }));

let sent: Sent[];
beforeEach(() => {
  sent = serveTailoring().sent;
  replace.mockReset();
  search = new URLSearchParams();
});

const type = (label: string | RegExp, value: string, scope: HTMLElement = document.body) =>
  fireEvent.change(within(scope).getByLabelText(label), { target: { value } });

describe("orders list", () => {
  it("starts on open orders and filters by status", async () => {
    render(<OrdersPage />);

    expect(await screen.findByRole("link", { name: /ORD-2042 · Meera Iyer/ })).toHaveAttribute(
      "href",
      "/orders/view/?id=42",
    );
    expect(lastSent(sent, "GET", "/orders")?.query).toContain("filter=OPEN");
    fireEvent.click(screen.getByRole("radio", { name: "Overdue" }));
    await waitFor(() => expect(lastSent(sent, "GET", "/orders")?.query).toContain("filter=OVERDUE"));
  });

  it("shows every order of one customer", async () => {
    search = new URLSearchParams("clientId=7");
    render(<OrdersPage />);
    await screen.findByRole("link", { name: /ORD-2042/ });
    expect(lastSent(sent, "GET", "/orders")?.query).toMatch(/clientId=7.*filter=ALL|filter=ALL.*clientId=7/);
    expect(screen.getByRole("link", { name: /New order/ })).toHaveAttribute("href", "/orders/new/?clientId=7");
  });
});

describe("order form rules", () => {
  it("explains what is missing", () => {
    const draft = blankDraft(new Date("2026-11-02T10:00:00"));
    expect(formFromDraft(null, draft).error).toBe("Choose the customer.");
    expect(formFromDraft(7, draft).error).toBe("Item 1: say what is being stitched.");
    const item = { ...blankItem(), description: "Blouse" };
    expect(formFromDraft(7, { ...draft, items: [item] }).error).toBe("Item 1: enter the price.");
    const priced = { ...item, price: "500", quantity: "0" };
    expect(formFromDraft(7, { ...draft, items: [priced] }).error).toBe("Item 1: enter the quantity.");
    const ok = { ...priced, quantity: "1" };
    expect(formFromDraft(7, { ...draft, items: [ok], discountKind: "PERCENT", discount: "120" }).error).toBe(
      "Enter the discount as a number.",
    );
    expect(formFromDraft(7, { ...draft, items: [ok] }).form).toMatchObject({
      clientId: 7,
      deliveryDate: "2026-11-09",
      items: [{ description: "Blouse", pricePaise: 50000, quantity: 1, measurements: null }],
      discountValue: 0,
      advancePaise: 0,
    });
  });
});

describe("new order", () => {
  it("fills price and measurements from the price list and books with live totals", async () => {
    search = new URLSearchParams("clientId=7");
    render(<NewOrderPage />);

    expect(await screen.findByText("Meera Iyer")).toBeInTheDocument();
    const item = await screen.findByRole("group", { name: "Item 1" });
    fireEvent.change(within(item).getByLabelText("From price list"), { target: { value: "11" } });
    expect(within(item).getByLabelText("Description")).toHaveValue("3 dart blouse");
    expect(within(item).getByLabelText("Price (₹)")).toHaveValue("500");
    type("Qty", "2", item);

    fireEvent.click(within(item).getByRole("button", { name: /Measurements \(2 of 2\)/ }));
    expect(within(item).getByLabelText("Bust (in)")).toHaveValue("34");
    type("Waist (in)", "29", item);

    fireEvent.click(screen.getByRole("radio", { name: "% off" }));
    type("Discount (%)", "10");
    type("Advance (₹)", "300");
    const total = screen.getByLabelText("Order total");
    expect(within(total).getByText("₹ 1,000")).toBeInTheDocument();
    expect(within(total).getByText("− ₹ 100")).toBeInTheDocument();
    expect(within(total).getByText("₹ 600")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Book order" }));
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/orders/view/?id=42"));
    expect(lastSent(sent, "POST", "/orders")?.body).toMatchObject({
      clientId: 7,
      items: [
        {
          templateId: 1,
          serviceProductId: 11,
          description: "3 dart blouse",
          quantity: 2,
          pricePaise: 50000,
          measurements: { bust: "34", waist: "29" },
        },
      ],
      discountKind: "PERCENT",
      discountValue: 1000,
      advancePaise: 30000,
      advanceMode: "CASH",
    });
  });

  it("asks for a customer first", async () => {
    render(<NewOrderPage />);
    fireEvent.click(await screen.findByRole("button", { name: "Book order" }));
    expect(await screen.findByText("Choose the customer.")).toBeInTheDocument();
    expect(lastSent(sent, "POST", "/orders")).toBeUndefined();
  });

  it("finds the customer by search", async () => {
    render(<NewOrderPage />);
    await screen.findByLabelText("Find customer");
    type("Find customer", "meera");
    fireEvent.submit(screen.getByRole("search"));
    fireEvent.click(await screen.findByRole("button", { name: /Meera Iyer/ }));
    expect(screen.getByRole("button", { name: "Change" })).toBeInTheDocument();
  });
});

describe("order page", () => {
  beforeEach(() => {
    search = new URLSearchParams("id=42");
  });

  it("shows items, money and payments", async () => {
    render(<OrderPage />);
    expect(await screen.findByRole("heading", { name: "ORD-2042" })).toBeInTheDocument();
    expect(screen.getByText(/2 × 3 dart blouse/)).toBeInTheDocument();
    expect(screen.getByText("Discount (10%)")).toBeInTheDocument();
    expect(screen.getByText("Advance")).toBeInTheDocument();
  });

  it("moves an item to the next stage", async () => {
    render(<OrderPage />);
    fireEvent.change(await screen.findByLabelText("Stage of 3 dart blouse"), { target: { value: "2" } });
    await waitFor(() =>
      expect(lastSent(sent, "POST", "/orders/42/stage")?.body).toEqual({ stageId: 2, itemIds: [101] }),
    );
    expect(await screen.findByText("In progress")).toBeInTheDocument();
  });

  it("takes the balance as a payment", async () => {
    render(<OrderPage />);
    fireEvent.click(await screen.findByRole("button", { name: "Take payment" }));
    const dialog = screen.getByRole("dialog", { name: "Take payment" });
    expect(within(dialog).getByLabelText("Amount (₹)")).toHaveValue("690");
    fireEvent.change(within(dialog).getByLabelText("Paid by"), { target: { value: "UPI" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Record payment" }));
    await waitFor(() =>
      expect(lastSent(sent, "POST", "/orders/42/payments")?.body).toEqual({
        amountPaise: 69000,
        mode: "UPI",
        refund: false,
        note: null,
      }),
    );
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  });

  it("changes dates, delivers and cancels", async () => {
    render(<OrderPage />);
    fireEvent.click(await screen.findByRole("button", { name: "Change dates" }));
    const dialog = screen.getByRole("dialog", { name: "Change dates" });
    type("Delivery date", "2026-11-10", dialog);
    fireEvent.click(within(dialog).getByRole("button", { name: "Save dates" }));
    await waitFor(() =>
      expect(lastSent(sent, "PUT", "/orders/42/schedule")?.body).toMatchObject({ deliveryDate: "2026-11-10" }),
    );

    fireEvent.click(await screen.findByRole("button", { name: "Cancel order" }));
    const confirm = screen.getByRole("dialog", { name: "Cancel ORD-2042?" });
    fireEvent.click(within(confirm).getByRole("button", { name: "Cancel order" }));
    await waitFor(() => expect(lastSent(sent, "POST", "/orders/42/cancel")).toBeDefined());
    expect(await screen.findByText("Cancelled")).toBeInTheDocument();
  });

  it("marks the whole order delivered", async () => {
    render(<OrderPage />);
    fireEvent.click(await screen.findByRole("button", { name: "Mark delivered" }));
    await waitFor(() => expect(lastSent(sent, "POST", "/orders/42/deliver")).toBeDefined());
  });
});

describe("order invoice", () => {
  it("issues the invoice from the order and then links to it", async () => {
    search = new URLSearchParams("id=42");
    render(<OrderPage />);
    fireEvent.click(await screen.findByRole("button", { name: "Issue invoice" }));
    await waitFor(() => expect(lastSent(sent, "POST", "/money/orders/42/invoice")).toBeDefined());
    expect(await screen.findByRole("link", { name: "Invoice INV-305" })).toHaveAttribute(
      "href",
      "/money/invoices/view/?id=5",
    );
  });
});
