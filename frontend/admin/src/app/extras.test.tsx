import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { activityHref } from "@/lib/activity";
import { qty } from "@/lib/extras";
import { lastSent, serveTailoring, type Sent } from "@/test/tailoring-fixtures";
import ActivityPage from "./activity/page";
import CouponsPage from "./coupons/page";
import CustomerPage from "./customers/view/page";
import GalleryPage from "./design/page";
import HelpPage from "./help/page";
import MessagesPage from "./messages/page";
import NotificationsPage from "./notifications/page";
import NewOrderPage from "./orders/new/page";
import OrderPage from "./orders/view/page";
import PrintOrderPage from "./print/order/page";
import PrintAlterationPage from "./print/alteration/page";
import RewardsPage from "./rewards/page";
import StockPage from "./stock/page";
import StockItemPage from "./stock/view/page";

const replace = vi.fn();
let search = new URLSearchParams();
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace }), useSearchParams: () => search }));

let sent: Sent[];
beforeEach(() => {
  sent = serveTailoring().sent;
  search = new URLSearchParams();
});

const type = (label: string | RegExp, value: string, scope: HTMLElement = document.body) =>
  fireEvent.change(within(scope).getByLabelText(label), { target: { value } });

describe("helpers", () => {
  it("formats quantities and links activity", () => {
    expect(qty(3.5)).toBe("3.5");
    expect(qty(2)).toBe("2");
    expect(activityHref({ refType: "ORDER", refId: 42 })).toBe("/orders/view/?id=42");
    expect(activityHref({ refType: "SETTINGS", refId: null })).toBe("/settings/");
    expect(activityHref({ refType: null, refId: null })).toBeNull();
  });
});

describe("stock", () => {
  it("lists items with what is left and records stock going out", async () => {
    render(<StockPage />);
    expect(await screen.findByText("3.5 metres")).toBeInTheDocument();
    expect(screen.getByText("Low")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "In / out" }));
    const dialog = screen.getByRole("dialog", { name: "Silk lining: in or out" });
    type("How much (metres)", "1.5", dialog);
    fireEvent.click(within(dialog).getByRole("button", { name: "Save" }));
    await waitFor(() =>
      expect(lastSent(sent, "POST", "/stock/31/movements")?.body).toEqual({
        kind: "OUT",
        quantity: 1.5,
        orderId: null,
        note: null,
      }),
    );
  });

  it("adds a stock item", async () => {
    render(<StockPage />);
    fireEvent.click(await screen.findByRole("button", { name: /Add item/ }));
    const dialog = screen.getByRole("dialog", { name: "New stock item" });
    type("Name", "Cotton thread", dialog);
    type("Unit", "PCS", dialog);
    type("Reorder at", "10", dialog);
    type("Cost per unit (₹)", "25", dialog);
    fireEvent.click(within(dialog).getByRole("button", { name: "Save item" }));
    await waitFor(() =>
      expect(lastSent(sent, "POST", "/stock")?.body).toEqual({
        name: "Cotton thread",
        sku: null,
        category: null,
        unit: "PCS",
        reorderLevel: 10,
        costRupees: 25,
      }),
    );
  });

  it("shows an item's ledger", async () => {
    search = new URLSearchParams("id=31");
    render(<StockItemPage />);
    expect(await screen.findByText("balance 3.5")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "ORD-2042" })).toHaveAttribute("href", "/orders/view/?id=42");
  });
});

