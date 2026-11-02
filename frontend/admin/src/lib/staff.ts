import type { AttendanceStatus, PayType, Permission, RequestKind, RequestStatus, StaffRole } from "@vaabhi/api-client";
import type { Tone } from "@/components/ui";

export const ROLES: { value: StaffRole; label: string }[] = [
  { value: "STAFF", label: "Staff" },
  { value: "MANAGER", label: "Manager" },
  { value: "OWNER", label: "Owner" },
];

export const roleLabel = (role: StaffRole) => ROLES.find((r) => r.value === role)?.label ?? role;

export const PAY_TYPES: { value: PayType; label: string; rate: string }[] = [
  { value: "MONTHLY", label: "Monthly salary", rate: "Salary a month (₹)" },
  { value: "DAILY", label: "Daily wage", rate: "Wage a day (₹)" },
  { value: "PIECE", label: "Piece rate", rate: "" },
];

export const WEEKDAYS = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];

export const PERMISSIONS: { value: Permission; label: string; description: string }[] = [
  { value: "ORDERS", label: "Orders", description: "Orders, customers, alterations, price list" },
  { value: "WORKSHOP", label: "Workshop", description: "Work board and overview" },
  { value: "MONEY", label: "Money", description: "Invoices, dues, expenses, ledger" },
  { value: "STAFF", label: "Staff", description: "Staff, attendance, requests" },
  { value: "PAYROLL", label: "Payroll", description: "Payslips and the production ledger" },
  { value: "CATALOGUE", label: "Catalogue", description: "Online shop products" },
  { value: "SETTINGS", label: "Settings", description: "Change shop settings" },
];

export const ATTENDANCE: { value: AttendanceStatus; label: string; short: string; tone: Tone }[] = [
  { value: "PRESENT", label: "Present", short: "P", tone: "ok" },
  { value: "HALF_DAY", label: "Half day", short: "½", tone: "warn" },
  { value: "ABSENT", label: "Absent", short: "A", tone: "bad" },
  { value: "LEAVE", label: "Leave", short: "L", tone: "warn" },
  { value: "OFF", label: "Off", short: "O", tone: "default" },
  { value: "HOLIDAY", label: "Holiday", short: "H", tone: "default" },
  { value: "COMP_OFF", label: "Comp-off", short: "C", tone: "accent" },
];

export const attendanceLabel = (status: AttendanceStatus) =>
  ATTENDANCE.find((a) => a.value === status)?.label ?? status;

/** Statuses on which check-in and check-out times mean something. */
export const worked = (status: AttendanceStatus | null | undefined) => status === "PRESENT" || status === "HALF_DAY";

export const REQUEST_KINDS: { value: RequestKind; label: string }[] = [
  { value: "LEAVE", label: "Leave" },
  { value: "PERMISSION", label: "Permission (hours)" },
  { value: "OFF_DAY", label: "Off day" },
  { value: "COMP_OFF", label: "Comp-off" },
  { value: "ADVANCE", label: "Salary advance" },
];

export const requestLabel = (kind: RequestKind) => REQUEST_KINDS.find((k) => k.value === kind)?.label ?? kind;

export const REQUEST_TONES: Record<RequestStatus, Tone> = { PENDING: "warn", APPROVED: "ok", REJECTED: "bad" };

/** Whether the kind covers a range of days (the others are for one day). */
export const spansDays = (kind: RequestKind) => kind === "LEAVE" || kind === "OFF_DAY" || kind === "COMP_OFF";

/** "125" minutes → "2 h 5 min"; 0 → "—". */
export function minutes(total: number): string {
  if (total <= 0) return "—";
  const h = Math.floor(total / 60);
  const m = total % 60;
  return h === 0 ? `${m} min` : m === 0 ? `${h} h` : `${h} h ${m} min`;
}

/** "10:00:00" (API) → "10:00" (time input). */
export const hhmm = (time: string | null | undefined) => (time ? time.slice(0, 5) : "");

/** The current month as yyyy-mm. */
export function thisMonth(now: Date = new Date()): string {
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}
