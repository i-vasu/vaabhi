import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { monthLabel, monthOf, shiftMonth } from "@/lib/money";
import { ledger, lastSent, serveTailoring, type Sent } from "@/test/tailoring-fixtures";
import DuesPage from "./dues/page";
import ExpensesPage from "./expenses/page";
import InvoicesPage from "./invoices/page";
import InvoicePage from "./invoices/view/page";
import LedgerPage, { sourceHref } from "./ledger/page";
import MoneyPage from "./page";

let search = new URLSearchParams();
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace: vi.fn() }), useSearchParams: () => search }));

let sent: Sent[];
beforeEach(() => {
  sent = serveTailoring().sent;
  search = new URLSearchParams();
});

describe("months", () => {
  it("knows month ends, leap years and steps across years", () => {
    expect(monthOf("2028-02-10")).toEqual({ from: "2028-02-01", to: "2028-02-29" });
    expect(shiftMonth(monthOf("2026-12-15"), 1)).toEqual({ from: "2027-01-01", to: "2027-01-31" });
    expect(shiftMonth(monthOf("2026-01-15"), -1).from).toBe("2025-12-01");
    expect(monthLabel(monthOf("2026-11-02"))).toBe("November 2026");
  });

  it("links ledger entries to where they came from", () => {
    expect(sourceHref(ledger.entries[1]!)).toBe("/orders/view/?id=42");
    expect(sourceHref(ledger.entries[0]!)).toBeNull();
  });
});

describe("money overview", () => {
  it("shows money in and out for the month and what is owed", async () => {
    render(<MoneyPage />);
    expect(await screen.findByText("₹ 300")).toBeInTheDocument();
    expect(screen.getByText("₹ 15,050")).toBeInTheDocument();
    expect(await screen.findByText("₹ 1,090")).toBeInTheDocument();
    expect(screen.getByText("₹ 400 already handed over")).toBeInTheDocument();
    expect(lastSent(sent, "GET", "/money/ledger")?.query).toMatch(/from=\d{4}-\d{2}-01/);

    fireEvent.click(screen.getByRole("button", { name: "Previous month" }));
    await waitFor(() => expect(sent.filter((s) => s.path.endsWith("/money/ledger"))).toHaveLength(2));
  });
});

describe("dues", () => {
  it("lists what is owed and filters to handed-over work", async () => {
    render(<DuesPage />);
    expect(await screen.findByRole("link", { name: /ALT-109/ })).toHaveAttribute("href", "/alterations/view/?id=9");
    expect(screen.getByRole("link", { name: /ORD-2042/ })).toHaveAttribute("href", "/orders/view/?id=42");
    fireEvent.click(screen.getByRole("radio", { name: "Handed over" }));
    expect(screen.queryByRole("link", { name: /ORD-2042/ })).not.toBeInTheDocument();
  });
});

describe("ledger", () => {
  it("shows the balance through the month and filters by kind", async () => {
    render(<LedgerPage />);
    const totals = await screen.findByLabelText("Ledger totals");
    expect(within(totals).getByText("₹ -9,750")).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: "ORD-2042" })[0]).toHaveAttribute("href", "/orders/view/?id=42");
    expect(screen.getByText("− ₹ 15,000")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("radio", { name: "Refund" }));
    await waitFor(() => expect(lastSent(sent, "GET", "/money/ledger")?.query).toContain("kind=REFUND"));
  });
});

describe("expenses", () => {
  it("lists the month and adds an expense", async () => {
    render(<ExpensesPage />);
    expect(await screen.findByText("Shop rent")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /Add expense/ }));
    const dialog = screen.getByRole("dialog", { name: "Add expense" });
    fireEvent.click(within(dialog).getByRole("button", { name: "Save expense" }));
    expect(await within(dialog).findByText("Say what it was for.")).toBeInTheDocument();

    fireEvent.change(within(dialog).getByLabelText("What for"), { target: { value: "Thread" } });
    fireEvent.change(within(dialog).getByLabelText("Amount (₹)"), { target: { value: "250" } });
    fireEvent.change(within(dialog).getByLabelText("Category"), { target: { value: "MATERIAL" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Save expense" }));
    await waitFor(() =>
      expect(lastSent(sent, "POST", "/money/expenses")?.body).toMatchObject({
        description: "Thread",
        amountRupees: 250,
        category: "MATERIAL",
        mode: "CASH",
        paidTo: null,
      }),
    );
  });

  it("edits and removes an expense, and filters by category", async () => {
    render(<ExpensesPage />);
    fireEvent.click(await screen.findByRole("button", { name: /Shop rent/ }));
    const dialog = screen.getByRole("dialog", { name: "Edit expense" });
    expect(within(dialog).getByLabelText("Amount (₹)")).toHaveValue("15000");
    fireEvent.click(within(dialog).getByRole("button", { name: /Remove/ }));
    await waitFor(() => expect(lastSent(sent, "DELETE", "/money/expenses/3")).toBeDefined());

    fireEvent.change(screen.getByLabelText("Category"), { target: { value: "RENT" } });
    await waitFor(() => expect(lastSent(sent, "GET", "/money/expenses")?.query).toContain("category=RENT"));
  });
});

describe("invoices", () => {
  it("lists the month's invoices with GST and searches all months", async () => {
    render(<InvoicesPage />);
    expect(await screen.findByRole("link", { name: /INV-305/ })).toHaveAttribute("href", "/money/invoices/view/?id=5");
    expect(screen.getByText("₹ 47.14")).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Search invoices"), { target: { value: "INV-305" } });
    fireEvent.submit(screen.getByRole("search"));
    await waitFor(() => expect(lastSent(sent, "GET", "/money/invoices")?.query).toBe("?page=1&pageSize=20&q=INV-305"));
  });

  it("shows the invoice with its GST split and can cancel it", async () => {
    search = new URLSearchParams("id=5");
    render(<InvoicePage />);
    expect(await screen.findByRole("heading", { name: "INV-305" })).toBeInTheDocument();
    expect(screen.getByText("3 dart blouse (Maroon)")).toBeInTheDocument();
    expect(screen.getByText("CGST 2.5%")).toBeInTheDocument();
    expect(screen.getByText("Prices include GST.")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Cancel invoice" }));
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Cancel invoice" }));
    await waitFor(() => expect(lastSent(sent, "POST", "/money/invoices/5/cancel")).toBeDefined());
    expect(await screen.findByText("Cancelled")).toBeInTheDocument();
  });
});
