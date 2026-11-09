import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { MaterialForm, ProductMaking, StaffMember, StockItem, WorkForm, WorkPiece } from "@vaabhi/api-client";
import { API, server } from "@/test/server";
import { ProductMakingSection } from "./product-making";

type Material = ProductMaking["materials"][number];
type Work = ProductMaking["work"][number];

const PRODUCTS = `${API}/api/v1/admin/products`;
const MAKING = `${PRODUCTS}/7/making`;
const BY_PRODUCT = `${API}/api/v1/admin/workshop/pieces/by-product/7`;
const STOCK = `${API}/api/v1/admin/stock`;
const STAFF = `${API}/api/v1/admin/staff`;

const making = (overrides: Partial<ProductMaking> = {}): ProductMaking => ({
  materials: [],
  work: [],
  costRupees: 0,
  ...overrides,
});

const material = (overrides: Partial<Material> = {}): Material => ({
  id: 1,
  stockItemId: 5,
  stockItemName: "Silk lot",
  unit: "M",
  quantity: 2.5,
  costRupees: 900,
  supplierName: "Surat Mills",
  material: "silk",
  gsm: 120,
  threadCount: 60,
  note: null,
  createdAt: "2026-11-01T10:00:00Z",
  ...overrides,
});

const work = (overrides: Partial<Work> = {}): Work => ({
  id: 1,
  staffId: 3,
  workerName: "Ravi",
  workType: "Stitching",
  costRupees: 300,
  doneOn: "2026-11-01",
  note: null,
  ...overrides,
});

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

const staff = (overrides: Partial<StaffMember> = {}): StaffMember => ({
  id: 3,
  name: "Ravi",
  phone: null,
  email: null,
  role: "STAFF",
  designation: null,
  joinedOn: null,
  payType: "DAILY",
  salaryRupees: 0,
  hourlyRupees: 0,
  shiftStart: "09:00",
  shiftEnd: "18:00",
  lateGraceMinutes: 10,
  weeklyOff: null,
  active: true,
  canSignIn: false,
  lastLoginAt: null,
  ...overrides,
});

const piece = (overrides: Partial<WorkPiece> = {}): WorkPiece => ({
  pieceId: 31,
  productId: 7,
  productName: "Ruby silk saree",
  productSlug: "ruby-silk-saree",
  productStatus: "DRAFT",
  stockItemId: 5,
  stockItemName: "Silk lot",
  quantity: 2.5,
  unit: "M",
  photoKey: null,
  stage: { id: 2, name: "Stitching", kind: "WORK" },
  stageSince: "2026-11-02T09:00:00Z",
  assignedTo: { id: 3, name: "Ravi" },
  assignedPhone: null,
  note: null,
  photos: [],
  fabric: { material: "silk", colour: null, widthIn: null, gsm: 120, threadCount: 60 },
  dimensions: [],
  ...overrides,
});

function renderSection(onFabric = vi.fn()) {
  render(<ProductMakingSection productId={7} onFabric={onFabric} />);
  return onFabric;
}

// The material and work forms each sit in their own grid, so their buttons are scoped by a field inside them.
const materialForm = () => screen.getByLabelText(/^Stock item/).closest("div")!;
const workForm = () => screen.getByLabelText("Who").closest("div")!;

beforeEach(() => {
  server.use(
    http.get(MAKING, () => HttpResponse.json(making())),
    http.get(BY_PRODUCT, () => new HttpResponse(null, { status: 404 })),
    http.get(STOCK, () => HttpResponse.json([stockItem()])),
    http.get(STAFF, () => HttpResponse.json([staff()])),
  );
});

