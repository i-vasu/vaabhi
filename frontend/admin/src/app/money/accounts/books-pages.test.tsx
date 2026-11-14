import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { api as realApi } from "@/lib/api";
import CreditNotesPage from "../credit-notes/page";
import PurchasesPage from "../purchases/page";
import BillsPage from "../purchases/bills/page";
import SupplierPage from "../purchases/supplier/page";
import AssetsPage from "./assets/page";
import BackupsPage from "./backups/page";
import BankPage from "./bank/page";
import ChartPage from "./chart/page";
import Gstr3bPage from "./gstr3b/page";
import JournalPage from "./journal/page";
import ProblemsPage from "./problems/page";
import StatementsPage from "./statements/page";
import YearEndPage from "./year-end/page";

type Api = typeof realApi;
type Accounts = Api["admin"]["accounts"];
type Purchases = Api["admin"]["purchases"];
/** What a client call resolves to, so every fixture below is checked against the API types. */
type Of<F> = F extends (...args: never[]) => Promise<infer T> ? T : never;

let search = new URLSearchParams();
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace: vi.fn() }), useSearchParams: () => search }));

const mocks = vi.hoisted(() => ({
  accounts: {
    chart: vi.fn(),
    journal: vi.fn(),
    problems: vi.fn(),
    sweep: vi.fn(),
    gstr3b: vi.fn(),
    gstr3bCsv: vi.fn(),
    statements: { profitAndLoss: vi.fn(), balanceSheet: vi.fn() },
    yearEnd: { get: vi.fn(), auditorPack: vi.fn(), close: vi.fn(), reopen: vi.fn(), saveStock: vi.fn() },
    backups: { status: vi.fn(), runNow: vi.fn(), recordDrill: vi.fn() },
    assets: { classes: vi.fn(), list: vi.fn(), createOpening: vi.fn(), dispose: vi.fn(), updateClass: vi.fn() },
    bank: { statements: vi.fn(), settlements: vi.fn(), remittances: vi.fn(), syncRazorpay: vi.fn() },
  },
  purchases: { suppliers: vi.fn(), supplier: vi.fn(), bills: vi.fn(), createPayment: vi.fn() },
  creditNotes: { list: vi.fn() },
}));
vi.mock("@/lib/api", () => ({
  api: { admin: { accounts: mocks.accounts, purchases: mocks.purchases, money: { creditNotes: mocks.creditNotes } } },
}));

const chart: Of<Accounts["chart"]> = [
  {
    id: 1,
    code: "1000",
    name: "Cash in hand",
    type: "ASSET",
    scheduleLine: "CASH_AND_BANK",
    parentId: null,
    systemKey: "CASH",
    active: true,
    balanceRupees: 1200,
  },
];

const journal: Of<Accounts["journal"]> = {
  items: [
    {
      id: 1,
      number: "JV-1",
      date: "2026-11-01",
      narration: "Opening stock brought in",
      sourceType: "MANUAL",
      sourceStep: "POST",
      totalRupees: 500,
      reversed: false,
    },
  ],
  total: 1,
  page: 1,
  size: 50,
};

const problems: Of<Accounts["problems"]> = [
  {
    sourceType: "INVOICE",
    sourceId: 5,
    sourceStep: "ISSUE",
    message: "No account for GST output",
    firstSeen: "2026-11-01T10:00:00Z",
    lastSeen: "2026-11-02T10:00:00Z",
  },
];

const gstRow = { taxableRupees: 1000, igstRupees: 0, cgstRupees: 25, sgstRupees: 25 };
const gstr3b: Of<Accounts["gstr3b"]> = {
  month: "2026-11",
  gstOn: true,
  outward: [{ section: "3.1(a)", description: "Outward taxable supplies", ...gstRow }],
  interState: [],
  itc: { section: "4(A)", description: "Eligible input tax credit", ...gstRow },
  setOff: [{ head: "CGST", outputRupees: 25, itcUsedRupees: 10, cashRupees: 15, carriedForwardRupees: 0 }],
};