describe("coupons and rewards", () => {
  it("lists coupons and creates one", async () => {
    render(<CouponsPage />);
    expect(await screen.findByText(/10% off/)).toBeInTheDocument();
    expect(screen.getByText("3 / 100 used")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /New coupon/ }));
    const dialog = screen.getByRole("dialog", { name: "New coupon" });
    type("Code", "SAVE200", dialog);
    fireEvent.click(within(dialog).getByRole("radio", { name: "₹ off" }));
    type("Discount (₹)", "200", dialog);
    fireEvent.click(within(dialog).getByRole("button", { name: "Save coupon" }));
    await waitFor(() =>
      expect(lastSent(sent, "POST", "/engage/coupons")?.body).toMatchObject({
        code: "SAVE200",
        kind: "AMOUNT",
        value: 200,
        minOrderRupees: 0,
        active: true,
      }),
    );
  });

  it("applies a coupon on a new order", async () => {
    search = new URLSearchParams("clientId=7");
    render(<NewOrderPage />);
    await screen.findByText("Meera Iyer");
    const item = await screen.findByRole("group", { name: "Item 1" });
    fireEvent.change(within(item).getByLabelText("From price list"), { target: { value: "11" } });
    type("Coupon code", "diwali10");
    fireEvent.click(screen.getByRole("button", { name: "Apply" }));
    expect(await screen.findByText("DIWALI10: ₹ 110 off")).toBeInTheDocument();
    expect(screen.getByLabelText("Discount (%)")).toHaveValue("10");
    expect(lastSent(sent, "GET", "/engage/coupons/check")?.query).toContain("code=diwali10");
  });

  it("saves reward settings", async () => {
    render(<RewardsPage />);
    await screen.findByLabelText("Points per ₹100");
    type("Points per ₹100", "2");
    expect(screen.getByText(/earns 20 points/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Save" }));
    await waitFor(() =>
      expect(lastSent(sent, "PUT", "/engage/rewards/settings")?.body).toEqual({
        enabled: true,
        pointsPer100: 2,
        rupeesPerPoint: 1,
      }),
    );
  });

  it("shows a customer's points and redeems some", async () => {
    search = new URLSearchParams("id=7");
    render(<CustomerPage />);
    expect(await screen.findByText(/24 points/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Redeem / adjust" }));
    const dialog = screen.getByRole("dialog", { name: "Reward points" });
    type("Points to redeem", "10", dialog);
    fireEvent.click(within(dialog).getByRole("button", { name: "Save" }));
    await waitFor(() =>
      expect(lastSent(sent, "POST", "/engage/rewards/clients/7")?.body).toEqual({
        kind: "REDEEMED",
        points: 10,
        orderId: null,
        note: null,
      }),
    );
  });
});

describe("messages", () => {
  it("prepares an order message and opens WhatsApp", async () => {
    search = new URLSearchParams("id=42");
    const open = vi.spyOn(window, "open").mockReturnValue(null);
    render(<OrderPage />);
    fireEvent.click(await screen.findByRole("button", { name: "Message" }));
    const dialog = screen.getByRole("dialog", { name: "Message the customer" });
    type("Message", "ORDER_READY", dialog);
    fireEvent.click(within(dialog).getByRole("button", { name: "Prepare message" }));
    await waitFor(() =>
      expect(lastSent(sent, "POST", "/engage/messages")?.body).toMatchObject({
        purpose: "ORDER_READY",
        orderId: 42,
        clientId: 7,
        channel: "WHATSAPP",
      }),
    );
    await waitFor(() =>
      expect(open).toHaveBeenCalledWith("https://wa.me/919876500002?text=Hi%20Meera", "_blank", "noopener"),
    );
    expect(within(dialog).getByRole("link", { name: "Open in WhatsApp" })).toBeInTheDocument();
    open.mockRestore();
  });

  it("lists messages sent", async () => {
    render(<MessagesPage />);
    expect(await screen.findByText("Order ready")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Send again" })).toHaveAttribute(
      "href",
      "https://wa.me/919876500002?text=Hi%20Meera",
    );
  });
});

describe("gallery, activity and notifications", () => {
  it("shows the gallery and removes a photo", async () => {
    render(<GalleryPage />);
    expect(await screen.findByRole("img", { name: "Boat neck" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Remove Boat neck" }));
    await waitFor(() => expect(lastSent(sent, "DELETE", "/gallery/5")).toBeDefined());
    fireEvent.click(screen.getByRole("radio", { name: "Customer garments" }));
    await waitFor(() => expect(lastSent(sent, "GET", "/gallery")?.query).toContain("kind=CUSTOMER"));
  });

  it("shows the activity log with links", async () => {
    render(<ActivityPage />);
    expect(await screen.findByRole("link", { name: /₹300 received/ })).toHaveAttribute("href", "/orders/view/?id=42");
    fireEvent.click(screen.getByRole("radio", { name: "Settings" }));
    await waitFor(() => expect(lastSent(sent, "GET", "/activity")?.query).toContain("kind=SETTINGS_CHANGED"));
  });

  it("shows alerts and recent updates, and marks them seen", async () => {
    render(<NotificationsPage />);
    expect(await screen.findByRole("link", { name: /stock items running low/ })).toHaveAttribute(
      "href",
      "/stock/?low=1",
    );
    expect(screen.getByText("1 new")).toBeInTheDocument();
    await waitFor(() => expect(lastSent(sent, "POST", "/me/notifications/seen")).toBeDefined());
  });
});

describe("print and help", () => {
  it("prints the order slip with measurements on the chosen paper", async () => {
    search = new URLSearchParams("id=42");
    render(<PrintOrderPage />);
    const slip = await screen.findByRole("article", { name: "Order ORD-2042" });
    expect(within(slip).getByText(/bust 34/)).toBeInTheDocument();
    expect(within(slip).getByText("₹ 990")).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Paper"), { target: { value: "THERMAL_80MM" } });
    expect(slip.className).toContain("max-w-[72mm]");
  });

  it("prints the alteration slip with the undertaking", async () => {
    search = new URLSearchParams("id=9");
    render(<PrintAlterationPage />);
    const slip = await screen.findByRole("article", { name: "Alteration ALT-109" });
    expect(within(slip).getByText(/best-effort/)).toBeInTheDocument();
  });

  it("answers common questions", () => {
    render(<HelpPage />);
    expect(screen.getByText("How do I book an order?")).toBeInTheDocument();
  });
});
