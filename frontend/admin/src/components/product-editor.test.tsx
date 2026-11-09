import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { AdminMedia, ProductForm } from "@vaabhi/api-client";
import { options, product } from "@/test/admin-fixtures";
import { API, server } from "@/test/server";
import { ProductEditor } from "./product-editor";

const replace = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace }) }));

// Uploads go to R2; here each one "succeeds" with the next media list, unless the file is named fail.*.
const uploaded: string[] = [];
const upload = async (_id: number, file: File) => {
  if (file.name.startsWith("fail")) throw new Error("R2 said no");
  uploaded.push(file.name);
  return MEDIA.slice(0, uploaded.length);
};
vi.mock("@/lib/photo-upload", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/photo-upload")>()),
  uploadPhoto: (id: number, file: File) => upload(id, file),
  uploadVideo: (id: number, file: File) => upload(id, file),
}));

async function fillNewProduct() {
  fireEvent.change(await screen.findByRole("textbox", { name: "Name" }), { target: { value: "Ruby silk saree" } });
  fireEvent.change(screen.getByRole("textbox", { name: "Colour name" }), { target: { value: "Ruby" } });
  fireEvent.click(screen.getByRole("button", { name: "Add colour" }));
}

const pick = (label: string, file: File) =>
  fireEvent.change(screen.getByLabelText(label), { target: { files: [file] } });

const OPTIONS = `${API}/api/v1/admin/catalog/options`;
const PRODUCTS = `${API}/api/v1/admin/products`;

// Ready to publish: a photo and a video.
const MEDIA: AdminMedia[] = [
  {
    id: 1,
    kind: "IMAGE",
    key: "products/7/p1",
    alt: "Front",
    colourName: null,
    width: 1200,
    height: 1600,
    status: "READY",
  },
  {
    id: 2,
    kind: "VIDEO",
    key: "products/7/v2",
    alt: "Drape",
    colourName: null,
    width: null,
    height: null,
    status: "READY",
  },
];