const revenue = {
  line: "REVENUE_FROM_OPERATIONS" as const,
  label: "Revenue from operations",
  amountRupees: 100000,
  previousRupees: 0,
  accounts: [{ accountId: 2, code: "4000", name: "Sales", amountRupees: 100000, previousRupees: 0 }],
};
const profitAndLoss: Of<Accounts["statements"]["profitAndLoss"]> = {
  from: "2026-04-01",
  to: "2027-03-31",
  previousFrom: "2025-04-01",
  previousTo: "2026-03-31",
  income: [revenue],
  expenses: [],
  totalIncomeRupees: 100000,
  totalExpensesRupees: 0,
  profitRupees: 100000,
  previousProfitRupees: 0,
};
const balanceSheet: Of<Accounts["statements"]["balanceSheet"]> = {
  asOf: "2027-03-31",
  previousAsOf: "2026-03-31",
  equityAndLiabilities: [],
  assets: [],
  totalEquityAndLiabilitiesRupees: 0,
  totalAssetsRupees: 0,
  previousTotalEquityAndLiabilitiesRupees: 0,
  previousTotalAssetsRupees: 0,
  balanced: true,
};

const yearEnd: Of<Accounts["yearEnd"]["get"]> = {
  fy: "2026-27",
  from: "2026-04-01",
  to: "2027-03-31",
  closed: false,
  stockSaved: false,
  canReopen: false,
  stock: [{ stockItemId: 3, name: "Silk fabric", unit: "m", quantity: 10, rateRupees: 500, valueRupees: 5000 }],
  finishedGoodsRupees: 0,
  stockNote: null,
  closingStockRupees: 5000,
  stockBalanceRupees: 5000,
  adjustmentRupees: 0,
  profitRupees: 12000,
  checks: [{ key: "TRIAL_BALANCE", label: "Trial balance balances", ok: true, message: null }],
  runs: [],
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
  attachmentsCopied: 3,
  drilledOn: null,
  createdBy: null,
};
const backups: Of<Accounts["backups"]["status"]> = {
  configured: true,
  locationLabel: "Cloudflare R2 (backups)",
  bucket: "vaabhi-backups",
  lastBackup: backupRun,
  lastCheck: backupRun,
  lastDrill: null,
  missedLastNight: false,
  missedDaysLast7: 0,
  recent: [backupRun],
};

const assetClasses: Of<Accounts["assets"]["classes"]> = [
  {
    id: 1,
    name: "Furniture",
    lifeYears: 10,
    residualPercent: 5,
    assetAccountId: 10,
    assetAccountCode: "1500",
    accdepAccountId: 11,
    accdepAccountCode: "1510",
    active: true,
  },
];
const assets: Of<Accounts["assets"]["list"]> = [
  {
    id: 1,
    name: "Cutting table",
    classId: 1,
    className: "Furniture",
    costRupees: 25000,
    putToUseOn: "2026-04-01",
    openingAccdepRupees: 0,
    accumulatedRupees: 1000,
    bookValueRupees: 24000,
    billId: null,
    billNo: null,
    disposedOn: null,
    disposalProceedsRupees: null,
    hasPostedDepreciation: true,
  },
];

const bankStatements: Of<Accounts["bank"]["statements"]> = [
  {
    id: 1,
    fileName: "hdfc-november.csv",
    periodFrom: "2026-11-01",
    periodTo: "2026-11-30",
    openingRupees: 1000,
    closingRupees: 2000,
    lineCount: 20,
    unmatchedCount: 2,
    uploadedAt: "2026-12-01T10:00:00Z",
  },
];

const supplierBalance: Of<Purchases["suppliers"]>[number] = {
  partyId: 4,
  name: "Lakshmi Silks",
  phone: null,
  gstin: null,
  state: "Telangana",
  billedRupees: 10000,
  paidRupees: 4000,
  tdsRupees: 0,
  balanceRupees: 6000,
  oldestUnpaidDue: "2026-10-01",
  overdue: true,
};
const bills: Of<Purchases["bills"]> = [
  {
    id: 9,
    supplierName: "Lakshmi Silks",
    billNo: "B-101",
    billDate: "2026-10-01",
    dueDate: "2026-10-31",
    totalRupees: 10000,
    payableRupees: 10000,
    paidRupees: 4000,
    status: "PART_PAID",
    source: "MANUAL",
    posted: true,
  },
];
const supplier: Of<Purchases["supplier"]> = {
  balance: supplierBalance,
  email: null,
  pan: null,
  address: null,
  city: null,
  pinCode: null,
  tdsSectionId: null,
  entries: [
    {
      date: "2026-10-01",
      kind: "BILL",
      id: 9,
      reference: "B-101",
      billedRupees: 10000,
      paidRupees: 0,
      runningBalanceRupees: 10000,
    },
  ],
};

