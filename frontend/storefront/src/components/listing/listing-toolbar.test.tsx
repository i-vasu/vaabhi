import { act, fireEvent, render, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { parseListingState } from "@/lib/listing";
import { filters } from "@/test/listing-fixture";
import { ListingToolbar } from "./listing-toolbar";

const replace = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace }),
  usePathname: () => "/c/sarees",
}));

function renderToolbar(query: Record<string, string> = { fabric: "silk" }) {
  render(<ListingToolbar state={parseListingState(query)} groups={filters} total={4} />);
}

describe("ListingToolbar", () => {
  beforeEach(() => replace.mockClear());

  it("shows the applied filter count and the current sort", () => {
    renderToolbar({ fabric: "silk", inStock: "true", sort: "price_asc" });
    expect(screen.getByRole("button", { name: "Filter, 2 applied" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Sort: Price ↑/ })).toBeInTheDocument();
  });

  it("filters: groups in Figma order, live counts and URL updates", () => {
    renderToolbar();
    fireEvent.click(screen.getByRole("button", { name: /Filter/ }));

    const dialog = screen.getByRole("dialog", { name: "Filters" });
    const tabs = within(dialog).getAllByRole("tab");
    expect(tabs.map((t) => t.textContent)).toEqual([
      "Occasion",
      "Fabric1, 1 selected",
      "Colour",
      "Price",
      "Availability",
    ]);
    expect(within(dialog).getByRole("button", { name: "Show 4 results" })).toBeInTheDocument();

    fireEvent.click(within(dialog).getByRole("tab", { name: /Fabric/ }));
    expect(within(dialog).getByRole("checkbox", { name: /Silk/ })).toBeChecked();
    expect(within(dialog).getByRole("checkbox", { name: /Linen/ })).toBeDisabled();
    fireEvent.click(within(dialog).getByRole("checkbox", { name: /Cotton/ }));
    expect(replace).toHaveBeenLastCalledWith("/c/sarees?fabric=cotton%2Csilk", { scroll: false });

    fireEvent.click(within(dialog).getByRole("tab", { name: "Colour" }));
    expect(within(dialog).getByRole("checkbox", { name: "Gold, 0 products" })).toBeDisabled();
    fireEvent.click(within(dialog).getByRole("checkbox", { name: "Maroon, 2 products" }));
    expect(replace).toHaveBeenLastCalledWith("/c/sarees?colour=maroon&fabric=silk", { scroll: false });

    fireEvent.click(within(dialog).getByRole("tab", { name: "Price" }));
    expect(within(dialog).getByRole("radio", { name: "Any price" })).toBeChecked();
    fireEvent.click(within(dialog).getByRole("radio", { name: "₹2,000 – ₹5,000" }));
    expect(replace).toHaveBeenLastCalledWith("/c/sarees?fabric=silk&priceMin=2000&priceMax=5000", { scroll: false });

    fireEvent.click(within(dialog).getByRole("tab", { name: "Availability" }));
    fireEvent.click(within(dialog).getByRole("checkbox", { name: "In stock only" }));
    expect(replace).toHaveBeenLastCalledWith("/c/sarees?fabric=silk&inStock=true", { scroll: false });

    fireEvent.click(within(dialog).getByRole("button", { name: "Clear all" }));
    expect(replace).toHaveBeenLastCalledWith("/c/sarees", { scroll: false });

    fireEvent.click(within(dialog).getByRole("button", { name: "Show 4 results" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("filters: price and availability reflect the current state", () => {
    renderToolbar({ priceMax: "2000", inStock: "true" });
    fireEvent.click(screen.getByRole("button", { name: /Filter/ }));
    const dialog = screen.getByRole("dialog", { name: "Filters" });
    fireEvent.click(within(dialog).getByRole("tab", { name: /Price/ }));
    expect(within(dialog).getByRole("radio", { name: "Under ₹2,000" })).toBeChecked();
    fireEvent.click(within(dialog).getByRole("radio", { name: "Any price" }));
    expect(replace).toHaveBeenLastCalledWith("/c/sarees?inStock=true", { scroll: false });
    fireEvent.click(within(dialog).getByRole("tab", { name: /Availability/ }));
    fireEvent.click(within(dialog).getByRole("checkbox", { name: "In stock only" }));
    expect(replace).toHaveBeenLastCalledWith("/c/sarees?priceMax=2000", { scroll: false });
    fireEvent.click(within(dialog).getByRole("button", { name: "Clear" }));
    expect(replace).toHaveBeenLastCalledWith("/c/sarees", { scroll: false });
  });

  it("sort: picking an option updates the URL and closes the sheet", () => {
    renderToolbar();
    fireEvent.click(screen.getByRole("button", { name: /Sort/ }));
    const dialog = screen.getByRole("dialog", { name: "Sort by" });
    expect(within(dialog).getByRole("radio", { name: "Popular" })).toBeChecked();
    fireEvent.click(within(dialog).getByRole("radio", { name: "Price: low to high" }));
    expect(replace).toHaveBeenLastCalledWith("/c/sarees?fabric=silk&sort=price_asc", { scroll: false });
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("closes from the close button, the backdrop and the dialog's own close event", () => {
    renderToolbar();
    fireEvent.click(screen.getByRole("button", { name: /Sort/ }));
    fireEvent.click(screen.getByRole("button", { name: "Close" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /Filter/ }));
    fireEvent.click(screen.getByRole("dialog"));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /Filter/ }));
    const dialog = screen.getByRole("dialog") as HTMLDialogElement;
    act(() => dialog.close());
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});
