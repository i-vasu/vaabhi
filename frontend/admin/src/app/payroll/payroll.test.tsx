import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { serveStaff } from "@/test/staff-fixtures";
import { lastSent, serveTailoring, type Sent } from "@/test/tailoring-fixtures";
import PayrollPage from "./page";
import ProductionPage from "./production/page";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
}));

let sent: Sent[];
beforeEach(() => {
  sent = serveTailoring().sent;
  serveStaff(sent);
});

describe("payroll", () => {
  it("shows the month's payslips with what makes up the pay", async () => {
    render(<PayrollPage />);
    expect(await screen.findByText("Finalised")).toBeInTheDocument();
    expect(lastSent(sent, "GET", `/payroll/months/${new Date().toISOString().slice(0, 7)}`)).toBeDefined();
    fireEvent.click(screen.getByRole("button", { name: /^Ravi Tailor/ }));
    const slip = screen.getByLabelText("Payslip of Ravi Tailor");
    expect(within(slip).getByText("Overtime 2 h")).toBeInTheDocument();
    expect(within(slip).getByText("− ₹ 80")).toBeInTheDocument();
    expect(within(slip).getByText("Advance taken back")).toBeInTheDocument();
  });

  it("pays a payslip", async () => {
    render(<PayrollPage />);
    fireEvent.click(await screen.findByRole("button", { name: "Pay Ravi Tailor" }));
    const dialog = screen.getByRole("dialog", { name: "Pay Ravi Tailor" });
    fireEvent.change(within(dialog).getByLabelText("Paid by"), { target: { value: "BANK" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Mark paid" }));
    await waitFor(() => expect(lastSent(sent, "POST", "/payroll/payslips/21/pay")?.body).toEqual({ mode: "BANK" }));
  });

  it("reopens a month nobody was paid for yet", async () => {
    render(<PayrollPage />);
    fireEvent.click(await screen.findByRole("button", { name: "Reopen month" }));
    expect(await screen.findByText("Preview")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Finalise month" })).toBeInTheDocument();
  });
});

describe("production ledger", () => {
  it("lists finished stages at their piece rates and filters by person", async () => {
    render(<ProductionPage />);
    expect(await screen.findByText("2 pieces · ₹ 300")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "ORD-2042" })).toHaveAttribute("href", "/orders/view/?id=42");
    fireEvent.change(await screen.findByLabelText("Person"), { target: { value: "3" } });
    await waitFor(() => expect(lastSent(sent, "GET", "/payroll/production")?.query).toContain("staffId=3"));
  });
});
