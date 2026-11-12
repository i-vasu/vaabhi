import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { notStarted, serveAccounts } from "@/test/accounts-fixtures";
import AccountsPage from "./page";
import EntryPage from "./entry/page";
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
});
