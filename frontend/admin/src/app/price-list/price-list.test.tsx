import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { lastSent, serveTailoring, type Sent } from "@/test/tailoring-fixtures";
import PriceListPage from "./page";

vi.mock("next/navigation", () => ({ useRouter: () => ({ back: vi.fn(), push: vi.fn(), replace: vi.fn() }) }));

let sent: Sent[];
beforeEach(() => {
  sent = serveTailoring().sent;
});

describe("price list", () => {
  it("groups items by garment with their profit", async () => {
    render(<PriceListPage />);
    expect(await screen.findByRole("heading", { name: "Blouse" })).toBeInTheDocument();
    expect(screen.getByText("BL-002 · profit ₹ 350")).toBeInTheDocument();
    expect(screen.getByText("₹ 500")).toBeInTheDocument();
  });

  it("adds an item with piece rates and shows the profit as you type", async () => {
    render(<PriceListPage />);
    await screen.findByText("3 dart blouse");
    fireEvent.click(screen.getByRole("button", { name: /Add item/ }));
    const dialog = await screen.findByRole("dialog", { name: "New item" });

    fireEvent.click(within(dialog).getByRole("button", { name: "Save item" }));
    expect(await within(dialog).findByText("Enter the name.")).toBeInTheDocument();

    fireEvent.change(within(dialog).getByLabelText("Name"), { target: { value: "Padded blouse" } });
    fireEvent.change(within(dialog).getByLabelText("Price (₹)"), { target: { value: "900" } });
    fireEvent.change(within(dialog).getByLabelText("Stitching"), { target: { value: "200" } });
    expect(within(dialog).getByText("₹ 700")).toBeInTheDocument();
    fireEvent.click(within(dialog).getByRole("button", { name: "Save item" }));

    await waitFor(() =>
      expect(lastSent(sent, "POST", "/service-products")?.body).toEqual({
        templateId: 1,
        code: null,
        name: "Padded blouse",
        priceRupees: 900,
        photoKey: null,
        stageCosts: [{ stageId: 2, rupees: 200 }],
      }),
    );
  });

  it("edits and removes an item", async () => {
    render(<PriceListPage />);
    fireEvent.click(await screen.findByRole("button", { name: /3 dart blouse/ }));
    const dialog = await screen.findByRole("dialog", { name: "Edit item" });
    expect(within(dialog).getByLabelText("Stitching")).toHaveValue("150");
    fireEvent.click(within(dialog).getByRole("button", { name: /Remove/ }));
    await waitFor(() => expect(lastSent(sent, "DELETE", "/service-products/11")).toBeDefined());
  });
});
