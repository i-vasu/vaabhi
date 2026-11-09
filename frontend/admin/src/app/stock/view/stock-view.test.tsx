import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { StockMovement, StockUsedIn, WorkPiece } from "@vaabhi/api-client";
import { options as catalogOptions } from "@/test/admin-fixtures";
import { lining, movement } from "@/test/extras-fixtures";
import { API, server } from "@/test/server";
import { stages } from "@/test/settings-fixtures";
import { serveTailoring } from "@/test/tailoring-fixtures";
import StockItemPage from "./page";

let search = new URLSearchParams();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn(), push: vi.fn() }),
  useSearchParams: () => search,
}));

const base = `${API}/api/v1/admin`;

const piece: WorkPiece = {
  pieceId: 7,
  productId: 70,
  productName: "Silk lining blouse",
  productSlug: "silk-lining-blouse",
  productStatus: "DRAFT",
  stockItemId: 31,
  stockItemName: "Silk lining",
  quantity: 2,
  unit: "M",
  photoKey: null,
  stage: stages[1]!,
  stageSince: "2026-11-03T10:00:00Z",
  assignedTo: null,
  assignedPhone: null,
  note: null,
  photos: [],
  fabric: { material: "Silk", colour: null, widthIn: 44, gsm: 60, threadCount: null },
  dimensions: [],
};

const draft: StockUsedIn = {
  productId: 70,
  productName: "Silk lining blouse",
  productSlug: "silk-lining-blouse",
  productStatus: "DRAFT",
  quantity: 2,
  createdAt: "2026-11-03T10:00:00+05:30",
};

const published: StockUsedIn = {
  productId: 71,
  productName: "Silk lining kurta",
  productSlug: "silk-lining-kurta",
  productStatus: "PUBLISHED",
  quantity: 1.5,
  createdAt: "2026-11-01T10:00:00+05:30",
};

beforeEach(() => {
  serveTailoring();
  search = new URLSearchParams("id=31");
  server.use(
    http.get(`${base}/stock/:id/products`, () => HttpResponse.json([])),
    http.get(`${base}/workshop/pieces/by-product/:id`, () => HttpResponse.json(piece)),
    http.get(`${base}/catalog/options`, () => HttpResponse.json(catalogOptions)),
  );
});