const creditNotes: Of<Api["admin"]["money"]["creditNotes"]["list"]> = {
  items: [
    {
      id: 9,
      number: "CN-9",
      invoiceId: 5,
      invoiceNumber: "INV-305",
      buyerName: "Ravi Kumar",
      issuedOn: "2026-11-02",
      totalRupees: 500,
      reason: "RETURN",
    },
  ],
  total: 1,
  page: 1,
  size: 20,
};

beforeEach(() => {
  search = new URLSearchParams();
  vi.clearAllMocks();
  mocks.accounts.chart.mockResolvedValue(chart);
  mocks.accounts.journal.mockResolvedValue(journal);
  mocks.accounts.problems.mockResolvedValue(problems);
  mocks.accounts.gstr3b.mockResolvedValue(gstr3b);
  mocks.accounts.statements.profitAndLoss.mockResolvedValue(profitAndLoss);
  mocks.accounts.statements.balanceSheet.mockResolvedValue(balanceSheet);
  mocks.accounts.yearEnd.get.mockResolvedValue(yearEnd);
  mocks.accounts.backups.status.mockResolvedValue(backups);
  mocks.accounts.assets.classes.mockResolvedValue(assetClasses);
  mocks.accounts.assets.list.mockResolvedValue(assets);
  mocks.accounts.bank.statements.mockResolvedValue(bankStatements);
  mocks.accounts.bank.settlements.mockResolvedValue([]);
  mocks.accounts.bank.remittances.mockResolvedValue([]);
  mocks.purchases.suppliers.mockResolvedValue([supplierBalance]);
  mocks.purchases.bills.mockResolvedValue(bills);
  mocks.purchases.supplier.mockResolvedValue(supplier);
  mocks.creditNotes.list.mockResolvedValue(creditNotes);
});

describe("books pages", () => {
  it("lists the chart of accounts", async () => {
    render(<ChartPage />);
    expect((await screen.findAllByText(/Cash in hand/)).length).toBeGreaterThan(0);
  });

  it("lists journal entries", async () => {
    render(<JournalPage />);
    expect((await screen.findAllByText(/Opening stock brought in/)).length).toBeGreaterThan(0);
  });

  it("shows posting problems", async () => {
    render(<ProblemsPage />);
    expect(await screen.findByText("No account for GST output")).toBeInTheDocument();
  });

  it("shows GSTR-3B for the month", async () => {
    search = new URLSearchParams({ month: "2026-11" });
    render(<Gstr3bPage />);
    expect((await screen.findAllByText("Outward taxable supplies")).length).toBeGreaterThan(0);
    expect(mocks.accounts.gstr3b).toHaveBeenCalled();
  });

  it("shows the profit and loss statement", async () => {
    render(<StatementsPage />);
    expect((await screen.findAllByText(/Revenue from operations/)).length).toBeGreaterThan(0);
  });

  it("shows the year-end checks and closing stock", async () => {
    render(<YearEndPage />);
    expect((await screen.findAllByText(/Trial balance balances/)).length).toBeGreaterThan(0);
    expect(mocks.accounts.yearEnd.get).toHaveBeenCalled();
  });

  it("shows where backups go and the recent runs", async () => {
    render(<BackupsPage />);
    expect((await screen.findAllByText(/Cloudflare R2 \(backups\)/)).length).toBeGreaterThan(0);
  });

  it("lists fixed assets with their class", async () => {
    render(<AssetsPage />);
    expect((await screen.findAllByText(/Cutting table/)).length).toBeGreaterThan(0);
  });

  it("lists uploaded bank statements", async () => {
    render(<BankPage />);
    expect((await screen.findAllByText(/hdfc-november\.csv/)).length).toBeGreaterThan(0);
  });

  it("lists suppliers with what is owed", async () => {
    render(<PurchasesPage />);
    expect((await screen.findAllByText(/Lakshmi Silks/)).length).toBeGreaterThan(0);
  });

  it("lists supplier bills", async () => {
    render(<BillsPage />);
    expect((await screen.findAllByText(/B-101/)).length).toBeGreaterThan(0);
  });

  it("shows a supplier's statement", async () => {
    search = new URLSearchParams({ id: "4" });
    render(<SupplierPage />);
    expect((await screen.findAllByText(/B-101/)).length).toBeGreaterThan(0);
    await waitFor(() => expect(mocks.purchases.supplier).toHaveBeenCalledWith(4));
  });

  it("lists credit notes", async () => {
    render(<CreditNotesPage />);
    expect((await screen.findAllByText(/CN-9 · Ravi Kumar/)).length).toBeGreaterThan(0);
  });
});
