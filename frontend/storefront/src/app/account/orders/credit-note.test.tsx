import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { StoreCreditNote } from "@vaabhi/api-client";
import { CreditNoteView } from "@/components/auth/credit-note-view";

const note: StoreCreditNote = {
  number: "CN/26-27/0001",
  title: "Credit Note",
  reason: "RETURN",
  issuedOn: "2026-11-10",
  invoiceNumber: "WINV-2026-0001",
  orderNumber: "WEB-1001",
  seller: { name: "Vaabhi Fashions", address: "12 Market Road", cin: null, state: "Delhi", stateCode: "07" },
  buyer: { name: "Meera Iyer", phone: "+919876501234", address: "4 Lake View Road", state: "Delhi" },
  lines: [
    {
      id: 1,
      invoiceLineId: 1,
      description: "Kanjeevaram silk saree",
      hsnCode: "5007",
      quantity: 1,
      unitRupees: 9250,
      gstRate: 5,
      taxableRupees: 8810,
      cgstRupees: 220,
      sgstRupees: 220,
      igstRupees: 0,
      amountRupees: 9250,
    },
  ],
  taxableRupees: 8810,
  cgstRupees: 220,
  sgstRupees: 220,
  igstRupees: 0,
  placeOfSupply: "Delhi",
  supplyType: "INTRA",
  totalRupees: 9250,
};

describe("CreditNoteView", () => {
  it("renders title, invoice number, lines and total", () => {
    render(<CreditNoteView note={note} />);
    expect(screen.getByRole("heading", { name: "Credit Note" })).toBeInTheDocument();
    expect(screen.getByText("Against invoice WINV-2026-0001")).toBeInTheDocument();
    expect(screen.getByText("Kanjeevaram silk saree")).toBeInTheDocument();
    expect(screen.getByText("5007")).toBeInTheDocument();
    expect(screen.getByText("CGST")).toBeInTheDocument();
    expect(screen.getByText("Total").nextSibling).toHaveTextContent("₹9,250");
  });
});
