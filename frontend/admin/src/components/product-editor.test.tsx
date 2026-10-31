import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ProductForm } from "@vaabhi/api-client";
import { options, product } from "@/test/admin-fixtures";
import { API, server } from "@/test/server";
import { ProductEditor } from "./product-editor";

const replace = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace }) }));

const OPTIONS = `${API}/api/v1/admin/catalog/options`;
const PRODUCTS = `${API}/api/v1/admin/products`;

describe("ProductEditor", () => {
  beforeEach(() => {
    replace.mockClear();
    server.use(http.get(OPTIONS, () => HttpResponse.json(options)));
  });

  it("creates a draft from scratch and opens its edit page", async () => {
    let sent: ProductForm | undefined;
    server.use(
      http.post(PRODUCTS, async ({ request }) => {
        sent = (await request.json()) as ProductForm;
        return HttpResponse.json(product({ id: 42 }), { status: 201 });
      }),
    );
    render(<ProductEditor />);

    fireEvent.change(await screen.findByRole("textbox", { name: "Name" }), { target: { value: "Ruby silk saree" } });
    fireEvent.click(screen.getByRole("checkbox", { name: "Silk" }));
    fireEvent.change(screen.getByRole("textbox", { name: "Colour name" }), { target: { value: "Ruby" } });
    fireEvent.change(screen.getByRole("combobox", { name: "Colour group" }), { target: { value: "red" } });
    fireEvent.change(screen.getByRole("textbox", { name: "Price ₹" }), { target: { value: "14250" } });
    fireEvent.change(screen.getByRole("textbox", { name: "MRP ₹" }), { target: { value: "17800" } });
    fireEvent.click(screen.getByRole("button", { name: "Add colour" }));
    fireEvent.change(screen.getByRole("textbox", { name: "Stock for Ruby" }), { target: { value: "4" } });
    fireEvent.click(screen.getByRole("button", { name: "Add standard blouse options" }));
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/products/edit/?id=42&notice=Saved."));
    expect(sent).toMatchObject({
      name: "Ruby silk saree",
      categorySlug: "sarees",
      sareeLengthM: 5.5,
      attributes: { fabric: ["silk"] },
      variants: [{ colourName: "Ruby", colourFamily: "red", size: null, pricePaise: 1425000, stockQty: 4 }],
    });
    expect(sent?.addons.map((a) => a.code)).toEqual(["blouse-unstitched", "blouse-standard", "blouse-custom"]);
  });

  it("adds one row per chosen size for stitched categories", async () => {
    render(<ProductEditor />);
    fireEvent.change(await screen.findByRole("combobox", { name: "Category" }), { target: { value: "lehengas" } });
    expect(screen.queryByRole("heading", { name: "Saree details" })).not.toBeInTheDocument();

    fireEvent.change(screen.getByRole("textbox", { name: "Colour name" }), { target: { value: "Mint" } });
    fireEvent.click(screen.getByRole("checkbox", { name: "M" }));
    fireEvent.click(screen.getByRole("checkbox", { name: "L" }));
    fireEvent.click(screen.getByRole("checkbox", { name: "L" }));
    fireEvent.click(screen.getByRole("checkbox", { name: "S" }));
    fireEvent.click(screen.getByRole("button", { name: "Add colour" }));

    expect(screen.getByRole("textbox", { name: "Price for Mint M" })).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Price for Mint S" })).toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: "Price for Mint L" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Remove Mint M" }));
    expect(screen.queryByRole("textbox", { name: "Price for Mint M" })).not.toBeInTheDocument();
  });

  it("edits, shows field errors from the API, then publishes", async () => {
    let status = "DRAFT";
    server.use(
      http.get(`${PRODUCTS}/7`, () => HttpResponse.json(product())),
      http.put(`${PRODUCTS}/7`, async ({ request }) => {
        const form = (await request.json()) as ProductForm;
        if (form.variants[0]!.mrpPaise < form.variants[0]!.pricePaise) {
          return HttpResponse.json(
            {
              status: 400,
              detail: "MRP must be at least the selling price",
              errors: [
                { field: "variants[0].mrpPaise", message: "MRP must be at least the selling price" },
                { field: "name", message: "Name is too long" },
              ],
            },
            { status: 400 },
          );
        }
        return HttpResponse.json(product({ status: status as "DRAFT", name: form.name }));
      }),
      http.post(`${PRODUCTS}/7/publish`, () => {
        status = "PUBLISHED";
        return HttpResponse.json(product({ status: "PUBLISHED" }));
      }),
    );
    render(<ProductEditor id={7} notice="Saved." />);

    expect(await screen.findByRole("heading", { level: 1, name: "Ruby silk saree" })).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("Saved.");
    expect(screen.getByRole("checkbox", { name: "Silk" })).toBeChecked();

    fireEvent.change(screen.getByRole("textbox", { name: "MRP for Ruby" }), { target: { value: "100" } });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));
    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("MRP must be at least the selling price");
    expect(within(alert).getAllByRole("listitem")).toHaveLength(2);
    expect(screen.getByText("Name is too long")).toBeInTheDocument();
    expect(within(alert).getAllByRole("listitem")[1]).toHaveTextContent("name: Name is too long");

    fireEvent.change(screen.getByRole("textbox", { name: "MRP for Ruby" }), { target: { value: "17800" } });
    fireEvent.click(screen.getByRole("button", { name: "Save & publish" }));
    expect(await screen.findByText("Published.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "View in store" })).toHaveAttribute(
      "href",
      "https://vaabhi.com/p/ruby-silk-saree",
    );
    expect(replace).not.toHaveBeenCalled();
  });

  it("unpublishes and archives a published product", async () => {
    server.use(
      http.get(`${PRODUCTS}/7`, () => HttpResponse.json(product({ status: "PUBLISHED" }))),
      http.put(`${PRODUCTS}/7`, () => HttpResponse.json(product({ status: "PUBLISHED" }))),
      http.post(`${PRODUCTS}/7/unpublish`, () => HttpResponse.json(product({ status: "DRAFT" }))),
      http.post(`${PRODUCTS}/7/archive`, () => HttpResponse.json(product({ status: "ARCHIVED" }))),
    );
    render(<ProductEditor id={7} />);

    fireEvent.click(await screen.findByRole("button", { name: "Unpublish" }));
    expect(await screen.findByText("Draft.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Archive" }));
    expect(await screen.findByText("Archived.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Archive" })).not.toBeInTheDocument();
  });

  it("reports a failed status change and a failed save", async () => {
    server.use(
      http.get(`${PRODUCTS}/7`, () => HttpResponse.json(product())),
      http.put(`${PRODUCTS}/7`, () => HttpResponse.json(product())),
      http.post(`${PRODUCTS}/7/publish`, () =>
        HttpResponse.json(
          {
            detail: "Add at least one active variant before publishing",
            errors: [{ field: "variants", message: "Add at least one active variant before publishing" }],
          },
          { status: 400 },
        ),
      ),
    );
    render(<ProductEditor id={7} />);

    fireEvent.click(await screen.findByRole("button", { name: "Save & publish" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Add at least one active variant before publishing");
    expect(screen.getAllByText("Add at least one active variant before publishing")).toHaveLength(2);

    server.use(http.put(`${PRODUCTS}/7`, () => HttpResponse.error()));
    fireEvent.click(screen.getByRole("button", { name: "Save" }));
    expect(await screen.findByText(/Could not reach the API/)).toBeInTheDocument();
  });

  it("edits every field and add-on row", async () => {
    let sent: ProductForm | undefined;
    server.use(
      http.get(`${PRODUCTS}/7`, () => HttpResponse.json(product())),
      http.put(`${PRODUCTS}/7`, async ({ request }) => {
        sent = (await request.json()) as ProductForm;
        return HttpResponse.json(product());
      }),
    );
    render(<ProductEditor id={7} />);
    const text = (name: string, value: string) =>
      fireEvent.change(screen.getByRole("textbox", { name }), { target: { value } });

    await screen.findByRole("heading", { level: 1 });
    text("Subtitle (shown on the card)", "Temple border");
    text("URL name Leave empty to generate it from the name", "ruby-saree");
    text("Care instructions", "Dry clean only");
    text("Description", "Handwoven");
    fireEvent.change(screen.getByRole("combobox", { name: "Badge" }), { target: { value: "" } });
    text("Saree length (m)", "6.3");
    text("Blouse length (m)", "1");
    text("Price for Ruby", "15000");
    text("SKU for Ruby", "vb-ruby-1");
    fireEvent.click(screen.getByRole("checkbox", { name: "Ruby is for sale" }));
    fireEvent.click(screen.getByRole("checkbox", { name: "Silk" }));
    fireEvent.click(screen.getByRole("button", { name: "Add option" }));
    const option2 = within(screen.getByRole("group", { name: "Option 2" }));
    fireEvent.change(option2.getByRole("textbox", { name: "Name" }), { target: { value: "Fall and pico" } });
    fireEvent.change(option2.getByRole("textbox", { name: "Code" }), { target: { value: "fall-pico" } });
    fireEvent.change(option2.getByRole("textbox", { name: "Note" }), { target: { value: "Done for you" } });
    fireEvent.change(option2.getByRole("textbox", { name: "Price ₹" }), { target: { value: "250" } });
    fireEvent.change(option2.getByRole("combobox", { name: "Ask for" }), { target: { value: "SIZE" } });
    fireEvent.click(option2.getByRole("checkbox", { name: "On" }));
    fireEvent.click(screen.getByRole("button", { name: "Remove option 1" }));
    fireEvent.click(screen.getByRole("checkbox", { name: "Blouse piece included" }));
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(sent).toBeDefined());
    expect(sent).toMatchObject({
      subtitle: "Temple border",
      slug: "ruby-saree",
      careInstructions: "Dry clean only",
      description: "Handwoven",
      badge: null,
      sareeLengthM: 6.3,
      blouseIncluded: false,
      blouseLengthM: null,
      attributes: {},
      variants: [{ sku: "VB-RUBY-1", pricePaise: 1500000, active: false }],
      addons: [
        {
          code: "fall-pico",
          name: "Fall and pico",
          description: "Done for you",
          pricePaise: 25000,
          input: "SIZE",
          active: false,
        },
      ],
    });
  });

  it("asks to sign in when the API refuses", async () => {
    server.use(http.get(OPTIONS, () => new HttpResponse(null, { status: 401 })));
    render(<ProductEditor />);

    expect(await screen.findByText(/session has expired/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Sign in" })).toHaveAttribute(
      "href",
      expect.stringMatching(/^\/login\/?$/),
    );
  });
});
