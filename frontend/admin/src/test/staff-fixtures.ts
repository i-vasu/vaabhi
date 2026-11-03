import type {
  DayAttendance,
  MonthAttendance,
  PayrollMonth,
  ProductionLedger,
  RolePermissions,
  StaffMember,
  StaffRequest,
  StaffStatement,
} from "@vaabhi/api-client";
import { http, HttpResponse } from "msw";
import { API, server } from "./server";
import type { Sent } from "./tailoring-fixtures";

export const ravi: StaffMember = {
  id: 2,
  name: "Ravi Tailor",
  phone: "9876512345",
  email: null,
  role: "STAFF",
  designation: "Tailor",
  joinedOn: "2026-01-15",
  payType: "MONTHLY",
  salaryRupees: 1500000,
  hourlyRupees: 12000,
  shiftStart: "10:00:00",
  shiftEnd: "19:00:00",
  lateGraceMinutes: 10,
  weeklyOff: 7,
  active: true,
  canSignIn: false,
  lastLoginAt: null,
};

export const meena: StaffMember = {
  ...ravi,
  id: 3,
  name: "Meena Manager",
  role: "MANAGER",
  email: "meena@vaabhi.local",
  designation: null,
  payType: "PIECE",
  salaryRupees: 0,
  canSignIn: true,
  lastLoginAt: "2026-11-01T09:00:00+05:30",
  weeklyOff: null,
};

export const day: DayAttendance = {
  day: "2026-11-02",
  holiday: null,
  entries: [
    {
      staffId: 2,
      name: "Ravi Tailor",
      designation: "Tailor",
      shiftStart: "10:00:00",
      shiftEnd: "19:00:00",
      status: "PRESENT",
      suggested: null,
      checkIn: "10:25:00",
      checkOut: "20:30:00",
      lateMinutes: 25,
      overtimeMinutes: 90,
      note: null,
      markedBy: "Asha Rao",
    },
    {
      staffId: 3,
      name: "Meena Manager",
      designation: null,
      shiftStart: "10:00:00",
      shiftEnd: "19:00:00",
      status: null,
      suggested: null,
      checkIn: null,
      checkOut: null,
      lateMinutes: 0,
      overtimeMinutes: 0,
      note: null,
      markedBy: null,
    },
  ],
};

export const month: MonthAttendance = {
  from: "2026-11-01",
  to: "2026-11-30",
  rows: [
    {
      staffId: 2,
      name: "Ravi Tailor",
      days: ["PRESENT", "ABSENT", ...Array<null>(28).fill(null)],
      present: 1,
      halfDays: 0,
      absent: 1,
      leave: 0,
      offDays: 0,
      unmarked: 28,
      lateMinutes: 25,
      overtimeMinutes: 90,
    },
  ],
};

export const leaveRequest: StaffRequest = {
  id: 11,
  staffId: 2,
  staffName: "Ravi Tailor",
  kind: "LEAVE",
  fromDate: "2026-11-10",
  toDate: "2026-11-12",
  hours: null,
  amountRupees: null,
  reason: "Family function",
  status: "PENDING",
  requestedBy: "Ravi Tailor",
  decidedBy: null,
  decidedAt: null,
  decisionNote: null,
  recoveredMonth: null,
  createdAt: "2026-11-02T09:00:00+05:30",
};

export const roles: RolePermissions[] = [
  {
    role: "OWNER",
    permissions: ["ORDERS", "WORKSHOP", "MONEY", "STAFF", "PAYROLL", "CATALOGUE", "SETTINGS"],
    editable: false,
  },
  { role: "MANAGER", permissions: ["ORDERS", "WORKSHOP", "MONEY", "STAFF", "CATALOGUE"], editable: true },
  { role: "STAFF", permissions: ["ORDERS", "WORKSHOP"], editable: true },
];

export const payroll: PayrollMonth = {
  month: "2026-10-01",
  finalized: true,
  slips: [
    {
      id: 21,
      staffId: 2,
      name: "Ravi Tailor",
      designation: "Tailor",
      month: "2026-10-01",
      payType: "MONTHLY",
      rateRupees: 1500000,
      daysPaid: 29.5,
      daysUnpaid: 1.5,
      baseRupees: 1427419,
      pieceRupees: 0,
      pieces: 4,
      overtimeMinutes: 120,
      overtimeRupees: 24000,
      lateMinutes: 40,
      lateRupees: 8000,
      advanceRupees: 200000,
      netRupees: 1243419,
      paidAt: null,
      paidMode: null,
    },
  ],
  totalNetRupees: 1243419,
  paidRupees: 0,
  unpaidRupees: 1243419,
};