describe("ProductEditor", () => {
  beforeEach(() => {
    replace.mockClear();
    uploaded.length = 0;
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
    fireEvent.click(screen.getByRole("button", { name: "+ Unstitched blouse piece" }));
    fireEvent.click(screen.getByRole("button", { name: "+ Stitched blouse, standard size" }));
    fireEvent.click(screen.getByRole("button", { name: "+ Stitched blouse, made to measure" }));
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/products/edit/?id=42&notice=Saved."));
    expect(sent).toMatchObject({
      name: "Ruby silk saree",
      categorySlug: "sarees",
      sareeLengthM: 5.5,
      attributes: { fabric: ["silk"] },
      variants: [{ colourName: "Ruby", colourFamily: "red", size: null, priceRupees: 14250, stockQty: 4 }],
    });
    expect(sent?.addons.map((a) => a.code)).toEqual(["blouse-unstitched", "blouse-standard", "blouse-custom"]);
  });

  it("keeps Save on but Publish off until there is a photo (a video is optional), all at the bottom", async () => {
    render(<ProductEditor />);
    await fillNewProduct();
    const actions = screen.getByRole("group", { name: "Product actions" });
    expect(within(actions).getByRole("button", { name: "Save" })).toBeEnabled();
    expect(within(actions).getByRole("button", { name: "Publish" })).toBeDisabled();
    expect(actions).toHaveTextContent("Add a photo to publish.");

    pick("Add photos", new File(["x"], "front.jpg", { type: "image/jpeg" }));
    expect(within(actions).getByRole("button", { name: "Publish" })).toBeEnabled();
  });

  it("publishes a new product: creates it, uploads the waiting photo and video, then publishes", async () => {
    const calls: string[] = [];
    server.use(
      http.post(PRODUCTS, () => {
        calls.push("create");
        return HttpResponse.json(product({ id: 42 }), { status: 201 });
      }),
      http.post(`${PRODUCTS}/42/publish`, () => {
        calls.push(`publish after ${uploaded.join(", ")}`);
        return HttpResponse.json(product({ id: 42, status: "PUBLISHED", media: MEDIA }));
      }),
    );
    render(<ProductEditor />);
    await fillNewProduct();
    // Picked in the "wrong" order: the photo still uploads first.
    pick("Add photos", new File(["x"], "front.jpg", { type: "image/jpeg" }));
    pick("Add video", new File(["x"], "drape.mp4", { type: "video/mp4" }));
    fireEvent.click(screen.getByRole("button", { name: "Publish" }));

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/products/edit/?id=42&notice=Published."));
    expect(calls).toEqual(["create", "publish after front.jpg, drape.mp4"]);
  });

  it("keeps a new product as a draft when a waiting file does not upload", async () => {
    let published = false;
    server.use(
      http.post(PRODUCTS, () => HttpResponse.json(product({ id: 42 }), { status: 201 })),
      http.post(`${PRODUCTS}/42/publish`, () => {
        published = true;
        return HttpResponse.json(product({ id: 42, status: "PUBLISHED" }));
      }),
    );
    render(<ProductEditor />);
    await fillNewProduct();
    pick("Add photos", new File(["x"], "front.jpg", { type: "image/jpeg" }));
    pick("Add video", new File(["x"], "fail.mp4", { type: "video/mp4" }));
    fireEvent.click(screen.getByRole("button", { name: "Publish" }));

    await waitFor(() => expect(replace).toHaveBeenCalled());
    const url = new URL(replace.mock.lastCall![0], "https://admin.test");
    expect(url.searchParams.get("notice")).toBe("Saved as a draft.");
    expect(url.searchParams.get("problem")).toBe("Some files did not upload. fail.mp4: R2 said no");
    expect(published).toBe(false);
  });

  it("shows a problem passed from the create step", async () => {
    server.use(http.get(`${PRODUCTS}/7`, () => HttpResponse.json(product())));
    render(<ProductEditor id={7} notice="Saved." problem="Some files did not upload. a.jpg: no" />);
    expect(await screen.findByRole("alert")).toHaveTextContent("Some files did not upload. a.jpg: no");
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
      http.get(`${PRODUCTS}/7`, () => HttpResponse.json(product({ media: MEDIA }))),
      http.put(`${PRODUCTS}/7`, async ({ request }) => {
        const form = (await request.json()) as ProductForm;
        if (form.variants[0]!.mrpRupees < form.variants[0]!.priceRupees) {
          return HttpResponse.json(
            {
              status: 400,
              detail: "MRP must be at least the selling price",
              errors: [
                { field: "variants[0].mrpRupees", message: "MRP must be at least the selling price" },
                { field: "name", message: "Name is too long" },
              ],
            },
            { status: 400 },
          );
        }
        return HttpResponse.json(product({ status: status as "DRAFT", name: form.name, media: MEDIA }));
      }),
      http.post(`${PRODUCTS}/7/publish`, () => {
        status = "PUBLISHED";
        return HttpResponse.json(product({ status: "PUBLISHED", media: MEDIA }));
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
    fireEvent.click(screen.getByRole("button", { name: "Publish" }));
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
      http.get(`${PRODUCTS}/7`, () => HttpResponse.json(product({ media: MEDIA }))),
      http.put(`${PRODUCTS}/7`, () => HttpResponse.json(product({ media: MEDIA }))),
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

    fireEvent.click(await screen.findByRole("button", { name: "Publish" }));
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
    fireEvent.click(screen.getByRole("button", { name: "Add add-on" }));
    const option2 = within(screen.getByRole("group", { name: "Add-on 2" }));
    fireEvent.change(option2.getByRole("textbox", { name: "Name" }), { target: { value: "Fall and pico" } });
    fireEvent.change(option2.getByRole("textbox", { name: "Note" }), { target: { value: "Done for you" } });
    fireEvent.change(option2.getByRole("textbox", { name: "Price ₹" }), { target: { value: "250" } });
    fireEvent.change(option2.getByRole("combobox", { name: "Ask for" }), { target: { value: "SIZE" } });
    fireEvent.click(option2.getByRole("checkbox", { name: "Shown" }));
    fireEvent.click(screen.getByRole("button", { name: "Remove add-on 1" }));
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
      shippingWeightGrams: null,
      attributes: {},
      variants: [{ sku: "VB-RUBY-1", priceRupees: 15000, active: false }],
      addons: [
        {
          code: "fall-and-pico",
          name: "Fall and pico",
          description: "Done for you",
          priceRupees: 250,
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
