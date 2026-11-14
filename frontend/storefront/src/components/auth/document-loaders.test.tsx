import { render, screen } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "@/test/server";
import { CreditNoteLoader } from "./credit-note-loader";
import { InvoiceLoader } from "./invoice-loader";

const INVOICE = "*/api/auth/orders/:number/invoice";
const CREDIT_NOTES = "*/api/auth/orders/:number/credit-notes";

describe("InvoiceLoader", () => {
  it("asks a signed-out customer to log in", async () => {
    server.use(http.get(INVOICE, () => new HttpResponse(null, { status: 401 })));
    render(<InvoiceLoader number="WEB-1001" />);
    expect(screen.getByText("Loading…")).toBeInTheDocument();
    expect(await screen.findByRole("link", { name: "Login to see your invoice" })).toHaveAttribute(
      "href",
      `/login?next=${encodeURIComponent("/account/orders/WEB-1001/invoice")}`,
    );
  });

  it("says when there is no invoice", async () => {
    server.use(http.get(INVOICE, () => new HttpResponse(null, { status: 404 })));
    render(<InvoiceLoader number="WEB-1001" />);
    expect(await screen.findByText("We couldn't find this invoice.")).toBeInTheDocument();
  });

  it("explains a failed load", async () => {
    server.use(http.get(INVOICE, () => new HttpResponse(null, { status: 500 })));
    render(<InvoiceLoader number="WEB-1001" />);
    expect(await screen.findByText("Your invoice could not be loaded. Please try again.")).toBeInTheDocument();
  });

  it("explains a network failure", async () => {
    server.use(http.get(INVOICE, () => HttpResponse.error()));
    render(<InvoiceLoader number="WEB-1001" />);
    expect(await screen.findByText("Your invoice could not be loaded. Please try again.")).toBeInTheDocument();
  });
});

describe("CreditNoteLoader", () => {
  it("asks a signed-out customer to log in", async () => {
    server.use(http.get(CREDIT_NOTES, () => new HttpResponse(null, { status: 401 })));
    render(<CreditNoteLoader number="WEB-1001" noteNumber="CN-9" />);
    expect(await screen.findByRole("link", { name: "Login to see your credit note" })).toHaveAttribute(
      "href",
      `/login?next=${encodeURIComponent("/account/orders/WEB-1001/credit-notes/CN-9")}`,
    );
  });

  it("says a note is not available when the order has none or another one", async () => {
    server.use(http.get(CREDIT_NOTES, () => HttpResponse.json([])));
    render(<CreditNoteLoader number="WEB-1001" noteNumber="CN-9" />);
    expect(await screen.findByText("This credit note isn't available.")).toBeInTheDocument();
  });

  it("says a note is not available when the order is unknown", async () => {
    server.use(http.get(CREDIT_NOTES, () => new HttpResponse(null, { status: 404 })));
    render(<CreditNoteLoader number="WEB-1001" noteNumber="CN-9" />);
    expect(await screen.findByText("This credit note isn't available.")).toBeInTheDocument();
  });

  it("explains a failed load", async () => {
    server.use(http.get(CREDIT_NOTES, () => new HttpResponse(null, { status: 502 })));
    render(<CreditNoteLoader number="WEB-1001" noteNumber="CN-9" />);
    expect(await screen.findByText("Your credit note could not be loaded. Please try again.")).toBeInTheDocument();
  });

  it("explains a network failure", async () => {
    server.use(http.get(CREDIT_NOTES, () => HttpResponse.error()));
    render(<CreditNoteLoader number="WEB-1001" noteNumber="CN-9" />);
    expect(await screen.findByText("Your credit note could not be loaded. Please try again.")).toBeInTheDocument();
  });
});
