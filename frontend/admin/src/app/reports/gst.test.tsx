import { render, screen } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Gstr1 } from "@vaabhi/api-client";
import { API, server } from "@/test/server";
import { serveTailoring } from "@/test/tailoring-fixtures";
import GstReportPage from "./gst/page";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn(), push: vi.fn(), prefetch: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => "/reports/gst/",
}));

const gstr1: Gstr1 = {
  month: "2026-11",
  gstOn: false,
  b2b: [],
  b2cl: [],
  b2cs: [
    {
      columns: { Type: "OE", "Place Of Supply": "36-Telangana", Rate: 5 },
      taxable: 1000,
      igst: 0,
      cgst: 25,
      sgst: 25,
      value: 1050,
    },
  ],
  cdnr: [],
  cdnur: [],
  hsn: [],
  docs: [],
};

beforeEach(() => {
  serveTailoring();
  server.use(http.get(`${API}/api/v1/admin/money/gstr1`, () => HttpResponse.json(gstr1)));
});

describe("GSTR-1 page", () => {
  it("warns when GST is off and shows a B2C small row", async () => {
    render(<GstReportPage />);
    expect(await screen.findByText("GST is off: these figures are for practice")).toBeInTheDocument();
    expect(screen.getByText("36-Telangana")).toBeInTheDocument();
    expect(screen.getAllByText("₹ 1,000").length).toBeGreaterThan(0);
    expect(screen.getByText("No documents this month")).toBeInTheDocument();
  });
});