export const production: ProductionLedger = {
  from: "2026-11-01",
  to: "2026-11-30",
  entries: [
    {
      at: "2026-11-02T12:00:00+05:30",
      staffId: 3,
      staffName: "Meena Manager",
      orderId: 42,
      orderNumber: "ORD-2042",
      itemId: 101,
      description: "3 dart blouse",
      quantity: 2,
      stageId: 1,
      stageName: "Cutting",
      rateRupees: 15000,
      amountRupees: 30000,
    },
  ],
  byStaff: [{ staffId: 3, name: "Meena Manager", pieces: 2, amountRupees: 30000 }],
};

export const statement: StaffStatement = {
  staffId: 2,
  name: "Ravi Tailor",
  balanceRupees: -200000,
  entries: [
    { date: "2026-10-10", kind: "ADVANCE", description: "Advance: Rent", amountRupees: -200000, balanceRupees: -200000 },
  ],
};

/** Serves the staff and payroll endpoints from the fixtures above, recording every request in `sent`. */
export function serveStaff(sent: Sent[]): void {
  const record = async (request: Request) => {
    const url = new URL(request.url);
    const text = request.method === "GET" ? "" : await request.text();
    sent.push({
      method: request.method,
      path: url.pathname,
      query: url.search,
      body: text ? JSON.parse(text) : undefined,
    });
  };
  const reply =
    (value: unknown, status = 200) =>
    async ({ request }: { request: Request }) => {
      await record(request);
      return HttpResponse.json(value as never, { status });
    };
  const base = `${API}/api/v1/admin`;
  server.use(
    http.get(`${base}/staff`, reply([ravi, meena])),
    http.post(`${base}/staff`, reply(ravi, 201)),
    http.get(`${base}/staff/roles`, reply(roles)),
    http.put(`${base}/staff/roles/:role`, reply({ ...roles[2], permissions: ["ORDERS", "WORKSHOP", "MONEY"] })),
    http.get(`${base}/staff/attendance`, reply(day)),
    http.put(`${base}/staff/attendance/:day/:staffId`, reply(day)),
    http.delete(`${base}/staff/attendance/:day/:staffId`, reply(day)),
    http.post(`${base}/staff/attendance/:day`, reply(day)),
    http.get(`${base}/staff/attendance/month/:month`, reply(month)),
    http.get(`${base}/staff/requests`, reply([leaveRequest])),
    http.post(`${base}/staff/requests`, reply(leaveRequest, 201)),
    http.post(`${base}/staff/requests/:id/decision`, reply({ ...leaveRequest, status: "APPROVED" })),
    http.get(`${base}/staff/:id`, reply(ravi)),
    http.put(`${base}/staff/:id`, reply(ravi)),
    http.post(`${base}/staff/:id/deactivate`, reply({ ...ravi, active: false })),
    http.post(`${base}/staff/:id/activate`, reply(ravi)),
    http.delete(`${base}/staff/:id/sign-in`, reply({ ...meena, canSignIn: false })),
    http.get(`${base}/me/requests`, reply([{ ...leaveRequest, status: "APPROVED", decisionNote: "Enjoy" }])),
    http.post(`${base}/me/requests`, reply(leaveRequest, 201)),
    http.get(`${base}/payroll/months/:month`, reply(payroll)),
    http.post(`${base}/payroll/months/:month/finalize`, reply(payroll)),
    http.post(`${base}/payroll/months/:month/reopen`, reply({ ...payroll, finalized: false })),
    http.post(
      `${base}/payroll/payslips/:id/pay`,
      reply({ ...payroll.slips[0], paidAt: "2026-11-02T12:00:00+05:30", paidMode: "BANK" }),
    ),
    http.get(`${base}/payroll/production`, reply(production)),
    http.get(`${base}/payroll/staff/:id/statement`, reply(statement)),
  );
}
