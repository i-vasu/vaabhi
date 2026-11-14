import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { api as realApi } from "@/lib/api";
import Gstr1Page from "../../reports/gst/page";
import CreditNotesPage from "../credit-notes/page";
import PurchasesPage from "../purchases/page";
import BillsPage from "../purchases/bills/page";
import SupplierPage from "../purchases/supplier/page";
import AssetsPage from "./assets/page";
import BackupsPage from "./backups/page";
import BankPage from "./bank/page";
import ChartPage from "./chart/page";
import Gstr3bPage from "./gstr3b/page";
import InputGstPage from "./input-gst/page";
import JournalPage from "./journal/page";
import LedgerPage from "./ledger/page";
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
    ledger: vi.fn(),
    inputGst: vi.fn(),
    inputGstCsv: vi.fn(),
    journal: vi.fn(),
    problems: vi.fn(),
    sweep: vi.fn(),
    gstr3b: vi.fn(),
    gstr3bCsv: vi.fn(),
    statements: { profitAndLoss: vi.fn(), balanceSheet: vi.fn() },
    yearEnd: { get: vi.fn(), auditorPack: vi.fn(), close: vi.fn(), reopen: vi.fn(), saveStock: vi.fn() },
    backups: { status: vi.fn(), runNow: vi.fn(), recordDrill: vi.fn() },
    tds: { sections: vi.fn() },
    assets: { classes: vi.fn(), list: vi.fn(), createOpening: vi.fn(), dispose: vi.fn(), updateClass: vi.fn() },
    bank: { statements: vi.fn(), settlements: vi.fn(), remittances: vi.fn(), syncRazorpay: vi.fn() },
  },
  purchases: {
    suppliers: vi.fn(),
    supplier: vi.fn(),
    bills: vi.fn(),
    createPayment: vi.fn(),
    createSupplier: vi.fn(),
    updateSupplier: vi.fn(),
  },
  settings: { states: vi.fn() },
  creditNotes: { list: vi.fn() },
  gstr1: { month: vi.fn(), csv: vi.fn() },
}));
vi.mock("@/lib/api", () => ({
  api: {
    admin: {
      accounts: mocks.accounts,
      purchases: mocks.purchases,
      settings: mocks.settings,
      money: { creditNotes: mocks.creditNotes, gstr1: mocks.gstr1 },
    },
  },
}));

const ledger: Of<Accounts["ledger"]> = {
  accountId: 1,
  accountCode: "1000",
  accountName: "Cash in hand",
  type: "ASSET",
  from: "2026-11-01",
  to: "2026-11-30",
  openingRupees: 1000,
  totalDebitRupees: 500,
  totalCreditRupees: 200,
  closingRupees: 1300,
  lines: [
    {
      entryId: 3,
      number: "JV-3",
      date: "2026-11-05",
      narration: "Fabric purchase paid",
      partyName: "Lakshmi Silks",
      memo: null,
      debitRupees: 0,
      creditRupees: 200,
      balanceRupees: 800,
    },
  ],
};

const inputGst: Of<Accounts["inputGst"]> = [
  {
    month: "2026-11",
    taxableRupees: 10000,
    cgstRupees: 250,
    sgstRupees: 250,
    igstRupees: 0,
    gatewayGstRupees: 36,
    totalRupees: 536,
  },
];

