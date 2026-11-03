import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { blankStaff, formFromDraft } from "@/components/staff-form";
import { requestFromDraft } from "@/components/request-form";
import { canSee, hasPermission, moreSections, searchableNav } from "@/lib/nav";
import { minutes } from "@/lib/staff";
import { serveStaff } from "@/test/staff-fixtures";
import { lastSent, serveTailoring, type Sent } from "@/test/tailoring-fixtures";
import MyRequestsPage from "../requests/page";
import AttendanceMonthPage from "./attendance/month/page";
import AttendancePage from "./attendance/page";
import NewStaffPage from "./new/page";
import StaffPage from "./page";
import StaffRequestsPage from "./requests/page";
import RolesPage from "./roles/page";
import StaffMemberPage from "./view/page";

const replace = vi.fn();
let search = new URLSearchParams();
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace }), useSearchParams: () => search }));

let sent: Sent[];
beforeEach(() => {
  sent = serveTailoring().sent;
  serveStaff(sent);
  replace.mockReset();
  search = new URLSearchParams();
});

const type = (label: string | RegExp, value: string, scope: HTMLElement = document.body) =>
  fireEvent.change(within(scope).getByLabelText(label), { target: { value } });

describe("permissions in the navigation", () => {
  const staff = { role: "STAFF" as const, permissions: ["ORDERS" as const, "WORKSHOP" as const] };

  it("shows staff only their areas and the owner everything", () => {
    const hrefs = searchableNav(staff).map((i) => i.href);
    expect(hrefs).toContain("/orders/");
    expect(hrefs).toContain("/requests/");
    expect(hrefs).toContain("/settings/account/");
    expect(hrefs).not.toContain("/money/");
    expect(hrefs).not.toContain("/payroll/");
    expect(hrefs).not.toContain("/settings/billing/");
    expect(hrefs).not.toContain("/staff/roles/");
    const roles = moreSections.flatMap((s) => s.items).find((i) => i.href === "/staff/roles/")!;
    expect(canSee(roles, { role: "MANAGER", permissions: ["STAFF"] })).toBe(false);
    expect(canSee(roles, { role: "OWNER", permissions: [] })).toBe(true);
    expect(hasPermission(undefined, "PAYROLL")).toBe(true);
  });
});

describe("staff form rules", () => {
  it("explains what is missing", () => {
    expect(formFromDraft(blankStaff, false).error).toBe("Enter the name.");
    const named = { ...blankStaff, name: "Ravi" };
    expect(formFromDraft({ ...named, salary: "lots" }, false).error).toBe("Enter pay as numbers.");
    expect(formFromDraft({ ...named, signIn: true }, false).error).toBe("Add an email to sign in with.");
    expect(formFromDraft({ ...named, signIn: true, email: "r@v.in", password: "short" }, false).error).toBe(
      "Give them a password of at least 10 characters.",
    );
    // Someone who can already sign in keeps their password when none is typed.
    expect(formFromDraft({ ...named, signIn: true, email: "r@v.in" }, true).form?.password).toBeNull();
    expect(formFromDraft({ ...named, payType: "PIECE", salary: "500" }, false).form?.salaryRupees).toBe(0);
  });

  it("formats minutes", () => {
    expect(minutes(0)).toBe("—");
    expect(minutes(45)).toBe("45 min");
    expect(minutes(120)).toBe("2 h");
    expect(minutes(125)).toBe("2 h 5 min");
  });
});

