import { act, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { bagStore, wishlistStore } from "@/lib/bag";
import { lehenga, saree } from "@/test/product-fixture";
import { ProductView } from "./product-view";

const radio = (name: string | RegExp) => screen.getByRole("radio", { name });
const addButton = () => screen.getAllByRole("button", { name: /add to bag|out of stock/i })[0]!;

describe("ProductView", () => {
  afterEach(() => {
    localStorage.clear();
    vi.unstubAllGlobals();
  });

  it("shows the product with the blouse choice and adds it to the bag", () => {
    render(<ProductView product={saree()} />);

    expect(screen.getByRole("heading", { level: 1, name: "Kanjeevaram silk saree" })).toBeInTheDocument();
    expect(screen.getAllByText("₹18,500").length).toBeGreaterThan(0);
    expect(screen.getByText("16% off")).toBeInTheDocument();
    expect(screen.getByText(/one size · 5.5 m saree with a 0.8 m blouse piece/)).toBeInTheDocument();
    expect(screen.getByText("Only 2 left in Rani pink")).toBeInTheDocument();
    expect(radio(/Unstitched blouse piece/)).toBeChecked();

    fireEvent.click(radio(/Stitched blouse, standard size/));
    expect(screen.getAllByText("₹19,700")).toHaveLength(2);
    fireEvent.click(addButton());
    expect(screen.getByText("Choose a blouse size.")).toBeInTheDocument();

    fireEvent.click(radio("36"));
    fireEvent.click(addButton());
    expect(screen.getByText(/Added to bag/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "View bag" })).toHaveAttribute("href", "/bag");
    expect(bagStore.read()).toEqual([
      expect.objectContaining({
        sku: "VB-RANI",
        addonCode: "blouse-standard",
        blouseSize: "36",
        unitPricePaise: 1850000,
        addonPricePaise: 120000,
        imageKey: "products/1/front",
        quantity: 1,
      }),
    ]);

    fireEvent.click(radio(/made to measure/));
    expect(screen.getByText(/we call you within a day/)).toBeInTheDocument();
  });

  it("switches colour, photos and stock; out-of-stock colours cannot be added", () => {
    render(<ProductView product={saree()} colour="Mustard" />);

    expect(radio(/Mustard/)).toBeChecked();
    expect(screen.getByText("1 / 2")).toBeInTheDocument();
    expect(screen.getByText("Out of stock in Mustard")).toBeInTheDocument();
    expect(addButton()).toBeDisabled();

    fireEvent.click(radio("Rani pink"));
    expect(screen.queryByText("1 / 2")).not.toBeInTheDocument();
    expect(addButton()).toBeEnabled();
  });

  it("needs a size for sized products and offers optional stitching", () => {
    render(<ProductView product={lehenga()} />);

    expect(screen.getByText("Choose a size to see availability")).toBeInTheDocument();
    expect(radio(/Without blouse stitching/)).toBeChecked();
    fireEvent.click(addButton());
    expect(screen.getByText("Choose a size first.")).toBeInTheDocument();

    expect(radio(/^M.*out of stock/)).toBeDisabled();
    fireEvent.click(radio("S"));
    expect(screen.getByText("In stock in Red, size S")).toBeInTheDocument();
    fireEvent.click(radio(/Made-to-measure blouse/));
    expect(screen.getByText(/Lehenga ₹58,000 \+ made-to-measure blouse ₹1,500/)).toBeInTheDocument();
    fireEvent.click(addButton());
    expect(bagStore.read()[0]).toEqual(expect.objectContaining({ sku: "VB-RED-S", size: "S", blouseSize: null }));

    fireEvent.click(radio("Wine"));
    expect(screen.queryByText(/% off/)).not.toBeInTheDocument();
  });

  it("opens the size guide and toggles the wishlist", () => {
    render(<ProductView product={saree()} />);

    fireEvent.click(radio(/standard size/));
    fireEvent.click(screen.getByRole("button", { name: "Size guide" }));
    const guide = screen.getByRole("dialog", { name: "Blouse size guide" });
    expect(within(guide).getAllByRole("row")).toHaveLength(8);
    fireEvent.click(within(guide).getByRole("button", { name: "Close" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();

    fireEvent.click(screen.getAllByRole("button", { name: "Add to wishlist" })[0]!);
    expect(wishlistStore.read()).toEqual(["kanjeevaram-silk-saree"]);
    expect(screen.getAllByRole("button", { name: "Remove from wishlist" })).toHaveLength(2);
  });

  it("steps through photos and checks delivery", () => {
    render(<ProductView product={saree()} colour="Mustard" />);

    fireEvent.click(screen.getByRole("button", { name: "Next photo" }));
    expect(screen.getByRole("button", { name: "Show photo 2" })).toHaveAttribute("aria-current", "true");
    fireEvent.click(screen.getByRole("button", { name: "Previous photo" }));
    expect(screen.getByRole("button", { name: "Show photo 1" })).toHaveAttribute("aria-current", "true");
    fireEvent.click(screen.getByRole("button", { name: "Show photo 2" }));
    expect(screen.getByText("2 / 2")).toBeInTheDocument();

    const pincode = screen.getByRole("textbox", { name: "Pincode" });
    fireEvent.change(pincode, { target: { value: "12a" } });
    expect(pincode).toHaveValue("12");
    fireEvent.click(screen.getByRole("button", { name: "CHECK" }));
    expect(screen.getByText(/Enter a 6-digit pincode/)).toBeInTheDocument();
    fireEvent.change(pincode, { target: { value: "600001" } });
    fireEvent.click(screen.getByRole("button", { name: "CHECK" }));
    expect(screen.getByText(/Estimated delivery .* to 600001 · COD available/)).toBeInTheDocument();
  });

  it("shows the sticky bar once the buttons scroll away", () => {
    let callback: (entries: Array<Partial<IntersectionObserverEntry>>) => void = () => {};
    const disconnect = vi.fn();
    vi.stubGlobal(
      "IntersectionObserver",
      class {
        constructor(cb: typeof callback) {
          callback = cb;
        }
        observe() {}
        disconnect = disconnect;
      },
    );
    const { unmount } = render(<ProductView product={saree()} />);

    act(() => callback([{ isIntersecting: false, boundingClientRect: { top: -50 } as DOMRect }]));
    const bars = screen.getAllByRole("button", { name: "Add to bag" });
    expect(bars).toHaveLength(2);
    fireEvent.click(bars[1]!);
    expect(bagStore.read()).toHaveLength(1);
    unmount();
    expect(disconnect).toHaveBeenCalled();
  });
});
