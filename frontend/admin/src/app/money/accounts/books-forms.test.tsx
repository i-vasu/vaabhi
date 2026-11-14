import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { api as realApi } from "@/lib/api";
import BillPage from "../purchases/bill/page";
import ShiprocketPage from "../purchases/shiprocket/page";
import BankReportPage from "./bank/report/page";
import BankStatementPage from "./bank/statement/page";
import BankUploadPage from "./bank/upload/page";
import TdsPage from "./tds/page";

type Api = typeof realApi;
type Accounts = Api["admin"]["accounts"];
type Purchases = Api["admin"]["purchases"];
/** What a client call resolves to, so every fixture below is checked against the API types. */
type Of<F> = F extends (...args: never[]) => Promise<infer T> ? T : never;

let search = new URLSearchParams();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn(), push: vi.fn() }),
  useSearchParams: () => search,
}));

const mocks = vi.hoisted(() => ({
  accounts: {
    chart: vi.fn(),
    sweep: vi.fn(),
    assets: { classes: vi.fn() },
    tds: {
      sections: vi.fn(),
      summary: vi.fn(),
      returns: vi.fn(),
      returnsCsv: vi.fn(),
      createChallan: vi.fn(),
      updateSection: vi.fn(),
    },
    bank: {
      statement: vi.fn(),
      report: vi.fn(),
      reportCsv: vi.fn(),
      candidates: vi.fn(),
      match: vi.fn(),
      ignore: vi.fn(),
      post: vi.fn(),
      undo: vi.fn(),
      previewStatement: vi.fn(),
      importStatement: vi.fn(),
      previewRemittance: vi.fn(),
      importRemittance: vi.fn(),
    },
  },
  purchases: {
    bill: vi.fn(),
    suppliers: vi.fn(),
    createBill: vi.fn(),
    updateBill: vi.fn(),
    cancelBill: vi.fn(),
    startAttachment: vi.fn(),
    confirmAttachment: vi.fn(),
    paymentFromBankLine: vi.fn(),
    previewShiprocket: vi.fn(),
    importShiprocket: vi.fn(),
  },
  stock: { list: vi.fn() },
}));
vi.mock("@/lib/api", () => ({
  api: { admin: { accounts: mocks.accounts, purchases: mocks.purchases, stock: mocks.stock } },
}));

const chart: Of<Accounts["chart"]> = [
  {
    id: 20,
    code: "5100",
    name: "Fabric purchases",
    type: "EXPENSE",
    scheduleLine: "PURCHASES_STOCK_IN_TRADE",
    parentId: null,
    systemKey: null,
    active: true,
    balanceRupees: 10000,
  },
];

const sections: Of<Accounts["tds"]["sections"]> = [
  {
    id: 1,
    code: "194C",
    name: "Contractors",
    ratePercent: 1,
    noPanRatePercent: 20,
    yearThresholdRupees: 100000,
    singleThresholdRupees: 30000,
    active: true,
  },
];

const supplier: Of<Purchases["suppliers"]>[number] = {
  partyId: 4,
  name: "Lakshmi Silks",
  phone: null,
  gstin: "36ABCDE1234F1Z5",
  state: "Telangana",
  billedRupees: 10500,
  paidRupees: 0,
  tdsRupees: 0,
  balanceRupees: 10500,
  oldestUnpaidDue: "2026-10-31",
  overdue: false,
};

const bill: Of<Purchases["bill"]> = {
  id: 9,
  partyId: 4,
  supplierName: "Lakshmi Silks",
  billNo: "B-101",
  billDate: "2026-10-01",
  dueDate: "2026-10-31",
  supplyStateCode: "36",
  taxableRupees: 10000,
  cgstRupees: 250,
  sgstRupees: 250,
  igstRupees: 0,
  totalRupees: 10500,
  tdsSectionId: null,
  tdsRupees: 0,
  payableRupees: 10500,
  paidRupees: 0,
  source: "MANUAL",
  status: "OPEN",
  posted: true,
  attachmentKey: null,
  notes: "Festive stock",
  lines: [
    {
      id: 1,
      position: 0,
      kind: "EXPENSE",
      description: "Silk fabric, 20 m",
      accountId: 20,
      accountName: "Fabric purchases",
      stockItemId: null,
      stockItemName: null,
      quantity: null,
      assetClassId: null,
      assetClassName: null,
      fixedAssetId: null,
      hsn: "5007",
      taxableRupees: 10000,
      gstRatePercent: 5,
      cgstRupees: 250,
      sgstRupees: 250,
      igstRupees: 0,
      totalRupees: 10500,
    },
  ],
  tdsWarning: null,
};

