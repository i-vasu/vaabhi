import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { MadePiece, MakePieces, StockItem } from "@vaabhi/api-client";
import { options } from "@/test/admin-fixtures";
import { API, server } from "@/test/server";
import { MakeProducts, leftAfter } from "./make-products";

describe("leftAfter", () => {
  it("takes the pieces out of what is on hand", () => {
    expect(leftAfter(10, [{ quantity: 3 }, { quantity: 3 }, { quantity: 3 }])).toBe(1);
  });

  it("goes below zero when the pieces need more than there is", () => {
    expect(leftAfter(10, [{ quantity: 3 }, { quantity: 3 }, { quantity: 3 }, { quantity: 3 }])).toBe(-2);
  });

  it("ignores rows without a quantity yet", () => {
    expect(leftAfter(2.5, [{ quantity: null }, { quantity: 0.75 }])).toBe(1.75);
  });
});

const CATALOG = `${API}/api/v1/admin/catalog/options`;
const PIECES = `${API}/api/v1/admin/workshop/pieces`;

const stockItem = (overrides: Partial<StockItem> = {}): StockItem => ({
  id: 5,
  name: "Silk lot",
  sku: null,
  category: null,
  unit: "M",
  reorderLevel: 0,
  costRupees: 900,
  onHand: 10,
  low: false,
  valueRupees: 9000,
  supplierName: "Surat Mills",
  supplierPhone: null,
  material: "silk",
  colour: null,
  gsm: 120,
  threadCount: 60,
  widthIn: null,
  notes: null,
  photos: [],
  ...overrides,
});

const made = (overrides: Partial<MadePiece> = {}): MadePiece => ({
  pieceId: 31,
  productId: 42,
  productName: "Kurta",
  productSlug: "kurta",
  ...overrides,
});

// Opens the sheet for a stock item and waits until the catalogue (its categories) has loaded.
async function openSheet(item = stockItem(), onMade = vi.fn()) {
  render(<MakeProducts item={item} onMade={onMade} />);
  fireEvent.click(screen.getByRole("button", { name: "Make products" }));
  const dialog = screen.getByRole("dialog", { name: "Make products from this fabric" });
  await within(dialog).findByRole("option", { name: "Sarees" });
  return { dialog, onMade };
}

// Each piece is a fieldset whose legend is "Piece 1", "Piece 2" …
const piece = (n: number) => within(screen.getByRole("group", { name: `Piece ${n}` }));

beforeEach(() => {
  server.use(http.get(CATALOG, () => HttpResponse.json(options)));
});

