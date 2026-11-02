import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { formatCell, toCsv } from "@/lib/reports";
import { salesReport } from "@/test/extras-fixtures";
import { lastSent, serveTailoring, type Sent } from "@/test/tailoring-fixtures";
import ReportsPage from "./page";
import ReportViewPage from "./view/page";

let search = new URLSearchParams();
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace: vi.fn() }), useSearchParams: () => search }));

let sent: Sent[];
beforeEach(() => {
  sent = serveTailoring().sent;
  search = new URLSearchParams();
});

describe("report helpers", () => {
  it("formats cells and exports CSV with rupees and totals", () => {
    expect(formatCell({ key: "t", label: "T", type: "MONEY" }, 99000)).toBe("₹ 990");
    expect(formatCell({ key: "s", label: "S", type: "TEXT" }, "IN_PROGRESS")).toBe("IN PROGRESS");
    expect(formatCell({ key: "s", label: "S", type: "TEXT" }, null)).toBe("—");
    expect(toCsv(salesReport)).toBe(
      "Day,Invoices,Total (₹)\n2026-11-01,2,1500.00\n2026-11-02,1,990.00\nTotal,3,2490.00\n",
    );
    expect(toCsv({ ...salesReport, rows: [{ day: "a,b", invoices: 1, total: 0 }], totals: {} })).toBe(
      'Day,Invoices,Total (₹)\n"a,b",1,0.00\n',
    );
  });
});

describe("reports hub", () => {
  it("shows this month, the six-month chart, ageing and the reports", async () => {
    render(<ReportsPage />);
    expect(await screen.findByText("₹ 2,500")).toBeInTheDocument();
    expect(screen.getByText("4 orders")).toBeInTheDocument();
    const chart = screen.getByText("Six months: booked, collected and spent").closest("figure")!;
    expect(within(chart).getByRole("table", { name: "Booked, collected and spent per month" })).toBeInTheDocument();
    expect(within(chart).getAllByText("Booked").length).toBeGreaterThan(0);
    expect(screen.getByText("1–7 days late")).toBeInTheDocument();
    expect(await screen.findByRole("link", { name: /GST register/ })).toHaveAttribute(
      "href",
      "/reports/view/?name=gst",
    );
  });
});

describe("report page", () => {
  it("shows the table with totals, changes the range and exports CSV", async () => {
    search = new URLSearchParams("name=sales");
    const create = vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:csv");
    const revoke = vi.spyOn(URL, "revokeObjectURL").mockImplementation(() => undefined);
    render(<ReportViewPage />);
    expect(await screen.findByText("Cancelled invoices are left out.")).toBeInTheDocument();
    const table = screen.getByRole("table");
    expect(within(table).getByText("₹ 1,500")).toBeInTheDocument();
    expect(within(table).getByText("₹ 2,490")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("radio", { name: "Last month" }));
    await waitFor(() => expect(lastSent(sent, "GET", "/reports/sales")?.query).toMatch(/from=\d{4}-\d{2}-01/));

    fireEvent.click(screen.getByRole("button", { name: /CSV/ }));
    expect(create).toHaveBeenCalled();
    create.mockRestore();
    revoke.mockRestore();
  });
});
