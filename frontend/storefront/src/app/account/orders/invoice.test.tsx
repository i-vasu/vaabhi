import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { InvoiceLine, StoreInvoice } from "@vaabhi/api-client";
import { InvoiceView } from "@/components/auth/invoice-view";

const baseLine: InvoiceLine = {
  id: 1,
  description: "Kanjeevaram silk saree",
  quantity: 2,
  unitRupees: 9250,
  amountRupees: 18500,
  discountRupees: 0,
  hsnCode: null,
  gstRate: 0,
  taxableRupees: 18500,
  cgstRupees: 0,
  sgstRupees: 0,
  igstRupees: 0,
};

const invoice: StoreInvoice = {
  number: "WINV-2026-0001",
  title: "Tax Invoice",
  issuedOn: "2026-11-04",
  seller: { name: "Vaabhi Fashions", address: "12 Market Road", cin: null, state: "Delhi", stateCode: "07" },
  buyer: { name: "Meera Iyer", phone: "+919876501234", address: "4 Lake View Road", state: "Delhi" },
  orderNumber: "WEB-1001",
  lines: [baseLine],
  taxableRupees: 17619,
  cgstRupees: 0,
  sgstRupees: 0,
  igstRupees: 0,
  placeOfSupply: null,
  supplyType: "INTRA",
  totalRupees: 18500,
  shopGstin: null,
  cancelled: false,
};

describe("InvoiceView", () => {
  it("renders title, lines and total", () => {
    render(<InvoiceView invoice={invoice} />);
    expect(screen.getByRole("heading", { name: "Tax Invoice" })).toBeInTheDocument();
    expect(screen.getByText("Kanjeevaram silk saree")).toBeInTheDocument();
    expect(screen.getByText("Total").nextSibling).toHaveTextContent("₹18,500");
    expect(screen.getByText("State: Delhi (07)")).toBeInTheDocument();
    expect(screen.queryByText("CGST")).not.toBeInTheDocument();
    expect(screen.queryByText("HSN/SAC")).not.toBeInTheDocument();
    expect(screen.queryByText(/Cancelled/)).not.toBeInTheDocument();
  });

  it("shows HSN, rate and IGST when taxed inter-state", () => {
    render(
      <InvoiceView
        invoice={{
          ...invoice,
          supplyType: "INTER",
          igstRupees: 881,
          lines: [{ ...baseLine, hsnCode: "5007", gstRate: 5 }],
        }}
      />,
    );
    expect(screen.getByText("HSN/SAC")).toBeInTheDocument();
    expect(screen.getByText("5007")).toBeInTheDocument();
    expect(screen.getByText("IGST")).toBeInTheDocument();
    expect(screen.queryByText("CGST")).not.toBeInTheDocument();
  });

  it("says when the invoice is cancelled", () => {
    render(<InvoiceView invoice={{ ...invoice, cancelled: true }} />);
    expect(screen.getByRole("status")).toHaveTextContent("Cancelled");
  });
});