describe("MakeProducts", () => {
  it("is disabled, with the reason, when nothing is left in stock", () => {
    render(<MakeProducts item={stockItem({ onHand: 0 })} onMade={vi.fn()} />);

    const button = screen.getByRole("button", { name: "Make products" });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute("title", "Nothing left in stock");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("opens with one piece and says how much fabric is in stock", async () => {
    const { dialog } = await openSheet();

    expect(dialog).toHaveTextContent(
      "Each piece becomes a draft product and goes to the first work stage. 10 metres in stock.",
    );
    expect(piece(1).getByRole("textbox", { name: "Product name" })).toHaveValue("");
    expect(piece(1).getByRole("combobox", { name: "Category" })).toHaveValue("sarees");
    expect(piece(1).getByRole("textbox", { name: "Quantity (metres)" })).toHaveValue("");
    expect(screen.getByRole("status")).toHaveTextContent("Left after: 10 metres");
    expect(screen.queryByRole("button", { name: "Remove piece" })).not.toBeInTheDocument();
  });

  it("keeps Make products off until every piece has a name and a quantity above zero", async () => {
    const { dialog } = await openSheet();
    const submit = within(dialog).getByRole("button", { name: "Make products" });
    expect(submit).toBeDisabled();

    fireEvent.change(piece(1).getByRole("textbox", { name: "Product name" }), { target: { value: "Kurta" } });
    expect(submit).toBeDisabled();

    fireEvent.change(piece(1).getByRole("textbox", { name: "Quantity (metres)" }), { target: { value: "0" } });
    expect(submit).toBeDisabled();

    fireEvent.change(piece(1).getByRole("textbox", { name: "Quantity (metres)" }), { target: { value: "abc" } });
    expect(submit).toBeDisabled();

    fireEvent.change(piece(1).getByRole("textbox", { name: "Quantity (metres)" }), { target: { value: "2" } });
    expect(submit).toBeEnabled();
  });

  it("shows the pieces left after cutting, in red, and blocks Make products when they need more than there is", async () => {
    const { dialog } = await openSheet();
    fireEvent.change(piece(1).getByRole("textbox", { name: "Product name" }), { target: { value: "Kurta" } });
    fireEvent.change(piece(1).getByRole("textbox", { name: "Quantity (metres)" }), { target: { value: "11" } });

    const left = screen.getByRole("status");
    expect(left).toHaveTextContent("Left after: -1 metres");
    expect(left).toHaveClass("text-bad");
    expect(within(dialog).getByRole("button", { name: "Make products" })).toBeDisabled();
  });

  it("adds a piece that copies the category of the last one, and removes a piece", async () => {
    const { dialog } = await openSheet();
    fireEvent.change(piece(1).getByRole("combobox", { name: "Category" }), { target: { value: "lehengas" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "+ piece" }));

    expect(piece(2).getByRole("combobox", { name: "Category" })).toHaveValue("lehengas");
    expect(screen.getAllByRole("button", { name: "Remove piece" })).toHaveLength(2);

    fireEvent.click(screen.getAllByRole("button", { name: "Remove piece" })[0]!);
    expect(screen.getAllByRole("group")).toHaveLength(1);
    expect(piece(1).getByRole("combobox", { name: "Category" })).toHaveValue("lehengas");
    expect(screen.queryByRole("button", { name: "Remove piece" })).not.toBeInTheDocument();
  });

  it("stops adding pieces at twenty", async () => {
    const { dialog } = await openSheet();
    const add = within(dialog).getByRole("button", { name: "+ piece" });
    for (let i = 1; i < 20; i++) fireEvent.click(add);

    expect(screen.getAllByRole("group")).toHaveLength(20);
    expect(add).toBeDisabled();
  });

  it("shows the catalogue's problem and will not submit without categories", async () => {
    server.use(http.get(CATALOG, () => HttpResponse.json({ detail: "Catalogue is down" }, { status: 500 })));
    render(<MakeProducts item={stockItem()} onMade={vi.fn()} />);
    fireEvent.click(screen.getByRole("button", { name: "Make products" }));
    const dialog = screen.getByRole("dialog", { name: "Make products from this fabric" });
    // The sheet opens before the catalogue answers; the alert is the failed load.
    expect(await within(dialog).findByRole("alert")).toHaveTextContent("Catalogue is down");

    fireEvent.change(piece(1).getByRole("textbox", { name: "Product name" }), { target: { value: "Kurta" } });
    fireEvent.change(piece(1).getByRole("textbox", { name: "Quantity (metres)" }), { target: { value: "3" } });
    expect(within(dialog).getByRole("button", { name: "Make products" })).toBeDisabled();
  });

  it("makes the pieces with trimmed names and their own categories, then lists them and reports them", async () => {
    let sent: MakePieces | undefined;
    const result = [made(), made({ pieceId: 32, productId: 43, productName: "Dupatta", productSlug: "dupatta" })];
    server.use(
      http.post(PIECES, async ({ request }) => {
        sent = (await request.json()) as MakePieces;
        return HttpResponse.json(result);
      }),
    );
    const { dialog, onMade } = await openSheet();

    fireEvent.change(piece(1).getByRole("textbox", { name: "Product name" }), { target: { value: "  Kurta  " } });
    fireEvent.change(piece(1).getByRole("textbox", { name: "Quantity (metres)" }), { target: { value: "3" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "+ piece" }));
    fireEvent.change(piece(2).getByRole("combobox", { name: "Category" }), { target: { value: "lehengas" } });
    fireEvent.change(piece(2).getByRole("textbox", { name: "Product name" }), { target: { value: "Dupatta" } });
    fireEvent.change(piece(2).getByRole("textbox", { name: "Quantity (metres)" }), { target: { value: "2.5" } });
    expect(screen.getByRole("status")).toHaveTextContent("Left after: 4.5 metres");

    fireEvent.click(within(dialog).getByRole("button", { name: "Make products" }));

    expect(await within(dialog).findByText("2 draft products are on the workshop board.")).toBeInTheDocument();
    expect(sent).toEqual({
      stockItemId: 5,
      pieces: [
        { name: "Kurta", categorySlug: "sarees", quantity: 3, note: null },
        { name: "Dupatta", categorySlug: "lehengas", quantity: 2.5, note: null },
      ],
    });
    expect(onMade).toHaveBeenCalledWith(result);
    expect(within(dialog).getByRole("link", { name: "Kurta" })).toHaveAttribute("href", "/products/edit/?id=42");
    expect(within(dialog).getByRole("link", { name: "Dupatta" })).toHaveAttribute("href", "/products/edit/?id=43");
    expect(within(dialog).getByRole("link", { name: "Open workshop board" })).toHaveAttribute(
      "href",
      "/workshop/board/",
    );
  });

  it("uses the singular wording for one piece", async () => {
    server.use(http.post(PIECES, () => HttpResponse.json([made()])));
    const { dialog } = await openSheet();
    fireEvent.change(piece(1).getByRole("textbox", { name: "Product name" }), { target: { value: "Kurta" } });
    fireEvent.change(piece(1).getByRole("textbox", { name: "Quantity (metres)" }), { target: { value: "3" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Make products" }));

    expect(await within(dialog).findByText("1 draft product is on the workshop board.")).toBeInTheDocument();
  });

  it("shows Making while the pieces are made, and keeps the form when the API refuses", async () => {
    let release!: () => void;
    const gate = new Promise<void>((resolve) => {
      release = resolve;
    });
    server.use(
      http.post(PIECES, async () => {
        await gate;
        return HttpResponse.json([made()]);
      }),
    );
    const { dialog, onMade } = await openSheet();
    fireEvent.change(piece(1).getByRole("textbox", { name: "Product name" }), { target: { value: "Kurta" } });
    fireEvent.change(piece(1).getByRole("textbox", { name: "Quantity (metres)" }), { target: { value: "3" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Make products" }));

    expect(within(dialog).getByRole("button", { name: "Making…" })).toBeDisabled();
    release();
    expect(await within(dialog).findByText("1 draft product is on the workshop board.")).toBeInTheDocument();
    expect(onMade).toHaveBeenCalledTimes(1);
  });

  it("keeps the form and shows the reason when the pieces are refused", async () => {
    server.use(http.post(PIECES, () => HttpResponse.json({ detail: "Stock changed, try again" }, { status: 400 })));
    const { dialog, onMade } = await openSheet();
    fireEvent.change(piece(1).getByRole("textbox", { name: "Product name" }), { target: { value: "Kurta" } });
    fireEvent.change(piece(1).getByRole("textbox", { name: "Quantity (metres)" }), { target: { value: "3" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Make products" }));

    expect(await within(dialog).findByRole("alert")).toHaveTextContent("Stock changed, try again");
    expect(within(dialog).getByRole("button", { name: "Make products" })).toBeEnabled();
    expect(onMade).not.toHaveBeenCalled();
  });

  it("closes from the close button and starts again the next time", async () => {
    const { dialog } = await openSheet();
    fireEvent.change(piece(1).getByRole("textbox", { name: "Product name" }), { target: { value: "Kurta" } });

    fireEvent.click(within(dialog).getByRole("button", { name: "Close" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Make products" }));
    const reopened = screen.getByRole("dialog");
    expect(piece(1).getByRole("textbox", { name: "Product name" })).toHaveValue("");
    expect(reopened).toBeInTheDocument();
  });

  it("closes from the backdrop and from the Escape key", async () => {
    await openSheet();
    fireEvent.click(screen.getAllByRole("button", { name: "Close" })[0]!);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Make products" }));
    await screen.findByRole("option", { name: "Sarees" });
    fireEvent.keyDown(document, { key: "Escape" });
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("shows the finished pieces and does not reset when the sheet is closed afterwards", async () => {
    server.use(http.post(PIECES, () => HttpResponse.json([made()])));
    const { dialog } = await openSheet();
    fireEvent.change(piece(1).getByRole("textbox", { name: "Product name" }), { target: { value: "Kurta" } });
    fireEvent.change(piece(1).getByRole("textbox", { name: "Quantity (metres)" }), { target: { value: "3" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Make products" }));
    await within(dialog).findByRole("link", { name: "Kurta" });

    fireEvent.click(within(dialog).getByRole("button", { name: "Close" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  });
});