describe("ProductMakingSection", () => {
  it("asks for the product to be saved first, and loads nothing", () => {
    const seen = vi.fn();
    server.use(
      http.get(MAKING, () => {
        seen();
        return HttpResponse.json(making());
      }),
    );
    render(<ProductMakingSection onFabric={vi.fn()} />);

    expect(screen.getByRole("heading", { name: "Made from & work done" })).toBeInTheDocument();
    expect(
      screen.getByText("Save the product first, then record which fabric it was cut from and who stitched it."),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
    expect(seen).not.toHaveBeenCalled();
  });

  it("shows the materials, work and cost recorded so far", async () => {
    server.use(
      http.get(MAKING, () =>
        HttpResponse.json(
          making({
            materials: [
              material(),
              material({
                id: 2,
                stockItemId: 6,
                stockItemName: "Cotton lot",
                unit: "PCS",
                quantity: 4,
                costRupees: 500,
                supplierName: null,
                gsm: null,
                threadCount: null,
              }),
            ],
            work: [work()],
            costRupees: 1400,
          }),
        ),
      ),
      http.get(STOCK, () =>
        HttpResponse.json([
          stockItem({ photos: [{ id: 9, key: "stock/5/p9" }] }),
          stockItem({ id: 6, name: "Cotton lot" }),
        ]),
      ),
    );
    renderSection();

    expect(await screen.findByText("Cut from Silk lot")).toBeInTheDocument();
    expect(screen.getByText(/Cost so far: ₹ 1,400\./)).toBeInTheDocument();
    expect(screen.getByRole("img", { name: "Fabric: Silk lot" })).toBeInTheDocument();
    expect(screen.getByText("2.5 m")).toBeInTheDocument();
    expect(screen.getByText("from Surat Mills · 120 GSM · 60 TC · ₹ 900")).toBeInTheDocument();

    const cotton = screen.getByText("Cut from Cotton lot").closest("li")!;
    expect(cotton).toHaveTextContent("4 pcs");
    expect(cotton).toHaveTextContent("₹ 500");
    expect(screen.queryByRole("img", { name: "Fabric: Cotton lot" })).not.toBeInTheDocument();

    expect(screen.getByText("Ravi").closest("li")).toHaveTextContent("₹ 300 · 2026-11-01");
    expect(screen.getByText("Stitching")).toBeInTheDocument();
    expect(document.querySelector("#making-staff option")?.getAttribute("value")).toBe("Ravi");
  });

  it("links to the workshop card of a product that is on the board", async () => {
    server.use(http.get(BY_PRODUCT, () => HttpResponse.json(piece())));
    renderSection();

    const line = (await screen.findByText("Stitching")).closest("p")!;
    expect(line).toHaveTextContent("In workshop: Stitching · with Ravi");
    expect(screen.getByRole("link", { name: "Work board" })).toHaveAttribute("href", "/workshop/board/");
  });

  it("shows the workshop stage without a person when nobody is assigned", async () => {
    server.use(
      http.get(BY_PRODUCT, () =>
        HttpResponse.json(piece({ stage: { id: 1, name: "Cutting", kind: "WORK" }, assignedTo: null })),
      ),
    );
    renderSection();

    const line = (await screen.findByText("Cutting")).closest("p")!;
    expect(line).toHaveTextContent("In workshop: Cutting");
    expect(line).not.toHaveTextContent("with");
  });

  it("explains that there is no stock yet when the stock list is empty", async () => {
    server.use(http.get(STOCK, () => HttpResponse.json([])));
    renderSection();

    expect(
      await screen.findByText("No stock items yet. Add the fabric you bought on the Stock page."),
    ).toBeInTheDocument();
    expect(screen.queryByLabelText(/^Stock item/)).not.toBeInTheDocument();
  });

  it("treats a stock list that fails to load as empty", async () => {
    server.use(http.get(STOCK, () => HttpResponse.json({ detail: "Stock is down" }, { status: 500 })));
    renderSection();

    expect(
      await screen.findByText("No stock items yet. Add the fabric you bought on the Stock page."),
    ).toBeInTheDocument();
  });

  it("says when the person may not see the product's making", async () => {
    server.use(http.get(MAKING, () => HttpResponse.json({ detail: "Forbidden" }, { status: 403 })));
    renderSection();

    expect(await screen.findByRole("alert")).toHaveTextContent("You do not have access to this.");
  });

  it("shows the server's reason when the making cannot be loaded", async () => {
    server.use(http.get(MAKING, () => HttpResponse.json({ detail: "Making is down" }, { status: 500 })));
    renderSection();

    expect(await screen.findByRole("alert")).toHaveTextContent("Making is down");
  });

  it("adds a material, hands its GSM and thread count to the editor, and clears the form", async () => {
    let sent: MaterialForm | undefined;
    server.use(
      http.get(STOCK, () =>
        HttpResponse.json([
          stockItem(),
          stockItem({ id: 6, name: "Cotton lot", supplierName: null, unit: "KG", onHand: 4 }),
        ]),
      ),
      http.post(`${MAKING}/materials`, async ({ request }) => {
        sent = (await request.json()) as MaterialForm;
        return HttpResponse.json(making({ materials: [material()], costRupees: 900 }));
      }),
    );
    const onFabric = renderSection();

    expect(await screen.findByRole("option", { name: "Cotton lot · 4 kg left" })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText(/^Stock item/), { target: { value: "5" } });
    fireEvent.change(screen.getByLabelText(/^Used/), { target: { value: "2.5" } });
    expect(screen.getByLabelText("Used (m)")).toBeInTheDocument();
    fireEvent.click(within(materialForm()).getByRole("button", { name: "Add" }));

    expect(await screen.findByText("Cut from Silk lot")).toBeInTheDocument();
    expect(sent).toEqual({ stockItemId: 5, quantity: 2.5, note: null });
    expect(onFabric).toHaveBeenCalledWith({ gsm: 120, threadCount: 60 });
    expect(screen.getByLabelText(/^Stock item/)).toHaveValue("");
    expect(screen.getByLabelText(/^Used/)).toHaveValue("");
    expect(screen.getByRole("option", { name: "Silk lot (Surat Mills) · 7.5 m left" })).toBeInTheDocument();
  });

  it("asks for a stock item and a quantity above zero before adding a material", async () => {
    const onFabric = renderSection();
    await screen.findByRole("option", { name: "Silk lot (Surat Mills) · 10 m left" });

    fireEvent.click(within(materialForm()).getByRole("button", { name: "Add" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Pick the stock item and how much was used.");

    fireEvent.change(screen.getByLabelText(/^Stock item/), { target: { value: "5" } });
    fireEvent.change(screen.getByLabelText(/^Used/), { target: { value: "0" } });
    fireEvent.click(within(materialForm()).getByRole("button", { name: "Add" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Pick the stock item and how much was used.");
    expect(onFabric).not.toHaveBeenCalled();
  });

  it("shows the server's reason when a material cannot be added", async () => {
    server.use(
      http.post(`${MAKING}/materials`, () =>
        HttpResponse.json({ detail: "Not enough left in stock" }, { status: 400 }),
      ),
    );
    const onFabric = renderSection();
    await screen.findByRole("option", { name: "Silk lot (Surat Mills) · 10 m left" });

    fireEvent.change(screen.getByLabelText(/^Stock item/), { target: { value: "5" } });
    fireEvent.change(screen.getByLabelText(/^Used/), { target: { value: "20" } });
    fireEvent.click(within(materialForm()).getByRole("button", { name: "Add" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Not enough left in stock");
    expect(onFabric).not.toHaveBeenCalled();
  });

  it("removes a material from the product", async () => {
    server.use(
      http.get(MAKING, () => HttpResponse.json(making({ materials: [material()], work: [work()], costRupees: 1200 }))),
      http.delete(`${MAKING}/materials/1`, () => HttpResponse.json(making({ work: [work()], costRupees: 300 }))),
    );
    renderSection();

    const row = (await screen.findByText("Cut from Silk lot")).closest("li")!;
    fireEvent.click(within(row).getByRole("button", { name: "Remove" }));
    await waitFor(() => expect(screen.queryByText("Cut from Silk lot")).not.toBeInTheDocument());
    expect(screen.getByText("Ravi")).toBeInTheDocument();
  });

  it("shows the server's reason when a material cannot be removed", async () => {
    server.use(
      http.get(MAKING, () => HttpResponse.json(making({ materials: [material()] }))),
      http.delete(`${MAKING}/materials/1`, () =>
        HttpResponse.json({ detail: "Already used in an order" }, { status: 400 }),
      ),
    );
    renderSection();

    const row = (await screen.findByText("Cut from Silk lot")).closest("li")!;
    fireEvent.click(within(row).getByRole("button", { name: "Remove" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Already used in an order");
    expect(screen.getByText("Cut from Silk lot")).toBeInTheDocument();
  });

  it("adds work for a staff member matched by name, with the cost and date typed", async () => {
    let sent: WorkForm | undefined;
    server.use(
      http.post(`${MAKING}/work`, async ({ request }) => {
        sent = (await request.json()) as WorkForm;
        return HttpResponse.json(
          making({ work: [work({ id: 4, costRupees: 1250, doneOn: "2026-11-02" })], costRupees: 1250 }),
        );
      }),
    );
    renderSection();
    await screen.findByRole("option", { name: "Silk lot (Surat Mills) · 10 m left" });

    fireEvent.change(screen.getByLabelText("Who"), { target: { value: "  ravi " } });
    fireEvent.change(screen.getByLabelText("Cost (₹)"), { target: { value: "1,250" } });
    fireEvent.change(screen.getByLabelText("Done on"), { target: { value: "2026-11-02" } });
    fireEvent.click(within(workForm()).getByRole("button", { name: "Add" }));

    expect(await screen.findByText("₹ 1,250 · 2026-11-02")).toBeInTheDocument();
    expect(sent).toEqual({
      staffId: 3,
      workerName: "Ravi",
      workType: "Stitching",
      costRupees: 1250,
      doneOn: "2026-11-02",
      note: null,
    });
    expect(screen.getByLabelText("Who")).toHaveValue("");
    expect(screen.getByLabelText("Cost (₹)")).toHaveValue("");
    expect(screen.getByLabelText("Work")).toHaveValue("Stitching");
  });

  it("records an outside tailor with a typed work type, no cost and no date", async () => {
    let sent: WorkForm | undefined;
    server.use(
      http.post(`${MAKING}/work`, async ({ request }) => {
        sent = (await request.json()) as WorkForm;
        return HttpResponse.json(
          making({
            work: [
              work({
                staffId: null,
                workerName: "Outside tailor Meena",
                workType: "Embroidery",
                costRupees: 0,
                doneOn: null,
              }),
            ],
          }),
        );
      }),
    );
    renderSection();
    await screen.findByRole("option", { name: "Silk lot (Surat Mills) · 10 m left" });

    fireEvent.change(screen.getByLabelText("Who"), { target: { value: "Outside tailor Meena" } });
    fireEvent.change(screen.getByLabelText("Work"), { target: { value: "Embroidery" } });
    fireEvent.change(screen.getByLabelText("Done on"), { target: { value: "" } });
    fireEvent.click(within(workForm()).getByRole("button", { name: "Add" }));

    expect(await screen.findByText("Outside tailor Meena")).toBeInTheDocument();
    expect(sent).toEqual({
      staffId: null,
      workerName: "Outside tailor Meena",
      workType: "Embroidery",
      costRupees: 0,
      doneOn: null,
      note: null,
    });
  });

  it("asks for who did the work and a cost that is not negative", async () => {
    const seen = vi.fn();
    server.use(
      http.post(`${MAKING}/work`, () => {
        seen();
        return HttpResponse.json(making());
      }),
    );
    renderSection();
    await screen.findByRole("option", { name: "Silk lot (Surat Mills) · 10 m left" });

    fireEvent.click(within(workForm()).getByRole("button", { name: "Add" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Enter who did the work, what they did and what it cost.",
    );

    fireEvent.change(screen.getByLabelText("Who"), { target: { value: "Ravi" } });
    fireEvent.change(screen.getByLabelText("Cost (₹)"), { target: { value: "-5" } });
    fireEvent.click(within(workForm()).getByRole("button", { name: "Add" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Enter who did the work, what they did and what it cost.",
    );
    expect(seen).not.toHaveBeenCalled();
  });

  it("shows the server's reason when work cannot be added", async () => {
    server.use(http.post(`${MAKING}/work`, () => HttpResponse.json({ detail: "Worker is inactive" }, { status: 400 })));
    renderSection();
    await screen.findByRole("option", { name: "Silk lot (Surat Mills) · 10 m left" });

    fireEvent.change(screen.getByLabelText("Who"), { target: { value: "Ravi" } });
    fireEvent.click(within(workForm()).getByRole("button", { name: "Add" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Worker is inactive");
    expect(screen.getByLabelText("Who")).toHaveValue("Ravi");
  });

  it("removes a line of work from the product", async () => {
    server.use(
      http.get(MAKING, () => HttpResponse.json(making({ work: [work()] }))),
      http.delete(`${MAKING}/work/1`, () => HttpResponse.json(making())),
    );
    renderSection();

    const row = (await screen.findByText("Ravi")).closest("li")!;
    fireEvent.click(within(row).getByRole("button", { name: "Remove" }));
    await waitFor(() => expect(screen.queryByText("Ravi")).not.toBeInTheDocument());
  });
});
