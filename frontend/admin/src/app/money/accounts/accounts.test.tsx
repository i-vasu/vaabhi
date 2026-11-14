import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { BackupStatus, SupplierBalance } from "@vaabhi/api-client";
import { notStarted, openPeriods, serveAccounts, startedStatus } from "@/test/accounts-fixtures";
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

  describe("once the books have started", () => {
    const started = { ...notStarted, startDate: "2026-04-01", shiprocketPartyId: 4 };
    const shiprocket: SupplierBalance = {
      partyId: 4,
      name: "Shiprocket",
      phone: null,
      gstin: null,
      state: null,
      billedRupees: 0,
      paidRupees: 0,
      tdsRupees: 0,
      balanceRupees: 0,
      oldestUnpaidDue: null,
      overdue: false,
    };
    const backupRun = {
      id: 1,
      kind: "BACKUP",
      status: "OK",
      startedAt: "2026-11-13T21:00:00Z",
      finishedAt: "2026-11-13T21:02:00Z",
      sizeBytes: 2048,
      objectKey: "db/2026-11-13.dump",
      message: null,
      notes: null,
      attachmentsCopied: 0,
      drilledOn: null,
      createdBy: null,
    };
    const backups: BackupStatus = {
      configured: true,
      locationLabel: "Cloudflare R2 (backups)",
      bucket: "vaabhi-backups",
      lastBackup: backupRun,
      lastCheck: null,
      lastDrill: null,
      missedLastNight: false,
      missedDaysLast7: 0,
      recent: [backupRun],
    };

    beforeEach(() => {
      server.use(
        http.get(`${API}/api/v1/admin/money/purchases/suppliers`, () => HttpResponse.json([shiprocket])),
        http.get(`${API}/api/v1/admin/accounts/backups/status`, () => HttpResponse.json(backups)),
      );
    });

    it("shows problems and a trial balance that is out", async () => {
      serveAccounts(started, { ...startedStatus, problemCount: 2, trialBalanceDifference: 12.5 });
      render(<AccountsPage />);
      expect(await screen.findByText("2 problems")).toBeInTheDocument();
      expect(await screen.findByText(/Out by/)).toBeInTheDocument();
      expect((await screen.findAllByText(/Books start on/)).length).toBeGreaterThan(0);
    });

    it("shows a single problem and nothing posted yet", async () => {
      serveAccounts(started, { ...startedStatus, lastSweepAt: null, problemCount: 1 });
      render(<AccountsPage />);
      expect(await screen.findByText("1 problem")).toBeInTheDocument();
      expect(await screen.findByText(/Nothing posted yet/)).toBeInTheDocument();
      let swept = false;
      server.use(
        http.post(`${API}/api/v1/admin/accounts/sweep`, () => {
          swept = true;
          return HttpResponse.json({ posted: 3, problems: 0 });
        }),
      );
      fireEvent.click(screen.getByRole("button", { name: "Post now" }));
      await waitFor(() => expect(swept).toBe(true));
    });
  });
});
