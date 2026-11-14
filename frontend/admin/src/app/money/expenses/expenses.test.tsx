import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { serveTailoring } from "@/test/tailoring-fixtures";
import ExpensesPage from "./page";

let search = new URLSearchParams();
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace: vi.fn() }), useSearchParams: () => search }));

beforeEach(() => {
  serveTailoring();
  search = new URLSearchParams();
});

describe("expenses", () => {
  it("shows payroll-owned expenses as links to Payroll, with no edit controls", async () => {
    render(<ExpensesPage />);

    const payroll = await screen.findByRole("link", { name: /from Payroll/ });
    expect(payroll).toHaveAttribute("href", "/payroll/");
    expect(screen.queryByRole("button", { name: /from Payroll/ })).not.toBeInTheDocument();

    // A manual expense is still an editable row.
    expect(screen.getByRole("button", { name: /Shop rent/ })).toBeInTheDocument();
  });
});
