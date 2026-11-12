import type {
  Account,
  AccountsStatus,
  BooksSettings,
  JournalEntry,
  JournalPage,
  OpeningBalances,
  PeriodStatus,
  TrialBalance,
} from "@vaabhi/api-client";
import { http, HttpResponse } from "msw";
import { API, server } from "./server";

export const cashAccount: Account = {
  id: 1,
  code: "1000",
  name: "Cash",
  type: "ASSET",
  parentId: null,
  systemKey: "CASH",
  active: true,
  balanceRupees: 5000,
};

export const salesAccount: Account = {
  id: 2,
  code: "4000",
  name: "Sales",
  type: "INCOME",
  parentId: null,
  systemKey: "SALES",
  active: true,
  balanceRupees: 5000,
};

export const notStarted: BooksSettings = { startDate: null, modeAccounts: {}, lastSweepAt: null };

export const startedStatus: AccountsStatus = {
  startDate: "2026-04-01",
  lastSweepAt: "2026-11-12T09:00:00Z",
  problemCount: 0,
  trialBalanceDifference: 0,
};

export const trialBalance: TrialBalance = {
  asOf: "2026-11-12",
  lines: [
    { accountId: 1, code: "1000", name: "Cash", type: "ASSET", debitRupees: 5000, creditRupees: 0 },
    { accountId: 2, code: "4000", name: "Sales", type: "INCOME", debitRupees: 0, creditRupees: 5000 },
  ],
  totalDebitRupees: 5000,
  totalCreditRupees: 5000,
  balanced: true,
};

export const invoiceEntry: JournalEntry = {
  id: 7,
  number: "JE-7",
  date: "2026-11-02",
  sourceType: "INVOICE",
  sourceId: 5,
  sourceStep: "ISSUE",
  narration: "Invoice INV-305",
  orderKey: null,
  reversesId: null,
  reversedById: null,
  createdBy: null,
  createdAt: "2026-11-02T10:00:00Z",
  sourceLink: { type: "INVOICE", id: 5, label: "INV-305" },
  lines: [
    {
      accountId: 1,
      accountCode: "1000",
      accountName: "Cash",
      partyId: null,
      partyName: null,
      debitRupees: 5000,
      creditRupees: 0,
      memo: null,
    },
    {
      accountId: 2,
      accountCode: "4000",
      accountName: "Sales",
      partyId: null,
      partyName: null,
      debitRupees: 0,
      creditRupees: 5000,
      memo: null,
    },
  ],
};

export const journalPage: JournalPage = {
  items: [
    {
      id: 7,
      number: "JE-7",
      date: "2026-11-02",
      narration: "Invoice INV-305",
      sourceType: "INVOICE",
      sourceStep: "ISSUE",
      totalRupees: 5000,
      reversed: false,
    },
  ],
  total: 1,
  page: 1,
  size: 20,
};

export const openingBalances: OpeningBalances = {
  startDate: "2026-04-01",
  entryId: null,
  entryNumber: null,
  lines: [],
};

export const openPeriods: PeriodStatus[] = [
  { month: "2026-10-01", locked: false, lockedBy: null, lockedAt: null },
  { month: "2026-09-01", locked: true, lockedBy: "Owner", lockedAt: "2026-10-02T09:00:00Z" },
];

/** Serves the Accounts endpoints; pass the settings and status to start from. */
export function serveAccounts(settings: BooksSettings = notStarted, status: AccountsStatus = startedStatus): void {
  const base = `${API}/api/v1/admin/accounts`;
  server.use(
    http.get(`${base}/status`, () => HttpResponse.json({ ...status, startDate: settings.startDate })),
    http.get(`${base}/settings`, () => HttpResponse.json(settings)),
    http.get(`${base}/accounts`, () => HttpResponse.json([cashAccount, salesAccount])),
    http.get(`${base}/trial-balance`, () => HttpResponse.json(trialBalance)),
    http.get(`${base}/journal`, () => HttpResponse.json(journalPage)),
    http.get(`${base}/journal/:id`, () => HttpResponse.json(invoiceEntry)),
    http.get(`${base}/opening-balances`, () => HttpResponse.json(openingBalances)),
    http.get(`${base}/periods`, () => HttpResponse.json(openPeriods)),
    http.get(`${base}/problems`, () => HttpResponse.json([])),
  );
}
