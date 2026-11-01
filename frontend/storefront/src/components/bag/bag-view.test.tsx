import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { afterEach, describe, expect, it } from "vitest";
import type { BagQuote } from "@vaabhi/api-client";
import { bagStore, wishlistStore, type BagLine } from "@/lib/bag";
import { quoteLine } from "@/lib/bag-quote.test";
import { server } from "@/test/server";
import { BagView } from "./bag-view";

const QUOTE = "*/api/bag/quote";

const saved = (o: Partial<BagLine> = {}): BagLine => ({
  sku: "VB-RANI",
  slug: "kanjeevaram",
  name: "Kanjeevaram silk saree",
  colourName: "Rani pink",
  colourHex: "#C2185B",
  size: null,
  addonCode: "blouse-standard",
  addonName: "Stitched blouse, standard size",
  blouseSize: "36",
  unitPricePaise: 1850000,
  addonPricePaise: 120000,
  imageKey: null,
  quantity: 1,
  ...o,
});

const quoteOf = (lines: BagQuote["lines"], totals: Partial<BagQuote> = {}): BagQuote => ({
  lines,
  itemsPaise: 1850000,
  addonsPaise: 120000,
  mrpPaise: 2320000,
  totalPaise: 1970000,
  ...totals,
});

describe("BagView", () => {
  afterEach(() => localStorage.clear());

  it("shows the empty bag with category links", () => {
    render(<BagView />);
    expect(screen.getByRole("heading", { name: "Your bag is empty" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Sarees" })).toHaveAttribute("href", expect.stringMatching(/^\/c\/sarees/));
    expect(screen.getByRole("link", { name: "Continue shopping" })).toBeInTheDocument();
  });

  it("prices the bag from the shop, changes quantity and removes with undo", async () => {
    bagStore.write([
      saved(),
      saved({
        sku: "VB-KURTA",
        slug: "kurta",
        name: "Kurta set",
        size: "M",
        addonCode: null,
        addonName: null,
        blouseSize: null,
        unitPricePaise: 480000,
        addonPricePaise: 0,
        quantity: 2,
      }),
    ]);
    const requests: unknown[] = [];
    server.use(
      http.post(QUOTE, async ({ request }) => {
        const body = (await request.json()) as { lines: Array<{ sku: string; quantity: number }> };
        requests.push(body);
        return HttpResponse.json(
          quoteOf(
            [
              quoteLine({
                name: "Kanjeevaram silk saree",
                pricePaise: 1850000,
                mrpPaise: 2200000,
                addonName: "Stitched blouse",
                addonPricePaise: 120000,
                addonInput: "SIZE",
                stockLeft: 2,
                maxQuantity: 2,
              }),
              quoteLine({
                sku: "VB-KURTA",
                name: "Kurta set",
                pricePaise: 450000,
                mrpPaise: 620000,
                quantity: body.lines[1]?.quantity ?? 2,
              }),
            ].filter((l) => body.lines.some((r) => r.sku === l.sku)),
            { itemsPaise: 2750000, addonsPaise: 120000, mrpPaise: 3560000, totalPaise: 2870000 },
          ),
        );
      }),
    );
    render(<BagView />);

    expect(await screen.findAllByText("₹28,700")).toHaveLength(2);
    expect(screen.getByText("3 items · saved on this device")).toBeInTheDocument();
    expect(screen.getByText("Calculated at checkout")).toBeInTheDocument();
    expect(screen.getByText("−₹6,900")).toBeInTheDocument();
    expect(screen.getByText("Only 2 left")).toBeInTheDocument();
    expect(screen.getByText(/Stitching adds 5–7 days/)).toBeInTheDocument();
    expect(screen.getByText("Price updated from ₹4,800")).toBeInTheDocument();
    expect(screen.getByText("₹4,500 each")).toBeInTheDocument();
    expect(screen.getByText(/size 36/)).toBeInTheDocument();

    const kurta = within(screen.getByRole("group", { name: "Quantity of Kurta set" }));
    expect(kurta.getByRole("button", { name: "Increase quantity" })).toBeEnabled();
    fireEvent.click(kurta.getByRole("button", { name: "Decrease quantity" }));
    expect(bagStore.read()[1]!.quantity).toBe(1);
    await waitFor(() => expect(requests.length).toBeGreaterThan(1));

    fireEvent.click(screen.getAllByRole("button", { name: "Remove" })[0]!);
    expect(bagStore.read().map((l) => l.sku)).toEqual(["VB-KURTA"]);
    fireEvent.click(screen.getByRole("button", { name: "Undo" }));
    expect(bagStore.read().map((l) => l.sku)).toEqual(["VB-RANI", "VB-KURTA"]);

    fireEvent.click(screen.getAllByRole("button", { name: "Move to wishlist" })[1]!);
    expect(wishlistStore.read()).toEqual(["kurta"]);
    expect(screen.getByText("Moved Kurta set to your wishlist")).toBeInTheDocument();

    await waitFor(() => expect(screen.getAllByRole("button", { name: /checkout/i })[0]).toBeEnabled());
    fireEvent.click(screen.getByRole("button", { name: "Checkout securely" }));
    expect(screen.getByText(/Checkout opens with the next update/)).toBeInTheDocument();
  });

  it("blocks checkout for unavailable items and lowers limited quantities", async () => {
    bagStore.write([
      saved({ quantity: 3 }),
      saved({
        sku: "GONE",
        name: "Linen saree",
        addonCode: "blouse-unstitched",
        addonName: "Unstitched blouse piece",
        blouseSize: null,
        addonPricePaise: 0,
      }),
    ]);
    server.use(
      http.post(QUOTE, () =>
        HttpResponse.json(
          quoteOf([
            quoteLine({
              status: "LIMITED",
              maxQuantity: 2,
              quantity: 3,
              name: "Kanjeevaram silk saree",
              pricePaise: 1850000,
              addonPricePaise: 120000,
            }),
            quoteLine({ sku: "GONE", status: "UNAVAILABLE", maxQuantity: 0, name: null, pricePaise: 0, mrpPaise: 0 }),
          ]),
        ),
      ),
    );
    render(<BagView />);

    expect(await screen.findByText("No longer available. Remove it to check out.")).toBeInTheDocument();
    await waitFor(() => expect(bagStore.read()[0]!.quantity).toBe(2));
    expect(screen.getByText(/so we lowered the quantity/)).toBeInTheDocument();
    expect(screen.getByText("Fix the items marked above to continue.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Checkout securely" })).toBeDisabled();
    expect(screen.getByText("(included)")).toBeInTheDocument();
  });

  it("explains out-of-stock lines and a failed price check", async () => {
    bagStore.write([saved()]);
    server.use(
      http.post(QUOTE, () => HttpResponse.json(quoteOf([quoteLine({ status: "OUT_OF_STOCK", maxQuantity: 0 })]))),
    );
    const { unmount } = render(<BagView />);
    expect(await screen.findByText("Out of stock. Remove it to check out.")).toBeInTheDocument();
    unmount();

    server.use(http.post(QUOTE, () => new HttpResponse(null, { status: 502 })));
    render(<BagView />);
    expect(await screen.findByRole("alert")).toHaveTextContent("We could not check current prices.");
    expect(screen.getByText("Prices could not be checked.")).toBeInTheDocument();
    server.use(http.post(QUOTE, () => HttpResponse.json(quoteOf([quoteLine()]))));
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(await screen.findAllByText("₹19,700")).not.toHaveLength(0);
  });
});