const tdsSummary: Of<Accounts["tds"]["summary"]> = [
  {
    sectionCode: "194C",
    sectionName: "Contractors",
    month: "2026-10",
    deductedRupees: 300,
    paidRupees: 0,
    balanceRupees: 300,
  },
];

const tdsQuarter: Of<Accounts["tds"]["returns"]> = {
  fy: "2026-27",
  quarter: 3,
  from: "2026-10-01",
  to: "2026-12-31",
  deductees: [
    {
      billId: 9,
      sectionCode: "194C",
      partyName: "Ravi Tailoring Works",
      pan: "ABCDE1234F",
      billNo: "RT-55",
      creditedOn: "2026-10-05",
      amountRupees: 30000,
      ratePercent: 1,
      tdsRupees: 300,
      challanNo: null,
    },
  ],
  challans: [],
  totals: [{ sectionCode: "194C", deductedRupees: 300, depositedRupees: 0, differenceRupees: 300 }],
};

const statementSummary = {
  id: 1,
  fileName: "hdfc-november.csv",
  periodFrom: "2026-11-01",
  periodTo: "2026-11-30",
  openingRupees: 1000,
  closingRupees: 2000,
  lineCount: 2,
  unmatchedCount: 1,
  uploadedAt: "2026-12-01T10:00:00Z",
};
const unmatched = {
  id: 11,
  date: "2026-11-04",
  description: "NEFT LAKSHMI SILKS",
  reference: "N123",
  debitRupees: 10500,
  creditRupees: 0,
  balanceRupees: 500,
  status: "UNMATCHED",
  journalEntryId: null,
  journalNumber: null,
  ignoreReason: null,
};
const matched = {
  ...unmatched,
  id: 12,
  date: "2026-11-06",
  description: "RAZORPAY SETTLEMENT",
  reference: null,
  debitRupees: 0,
  creditRupees: 11000,
  balanceRupees: 11500,
  status: "MATCHED",
  journalEntryId: 3,
  journalNumber: "JV-3",
};
const statement: Of<Accounts["bank"]["statement"]> = { statement: statementSummary, lines: [unmatched, matched] };

const report: Of<Accounts["bank"]["report"]> = {
  statement: statementSummary,
  statementClosingRupees: 2000,
  ledgerBalanceRupees: 1500,
  differenceRupees: 500,
  unmatchedLines: [unmatched],
  ledgerOnly: [
    {
      journalEntryId: 4,
      number: "JV-4",
      date: "2026-11-20",
      narration: "Cash deposit",
      debitRupees: 500,
      creditRupees: 0,
    },
  ],
  razorpayClearingRupees: 0,
  codClearingRupees: 0,
  oldestUnsettledWebPayment: null,
  openingMismatch: false,
  ignoredLines: [{ ...unmatched, id: 13, description: "BANK CHARGES", status: "IGNORED", ignoreReason: "Fee" }],
};

beforeEach(() => {
  search = new URLSearchParams();
  vi.clearAllMocks();
  mocks.accounts.chart.mockResolvedValue(chart);
  mocks.accounts.assets.classes.mockResolvedValue([]);
  mocks.accounts.tds.sections.mockResolvedValue(sections);
  mocks.accounts.tds.summary.mockResolvedValue(tdsSummary);
  mocks.accounts.tds.returns.mockResolvedValue(tdsQuarter);
  mocks.accounts.bank.statement.mockResolvedValue(statement);
  mocks.accounts.bank.report.mockResolvedValue(report);
  mocks.purchases.bill.mockResolvedValue(bill);
  mocks.purchases.suppliers.mockResolvedValue([supplier]);
  mocks.stock.list.mockResolvedValue([]);
  mocks.purchases.updateBill.mockResolvedValue(bill);
  mocks.accounts.bank.candidates.mockResolvedValue([
    { journalEntryId: 7, number: "PAY-7", date: "2026-11-04", narration: "Paid Lakshmi Silks", amountRupees: 10500 },
  ] satisfies Of<Accounts["bank"]["candidates"]>);
});