const gstr1: Of<Api["admin"]["money"]["gstr1"]["month"]> = {
  month: "2026-11",
  gstOn: true,
  b2b: [
    {
      columns: { "Invoice Number": "INV-305", "GSTIN/UIN of Recipient": "36ABCDE1234F1Z5" },
      taxable: 1000,
      igst: 0,
      cgst: 25,
      sgst: 25,
      value: 1050,
    },
  ],
  b2cl: [],
  b2cs: [],
  cdnr: [],
  cdnur: [],
  hsn: [
    {
      hsn: "6204",
      description: "Blouses",
      uqc: "NOS",
      quantity: 2,
      value: 1050,
      rate: 5,
      taxable: 1000,
      igst: 0,
      cgst: 25,
      sgst: 25,
      cess: 0,
    },
  ],
  docs: [{ nature: "Invoices for outward supply", from: "INV-301", to: "INV-305", total: 5, cancelled: 0 }],
};

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
  mocks.accounts.ledger.mockResolvedValue(ledger);
  mocks.accounts.inputGst.mockResolvedValue(inputGst);
  mocks.gstr1.month.mockResolvedValue(gstr1);
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
  mocks.settings.states.mockResolvedValue([{ code: "36", name: "Telangana" }]);
  mocks.accounts.tds.sections.mockResolvedValue([]);
  mocks.accounts.yearEnd.saveStock.mockResolvedValue(yearEnd);
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

  it("shows an account's ledger lines", async () => {
    search = new URLSearchParams({ id: "1" });
    render(<LedgerPage />);
    expect((await screen.findAllByText(/Fabric purchase paid/)).length).toBeGreaterThan(0);
    expect(mocks.accounts.ledger).toHaveBeenCalled();
  });

  it("totals input GST by month", async () => {
    render(<InputGstPage />);
    // The footer row only renders once there are months, so two "Total" cells means the data is in.
    await waitFor(() => expect(screen.getAllByText("Total")).toHaveLength(2));
    expect(mocks.accounts.inputGst).toHaveBeenCalled();
  });

  it("shows the GSTR-1 sections for the month", async () => {
    render(<Gstr1Page />);
    expect((await screen.findAllByText("B2B invoices")).length).toBeGreaterThan(0);
    expect((await screen.findAllByText(/Blouses/)).length).toBeGreaterThan(0);
  });

  it("lists credit notes", async () => {
    render(<CreditNotesPage />);
    expect((await screen.findAllByText(/CN-9 · Ravi Kumar/)).length).toBeGreaterThan(0);
  });

  it("opens the supplier's details for editing", async () => {
    search = new URLSearchParams({ id: "4" });
    render(<SupplierPage />);
    fireEvent.click(await screen.findByRole("button", { name: "Edit" }));
    await waitFor(() => expect(mocks.settings.states).toHaveBeenCalled());
    expect(mocks.accounts.tds.sections).toHaveBeenCalled();
  });

  it("opens the payment sheet with the supplier's open bills", async () => {
    search = new URLSearchParams({ id: "4" });
    render(<SupplierPage />);
    fireEvent.click(await screen.findByRole("button", { name: "Pay" }));
    expect(await screen.findByRole("button", { name: "Record payment" })).toBeInTheDocument();
    await waitFor(() => expect(mocks.purchases.bills).toHaveBeenCalledWith({ partyId: 4, status: "OPEN" }));
  });

  it("opens the opening-asset sheet", async () => {
    render(<AssetsPage />);
    fireEvent.click(await screen.findByRole("button", { name: /Add opening asset/ }));
    expect((await screen.findAllByText("Add opening asset")).length).toBeGreaterThan(1);
  });

  it("opens the dispose sheet for an asset", async () => {
    render(<AssetsPage />);
    fireEvent.click(await screen.findByRole("button", { name: "Dispose" }));
    expect((await screen.findAllByText("Dispose Cutting table")).length).toBeGreaterThan(0);
  });

  it("saves the year-end stock list", async () => {
    render(<YearEndPage />);
    fireEvent.click(await screen.findByRole("button", { name: "Save stock list" }));
    await waitFor(() => expect(mocks.accounts.yearEnd.saveStock).toHaveBeenCalled());
  });

  it("shows a closed year with its closing run and failed checks", async () => {
    mocks.accounts.yearEnd.get.mockResolvedValue({
      ...yearEnd,
      closed: true,
      stockSaved: true,
      canReopen: true,
      stockNote: "Counted on 31 March",
      checks: [{ key: "PROBLEMS", label: "No posting problems", ok: false, message: "2 problems left" }],
      runs: [
        {
          id: 1,
          stockEntryId: 10,
          closingEntryId: 11,
          closedBy: "Asha Rao",
          closedAt: "2027-04-02T10:00:00Z",
          reopenedBy: null,
          reopenedAt: null,
        },
      ],
    });
    render(<YearEndPage />);
    expect((await screen.findAllByText(/No posting problems/)).length).toBeGreaterThan(0);
    expect((await screen.findAllByText(/2 problems left/)).length).toBeGreaterThan(0);
  });

  it("warns when backups are not set up", async () => {
    mocks.accounts.backups.status.mockResolvedValue({
      ...backups,
      configured: false,
      lastBackup: null,
      lastCheck: null,
      recent: [],
    });
    render(<BackupsPage />);
    await waitFor(() => expect(mocks.accounts.backups.status).toHaveBeenCalled());
    expect((await screen.findAllByText(/Not done/)).length).toBeGreaterThan(0);
  });

  it("flags a missed backup and shows the last restore drill", async () => {
    mocks.accounts.backups.status.mockResolvedValue({
      ...backups,
      missedLastNight: true,
      missedDaysLast7: 2,
      lastDrill: {
        ...backups.recent[0]!,
        id: 2,
        kind: "DRILL",
        drilledOn: "2026-11-01",
        notes: "Restored to a laptop",
      },
      recent: [...backups.recent, { ...backups.recent[0]!, id: 3, status: "FAILED", message: "Bucket refused" }],
    });
    render(<BackupsPage />);
    expect((await screen.findAllByText(/Bucket refused|FAILED/)).length).toBeGreaterThan(0);
  });

  it("switches between bank statements, Razorpay settlements and COD remittances", async () => {
    mocks.accounts.bank.settlements.mockResolvedValue([
      {
        id: "setl_1",
        utr: "UTR778",
        settledOn: "2026-11-03",
        grossRupees: 1000,
        feeRupees: 20,
        taxRupees: 3.6,
        netRupees: 976.4,
        journalEntryId: 5,
        journalNumber: "JV-5",
      },
    ] satisfies Of<Accounts["bank"]["settlements"]>);
    mocks.accounts.bank.remittances.mockResolvedValue([
      {
        id: 1,
        remittanceRef: "COD-REM-9",
        utr: null,
        remittedOn: "2026-11-05",
        codRupees: 2000,
        deductionRupees: 40,
        netRupees: 1960,
        awbCount: 3,
        journalNumber: null,
      },
    ] satisfies Of<Accounts["bank"]["remittances"]>);
    render(<BankPage />);
    await screen.findAllByText(/hdfc-november.csv/);
    fireEvent.click(screen.getByRole("radio", { name: "Razorpay" }));
    expect((await screen.findAllByText(/UTR UTR778/)).length).toBeGreaterThan(0);
    fireEvent.click(screen.getByRole("radio", { name: "COD remittances" }));
    expect((await screen.findAllByText(/COD-REM-9/)).length).toBeGreaterThan(0);
  });

  it("expands a statement line and switches to the balance sheet", async () => {
    render(<StatementsPage />);
    fireEvent.click(await screen.findByRole("button", { name: /Revenue from operations/ }));
    expect((await screen.findAllByText(/4000 · Sales/)).length).toBeGreaterThan(0);
    fireEvent.click(screen.getByRole("radio", { name: "Balance sheet" }));
    await waitFor(() => expect(mocks.accounts.statements.balanceSheet).toHaveBeenCalled());
  });

  it("opens the add and rename account sheets", async () => {
    render(<ChartPage />);
    fireEvent.click(await screen.findByRole("button", { name: "Rename Cash in hand" }));
    expect((await screen.findAllByText("Rename account")).length).toBeGreaterThan(0);
  });

  it("opens a new account sheet", async () => {
    render(<ChartPage />);
    await screen.findAllByText(/Cash in hand/);
    fireEvent.click(screen.getByRole("button", { name: /Add account/ }));
    expect((await screen.findAllByText("Add account")).length).toBeGreaterThan(1);
  });

  it("shows a disposed asset and an inactive class", async () => {
    mocks.accounts.assets.classes.mockResolvedValue([{ ...assetClasses[0]!, active: false }]);
    mocks.accounts.assets.list.mockResolvedValue([
      { ...assets[0]!, disposedOn: "2026-10-01", disposalProceedsRupees: 5000, billId: 9, billNo: "B-101" },
    ]);
    render(<AssetsPage />);
    expect((await screen.findAllByText(/Disposed/)).length).toBeGreaterThan(0);
  });

  it("explains GSTR-3B when GST is off", async () => {
    mocks.accounts.gstr3b.mockResolvedValue({ ...gstr3b, gstOn: false, outward: [], setOff: [] });
    render(<Gstr3bPage />);
    await waitFor(() => expect(mocks.accounts.gstr3b).toHaveBeenCalled());
    expect((await screen.findAllByText(/Eligible input tax credit/)).length).toBeGreaterThan(0);
  });

  it("shows empty states", async () => {
    mocks.creditNotes.list.mockResolvedValue({ ...creditNotes, items: [], total: 0 });
    mocks.purchases.suppliers.mockResolvedValue([]);
    mocks.accounts.problems.mockResolvedValue([]);
    mocks.accounts.journal.mockResolvedValue({ ...journal, items: [], total: 0 });
    const { unmount } = render(<CreditNotesPage />);
    await waitFor(() => expect(mocks.creditNotes.list).toHaveBeenCalled());
    unmount();
    const purchases = render(<PurchasesPage />);
    expect(await screen.findByText("No suppliers yet")).toBeInTheDocument();
    purchases.unmount();
    const problemsPage = render(<ProblemsPage />);
    await waitFor(() => expect(mocks.accounts.problems).toHaveBeenCalled());
    problemsPage.unmount();
    render(<JournalPage />);
    await waitFor(() => expect(mocks.accounts.journal).toHaveBeenCalled());
  });
});
