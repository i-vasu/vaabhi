import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { notStarted, openPeriods, serveAccounts } from "@/test/accounts-fixtures";
import { API, server } from "@/test/server";
import AccountsPage from "./page";
import EntryPage from "./entry/page";
import ManualJournalPage from "./manual/page";
import OpeningBalancesPage from "./opening/page";
import PeriodsPage from "./periods/page";
import TrialBalancePage from "./trial-balance/page";

let search = new URLSearchParams();
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace: vi.fn() }), useSearchParams: () => search }));

beforeEach(() => {
  search = new URLSearchParams();
  serveAccounts(notStarted);
});

describe("accounts", () => {
  it("says the books have not started when there is no start date", async () => {
    render(<AccountsPage />);
    expect(await screen.findByText("Books haven't started. Set the start date to begin.")).toBeInTheDocument();
  });

  it("shows the trial balance as balanced", async () => {
    render(<TrialBalancePage />);
    expect(await screen.findByText("Balanced ✓")).toBeInTheDocument();
  });

  it("links an entry's invoice to the invoice page", async () => {
    search = new URLSearchParams({ id: "7" });
    render(<EntryPage />);
    expect(await screen.findByRole("link", { name: "INV-305" })).toHaveAttribute("href", "/money/invoices/view/?id=5");
  });

  it("enables manual Save only when debits equal credits", async () => {
    render(<ManualJournalPage />);
    await screen.findAllByRole("option", { name: "1000 · Cash", hidden: true });
    fireEvent.change(screen.getByLabelText("Narration"), { target: { value: "Owner top-up" } });
    fireEvent.change(screen.getByLabelText("Account 1"), { target: { value: "1" } });
    fireEvent.change(screen.getByLabelText("Account 2"), { target: { value: "2" } });
    fireEvent.change(screen.getByLabelText("Debit 1"), { target: { value: "100.10" } });
    fireEvent.change(screen.getByLabelText("Credit 2"), { target: { value: "100" } });
    expect(screen.getByText(/Difference/)).toHaveTextContent("Difference ₹ 0.1");
    expect(screen.getByRole("button", { name: "Save" })).toBeDisabled();
    fireEvent.change(screen.getByLabelText("Credit 2"), { target: { value: "100.10" } });
    expect(screen.getByText(/Difference/)).toHaveTextContent("Difference ₹ 0");
    expect(screen.getByRole("button", { name: "Save" })).toBeEnabled();
  });

  it("shows where the opening balance remainder goes", async () => {
    render(<OpeningBalancesPage />);
    fireEvent.change(await screen.findByLabelText("Debit 1000"), { target: { value: "5000" } });
    expect(screen.getByText("₹ 5,000 will go to Opening balance equity")).toBeInTheDocument();
  });

  it("locks a month through the lock endpoint", async () => {
    let called = "";
    server.use(
      http.put(`${API}/api/v1/admin/accounts/periods/:month/lock`, ({ params }) => {
        called = String(params.month);
        return HttpResponse.json(openPeriods);
      }),
    );
    render(<PeriodsPage />);
    fireEvent.click(await screen.findByRole("button", { name: "Lock" }));
    fireEvent.click(await screen.findByRole("button", { name: "Lock month" }));
    await waitFor(() => expect(called).toBe("2026-10"));
  });
});
