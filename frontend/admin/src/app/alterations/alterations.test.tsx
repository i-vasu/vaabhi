import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { lastSent, serveTailoring, type Sent } from "@/test/tailoring-fixtures";
import NewAlterationPage from "./new/page";
import AlterationsPage from "./page";
import AlterationPage from "./view/page";

const replace = vi.fn();
let search = new URLSearchParams();
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace }), useSearchParams: () => search }));

let sent: Sent[];
beforeEach(() => {
  sent = serveTailoring().sent;
  replace.mockReset();
  search = new URLSearchParams();
});

describe("alterations list", () => {
  it("lists open alterations with what is due", async () => {
    render(<AlterationsPage />);
    const row = await screen.findByRole("link", { name: /ALT-109 · Meera Iyer/ });
    expect(within(row).getByText("₹ 400 due")).toBeInTheDocument();
    expect(lastSent(sent, "GET", "/alterations")?.query).toContain("filter=OPEN");
  });
});

describe("new alteration", () => {
  it("needs the terms accepted, then books a free fix on the shop's own order", async () => {
    search = new URLSearchParams("clientId=7&orderId=42");
    render(<NewAlterationPage />);

    expect(await screen.findByText(/garments from their order/)).toBeInTheDocument();
    expect(screen.getByRole("switch", { name: "Charge for this alteration" })).toHaveAttribute("aria-checked", "false");
    const garment = screen.getByRole("group", { name: "Garment 1" });
    fireEvent.change(within(garment).getByLabelText("Garment"), { target: { value: "3 dart blouse" } });
    fireEvent.change(within(garment).getByLabelText("Work"), { target: { value: "LENGTH" } });

    fireEvent.click(screen.getByRole("button", { name: "Book alteration" }));
    expect(await screen.findByText("The customer must accept the alteration terms.")).toBeInTheDocument();

    expect(await screen.findByText(/best-effort/)).toBeInTheDocument();
    fireEvent.click(screen.getByLabelText("The customer accepts these terms"));
    fireEvent.click(screen.getByRole("button", { name: "Book alteration" }));
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/alterations/view/?id=9"));
    expect(lastSent(sent, "POST", "/alterations")?.body).toMatchObject({
      clientId: 7,
      orderId: 42,
      paid: false,
      items: [{ kind: "LENGTH", garment: "3 dart blouse", costRupees: 0 }],
      undertakingAccepted: true,
    });
  });

  it("charges per garment when paid", async () => {
    search = new URLSearchParams("clientId=7");
    render(<NewAlterationPage />);
    await screen.findByRole("button", { name: "Change" });
    expect(screen.getByRole("switch", { name: "Charge for this alteration" })).toHaveAttribute("aria-checked", "true");

    fireEvent.change(screen.getByLabelText("Garment"), { target: { value: "Lehenga" } });
    fireEvent.change(screen.getByLabelText("Charge (₹)"), { target: { value: "250" } });
    fireEvent.click(screen.getByRole("button", { name: /Add garment/ }));
    const second = screen.getByRole("group", { name: "Garment 2" });
    fireEvent.change(within(second).getByLabelText("Garment"), { target: { value: "Blouse" } });
    fireEvent.change(within(second).getByLabelText("Charge (₹)"), { target: { value: "150" } });
    expect(screen.getByText("Total ₹ 400")).toBeInTheDocument();

    fireEvent.click(screen.getByLabelText("The customer accepts these terms"));
    fireEvent.click(screen.getByRole("button", { name: "Book alteration" }));
    await waitFor(() =>
      expect(lastSent(sent, "POST", "/alterations")?.body).toMatchObject({
        paid: true,
        orderId: null,
        items: [{ costRupees: 250 }, { costRupees: 150 }],
      }),
    );
  });
});

describe("alteration page", () => {
  it("collects what is due on hand-over", async () => {
    search = new URLSearchParams("id=9");
    render(<AlterationPage />);
    expect(await screen.findByRole("heading", { name: "ALT-109" })).toBeInTheDocument();
    expect(screen.getByText("Shorten 1 inch")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Hand over" }));
    const dialog = screen.getByRole("dialog", { name: "Hand over" });
    expect(within(dialog).getByLabelText("Collected now (₹)")).toHaveValue("400");
    fireEvent.change(within(dialog).getByLabelText("Paid by"), { target: { value: "UPI" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Hand over" }));
    await waitFor(() =>
      expect(lastSent(sent, "POST", "/alterations/9/progress")?.body).toEqual({
        status: "DELIVERED",
        collectRupees: 400,
        mode: "UPI",
      }),
    );
    expect(await screen.findByText("Delivered")).toBeInTheDocument();
  });

  it("can be cancelled while open", async () => {
    search = new URLSearchParams("id=9");
    render(<AlterationPage />);
    fireEvent.click(await screen.findByRole("button", { name: "Cancel alteration" }));
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Cancel alteration" }));
    await waitFor(() =>
      expect(lastSent(sent, "POST", "/alterations/9/progress")?.body).toEqual({
        status: "CANCELLED",
        collectRupees: 0,
        mode: null,
      }),
    );
  });
});