describe("staff pages", () => {
  it("lists staff with their pay", async () => {
    render(<StaffPage />);
    expect(await screen.findByRole("link", { name: /Ravi Tailor/ })).toHaveAttribute("href", "/staff/view/?id=2");
    expect(screen.getByText("₹ 15,000 / month")).toBeInTheDocument();
    expect(screen.getByText("By the piece")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("switch", { name: "Show people who left" }));
    await waitFor(() => expect(lastSent(sent, "GET", "/staff")?.query).toBe("?includeInactive=true"));
  });

  it("adds someone with a dashboard sign-in", async () => {
    render(<NewStaffPage />);
    type("Name", "Ravi Tailor");
    type("Salary a month (₹)", "15000");
    type("Overtime per hour (₹)", "120");
    fireEvent.click(screen.getByRole("switch", { name: "Can sign in to the dashboard" }));
    type("Email", "ravi@vaabhi.local");
    type("Password", "a-long-password");
    fireEvent.click(screen.getByRole("button", { name: "Add staff" }));
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/staff/view/?id=2"));
    expect(lastSent(sent, "POST", "/staff")?.body).toMatchObject({
      name: "Ravi Tailor",
      role: "STAFF",
      payType: "MONTHLY",
      salaryRupees: 1500000,
      hourlyRupees: 12000,
      email: "ravi@vaabhi.local",
      password: "a-long-password",
      weeklyOff: null,
    });
  });

  it("shows a person, their requests and pay statement, and can mark them as left", async () => {
    search = new URLSearchParams("id=2");
    render(<StaffMemberPage />);
    expect(await screen.findByRole("heading", { name: "Ravi Tailor" })).toBeInTheDocument();
    expect(screen.getByText("Sunday")).toBeInTheDocument();
    expect(await screen.findByText("₹ 2,000 advance still to earn back.")).toBeInTheDocument();
    expect(await screen.findByText(/10 Nov/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Mark as left" }));
    await waitFor(() => expect(lastSent(sent, "POST", "/staff/2/deactivate")).toBeDefined());
    expect(await screen.findByRole("button", { name: "Bring back" })).toBeInTheDocument();
  });

  it("lets the owner change what a role may do", async () => {
    render(<RolesPage />);
    const money = await screen.findByRole("switch", { name: "Staff: Money" });
    expect(screen.queryByRole("switch", { name: "Owner: Money" })).not.toBeInTheDocument();
    fireEvent.click(money);
    fireEvent.click(screen.getByRole("button", { name: "Save staff" }));
    await waitFor(() =>
      expect(lastSent(sent, "PUT", "/staff/roles/STAFF")?.body).toEqual({
        permissions: expect.arrayContaining(["ORDERS", "WORKSHOP", "MONEY"]),
      }),
    );
  });
});

describe("attendance", () => {
  it("marks people and their times, and the rest present at once", async () => {
    render(<AttendancePage />);
    const ravi = await screen.findByRole("group", { name: "Mark Ravi Tailor" });
    expect(screen.getByText("Late 25 min")).toBeInTheDocument();
    expect(screen.getByText("Overtime 1 h 30 min")).toBeInTheDocument();

    type("Out (Ravi Tailor)", "21:00");
    fireEvent.click(screen.getByRole("button", { name: "Save times" }));
    await waitFor(() => {
      const marks = sent.filter((s) => s.method === "PUT" && s.path.endsWith("/2"));
      expect(marks.at(-1)?.body).toMatchObject({ status: "PRESENT", checkIn: "10:25", checkOut: "21:00" });
    });

    fireEvent.click(within(ravi).getByRole("button", { name: "Half day" }));
    await waitFor(() => {
      const marks = sent.filter((s) => s.method === "PUT" && s.path.includes("/staff/attendance/"));
      expect(marks.at(-1)?.body).toMatchObject({ status: "HALF_DAY", checkIn: "10:25", checkOut: "21:00" });
    });

    fireEvent.click(screen.getByRole("button", { name: "Mark the other 1 present" }));
    await waitFor(() => {
      const bulk = sent.filter((s) => s.method === "POST" && s.path.includes("/staff/attendance/"));
      expect(bulk.at(-1)?.body).toEqual({ status: "PRESENT", staffIds: [3] });
    });
  });

  it("shows the month as a grid with totals", async () => {
    render(<AttendanceMonthPage />);
    const row = await screen.findByRole("row", { name: /Ravi Tailor/ });
    expect(within(row).getByTitle("Present")).toHaveTextContent("P");
    expect(within(row).getByTitle("Absent")).toHaveTextContent("A");
    expect(within(row).getByText("25 min")).toBeInTheDocument();
  });
});

describe("requests", () => {
  it("validates the request form", () => {
    const base = {
      staffId: null,
      kind: "LEAVE" as const,
      fromDate: "2026-11-10",
      toDate: "",
      hours: "",
      amount: "",
      reason: "",
    };
    expect(requestFromDraft({ ...base, toDate: "2026-11-01" }).error).toBe("The last day is before the first.");
    expect(requestFromDraft({ ...base, kind: "PERMISSION", hours: "12" }).error).toBe("Permission is 0.5 to 8 hours.");
    expect(requestFromDraft({ ...base, kind: "ADVANCE" }).error).toBe("Enter the advance amount.");
    expect(requestFromDraft(base).form).toMatchObject({ toDate: "2026-11-10", hours: null, amountRupees: null });
    expect(requestFromDraft({ ...base, kind: "ADVANCE", amount: "2000" }).form).toMatchObject({
      toDate: null,
      amountRupees: 200000,
    });
  });

  it("approves a waiting request and raises one for someone", async () => {
    render(<StaffRequestsPage />);
    fireEvent.click(await screen.findByRole("button", { name: "Approve Ravi Tailor's request" }));
    await waitFor(() =>
      expect(lastSent(sent, "POST", "/staff/requests/11/decision")?.body).toEqual({ approve: true, note: null }),
    );

    fireEvent.click(screen.getByRole("button", { name: /New request/ }));
    const dialog = screen.getByRole("dialog", { name: "New request" });
    type("Kind", "ADVANCE", dialog);
    type("Amount (₹)", "1500", dialog);
    fireEvent.click(within(dialog).getByRole("button", { name: "Send request" }));
    await waitFor(() =>
      expect(lastSent(sent, "POST", "/staff/requests")?.body).toMatchObject({
        staffId: 2,
        kind: "ADVANCE",
        amountRupees: 150000,
      }),
    );
  });

  it("lists my requests and raises one", async () => {
    render(<MyRequestsPage />);
    expect(await screen.findByText(/“Enjoy”/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /New request/ }));
    const dialog = screen.getByRole("dialog", { name: "New request" });
    expect(within(dialog).queryByLabelText("For")).not.toBeInTheDocument();
    type("Kind", "PERMISSION", dialog);
    type("Hours", "2", dialog);
    fireEvent.click(within(dialog).getByRole("button", { name: "Send request" }));
    await waitFor(() =>
      expect(lastSent(sent, "POST", "/me/requests")?.body).toMatchObject({
        staffId: null,
        kind: "PERMISSION",
        hours: 2,
      }),
    );
  });
});