describe("books forms", () => {
  it("opens a saved supplier bill with its lines", async () => {
    search = new URLSearchParams({ id: "9" });
    render(<BillPage />);
    expect((await screen.findAllByText(/Bill B-101/)).length).toBeGreaterThan(0);
    expect(mocks.purchases.bill).toHaveBeenCalledWith(9);
  });

  it("starts a new supplier bill", async () => {
    render(<BillPage />);
    expect((await screen.findAllByText(/New bill/)).length).toBeGreaterThan(0);
    expect(mocks.purchases.bill).not.toHaveBeenCalled();
  });

  it("shows TDS deducted and paid by section", async () => {
    render(<TdsPage />);
    expect((await screen.findAllByText(/Contractors/)).length).toBeGreaterThan(0);
    expect(mocks.accounts.tds.summary).toHaveBeenCalled();
  });

  it("shows the quarter's TDS return", async () => {
    search = new URLSearchParams({ tab: "returns" });
    render(<TdsPage />);
    expect((await screen.findAllByText("Ravi Tailoring Works")).length).toBeGreaterThan(0);
    expect(mocks.accounts.tds.returns).toHaveBeenCalled();
  });

  it("lists a bank statement's lines", async () => {
    search = new URLSearchParams({ id: "1" });
    render(<BankStatementPage />);
    expect((await screen.findAllByText(/hdfc-november\.csv/)).length).toBeGreaterThan(0);
    expect((await screen.findAllByText(/NEFT LAKSHMI SILKS/)).length).toBeGreaterThan(0);
  });

  it("shows the reconciliation report", async () => {
    search = new URLSearchParams({ id: "1" });
    render(<BankReportPage />);
    expect((await screen.findAllByText(/NEFT LAKSHMI SILKS/)).length).toBeGreaterThan(0);
    expect((await screen.findAllByText(/BANK CHARGES/)).length).toBeGreaterThan(0);
  });

  it("asks for a bank statement file", async () => {
    render(<BankUploadPage />);
    expect(await screen.findByLabelText("Statement file")).toBeInTheDocument();
  });

  it("asks for a Shiprocket invoice file", async () => {
    render(<ShiprocketPage />);
    expect(await screen.findByLabelText("Shiprocket invoice file")).toBeInTheDocument();
  });

  it("rejects a new bill without a supplier", async () => {
    render(<BillPage />);
    fireEvent.click(await screen.findByRole("button", { name: "Save bill" }));
    expect(await screen.findByText("Pick a supplier.")).toBeInTheDocument();
    expect(mocks.purchases.createBill).not.toHaveBeenCalled();
  });

  it("saves changes to a saved bill", async () => {
    search = new URLSearchParams({ id: "9" });
    render(<BillPage />);
    fireEvent.click(await screen.findByRole("button", { name: "Save bill" }));
    await waitFor(() => expect(mocks.purchases.updateBill).toHaveBeenCalled());
    expect(mocks.purchases.updateBill.mock.calls[0]?.[0]).toBe(9);
  });

  it("opens the challan sheet", async () => {
    render(<TdsPage />);
    fireEvent.click(await screen.findByRole("button", { name: "Record challan" }));
    expect((await screen.findAllByRole("button", { name: "Record challan" })).length).toBeGreaterThan(1);
  });

  it("opens a TDS section for editing", async () => {
    render(<TdsPage />);
    const edit = await screen.findAllByRole("button", { name: "Edit" });
    fireEvent.click(edit[0] as HTMLElement);
    expect(await screen.findByRole("button", { name: "Save section" })).toBeInTheDocument();
  });

  it("opens an unmatched bank line and suggests entries", async () => {
    search = new URLSearchParams({ id: "1" });
    render(<BankStatementPage />);
    fireEvent.click(await screen.findByRole("button", { name: /NEFT LAKSHMI SILKS/ }));
    await waitFor(() => expect(mocks.accounts.bank.candidates).toHaveBeenCalledWith(11));
    expect((await screen.findAllByText("PAY-7")).length).toBeGreaterThan(0);
  });
});