describe("stock item page", () => {
  it("asks for a stock item when the link has none", () => {
    search = new URLSearchParams();
    render(<StockItemPage />);
    expect(screen.getByRole("alert")).toHaveTextContent("No stock item selected.");
  });

  it("shows the reason when the item cannot be found", async () => {
    server.use(
      http.get(`${base}/stock/:id`, () => HttpResponse.json({ detail: "No stock item with that id" }, { status: 404 })),
    );
    render(<StockItemPage />);
    expect(await screen.findByText("No stock item with that id")).toBeInTheDocument();
  });

  it("shows the stock figures, supplier and fabric details", async () => {
    render(<StockItemPage />);
    expect(await screen.findByText("3.5 metres")).toBeInTheDocument();
    expect(screen.getByText("Low")).toBeInTheDocument();
    expect(screen.getByText("On hand").nextElementSibling).toHaveTextContent("3.5 metres");
    expect(screen.getByText("Reorder at").nextElementSibling).toHaveTextContent("5");
    expect(screen.getByText("Cost per unit").nextElementSibling).toHaveTextContent("120");
    expect(screen.getByText("Value").nextElementSibling).toHaveTextContent("420");

    expect(screen.getByText("Sri Lakshmi Silks")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "98450 12345" })).toHaveAttribute("href", "tel:9845012345");

    expect(screen.getByText("Material").nextElementSibling).toHaveTextContent("Silk");
    expect(screen.getByText("GSM").nextElementSibling).toHaveTextContent("60");
    expect(screen.getByText("Width").nextElementSibling).toHaveTextContent("44 in");
    expect(screen.queryByText("Colour")).not.toBeInTheDocument();
  });

  it("lists what was made from the item, with its status and workshop stage", async () => {
    server.use(http.get(`${base}/stock/:id/products`, () => HttpResponse.json([draft, published])));
    render(<StockItemPage />);
    expect(await screen.findByRole("link", { name: "Silk lining blouse" })).toHaveAttribute(
      "href",
      "/products/edit/?id=70",
    );
    expect(screen.getByText("In workshop: Stitching")).toBeInTheDocument();
    expect(screen.getByText("Draft")).toBeInTheDocument();
    expect(screen.getByText("Published")).toBeInTheDocument();
    expect(screen.getByText("2 metres")).toBeInTheDocument();
    expect(screen.getByText("1.5 metres")).toBeInTheDocument();
  });

  it("says so when nothing has been made from the item", async () => {
    render(<StockItemPage />);
    expect(
      await screen.findByText("No products made from this yet. Use Make products to cut it into pieces."),
    ).toBeInTheDocument();
  });

  it("shows the reason when what was made cannot be loaded", async () => {
    server.use(
      http.get(`${base}/stock/:id/products`, () =>
        HttpResponse.json({ detail: "Could not load what was made" }, { status: 500 }),
      ),
    );
    render(<StockItemPage />);
    expect(await screen.findByRole("alert")).toHaveTextContent("Could not load what was made");
  });

  it("lists the ledger with signed quantities and the balance after each movement", async () => {
    const ledger: StockMovement[] = [
      movement,
      {
        ...movement,
        id: 2,
        kind: "IN",
        quantity: 20,
        balance: 23.5,
        orderId: null,
        orderNumber: null,
        note: "Restock",
        movedBy: "Asha Rao",
      },
      {
        ...movement,
        id: 3,
        kind: "ADJUST",
        quantity: -1,
        balance: 22.5,
        orderId: null,
        orderNumber: null,
        note: "Counted short",
        movedBy: null,
      },
    ];
    server.use(http.get(`${base}/stock/:id/movements`, () => HttpResponse.json(ledger)));
    render(<StockItemPage />);
    expect(await screen.findByText("balance 3.5")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "ORD-2042" })).toHaveAttribute("href", "/orders/view/?id=42");
    expect(screen.getByText("-16.5")).toBeInTheDocument();
    expect(screen.getByText("+20")).toBeInTheDocument();
    expect(screen.getByText(/Restock/)).toBeInTheDocument();
    expect(screen.getByText("Correction · Counted short")).toBeInTheDocument();
    expect(screen.getByText("balance 22.5")).toBeInTheDocument();
  });

  it("says when the ledger has no movements yet", async () => {
    server.use(http.get(`${base}/stock/:id/movements`, () => HttpResponse.json([])));
    render(<StockItemPage />);
    expect(await screen.findByText("No movements yet.")).toBeInTheDocument();
  });

  it("shows the reason when the ledger cannot be loaded", async () => {
    server.use(
      http.get(`${base}/stock/:id/movements`, () =>
        HttpResponse.json({ detail: "Could not load the ledger" }, { status: 500 }),
      ),
    );
    render(<StockItemPage />);
    expect(await screen.findByText("Could not load the ledger")).toBeInTheDocument();
  });

  it("does not let a fabric with nothing left be cut", async () => {
    server.use(http.get(`${base}/stock/:id`, () => HttpResponse.json({ ...lining, onHand: 0, low: true })));
    render(<StockItemPage />);
    expect(await screen.findByRole("button", { name: "Make products" })).toBeDisabled();
    expect(screen.getByTitle("Nothing left in stock")).toBeDisabled();
  });

  it("cuts the fabric into a draft product and reports it", async () => {
    const made = vi.fn();
    server.use(
      http.post(`${base}/workshop/pieces`, async ({ request }) => {
        made(await request.json());
        return HttpResponse.json(
          [{ pieceId: 8, productId: 72, productName: "Silk lining blouse", productSlug: "silk-lining-blouse" }],
          { status: 201 },
        );
      }),
    );
    render(<StockItemPage />);
    fireEvent.click(await screen.findByRole("button", { name: "Make products" }));
    const dialog = screen.getByRole("dialog", { name: "Make products from this fabric" });
    fireEvent.change(within(dialog).getByLabelText("Product name"), { target: { value: "Silk lining blouse" } });
    fireEvent.change(within(dialog).getByLabelText("Quantity (metres)"), { target: { value: "2" } });
    expect(within(dialog).getByRole("status")).toHaveTextContent("Left after: 1.5 metres");

    const submit = within(dialog).getByRole("button", { name: "Make products" });
    await waitFor(() => expect(submit).toBeEnabled());
    fireEvent.click(submit);

    expect(await within(dialog).findByText("1 draft product is on the workshop board.")).toBeInTheDocument();
    expect(made).toHaveBeenCalledWith({
      stockItemId: 31,
      pieces: [{ name: "Silk lining blouse", categorySlug: "sarees", quantity: 2, note: null }],
    });
    expect(within(dialog).getByRole("link", { name: "Silk lining blouse" })).toHaveAttribute(
      "href",
      "/products/edit/?id=72",
    );
    expect(within(dialog).getByRole("link", { name: "Open workshop board" })).toHaveAttribute(
      "href",
      "/workshop/board/",
    );
  });

  it("will not cut more than is in stock", async () => {
    render(<StockItemPage />);
    fireEvent.click(await screen.findByRole("button", { name: "Make products" }));
    const dialog = screen.getByRole("dialog", { name: "Make products from this fabric" });
    fireEvent.change(within(dialog).getByLabelText("Product name"), { target: { value: "Silk lining blouse" } });
    fireEvent.change(within(dialog).getByLabelText("Quantity (metres)"), { target: { value: "2" } });
    const submit = within(dialog).getByRole("button", { name: "Make products" });
    await waitFor(() => expect(submit).toBeEnabled());

    fireEvent.change(within(dialog).getByLabelText("Quantity (metres)"), { target: { value: "5" } });
    expect(within(dialog).getByRole("status")).toHaveTextContent("Left after: -1.5 metres");
    expect(submit).toBeDisabled();
  });
});
