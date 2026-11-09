import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { WorkPiece, WorkshopStaff } from "@vaabhi/api-client";
import { mediaUrl } from "@/lib/photo-upload";
import { PieceCard } from "./piece-card";

const piece = (overrides: Partial<WorkPiece> = {}): WorkPiece => ({
  pieceId: 7,
  productId: 70,
  productName: "Kalamkari kurti – peacock",
  productSlug: "kalamkari-kurti-peacock",
  productStatus: "DRAFT",
  stockItemId: 12,
  stockItemName: "Kalamkari cotton",
  quantity: 3,
  unit: "M",
  photoKey: "stock/fabric",
  stage: { id: 2, name: "Stitching", kind: "WORK" },
  stageSince: "2026-11-09T10:00:00Z",
  assignedTo: null,
  assignedPhone: null,
  note: null,
  photos: [],
  fabric: { material: null, colour: null, widthIn: null, gsm: null, threadCount: null },
  dimensions: [],
  ...overrides,
});

const staff: WorkshopStaff[] = [
  { id: 1, name: "Asha Rao", role: "OWNER", stageIds: [], workingOn: 0 },
  { id: 2, name: "Sita Cutter", role: "STAFF", stageIds: [1], workingOn: 3 },
];

type Props = Parameters<typeof PieceCard>[0];

function setup(overrides: Partial<Props> = {}) {
  const handlers = {
    onSelect: vi.fn(),
    onOpen: vi.fn(),
    onAssign: vi.fn(),
    onNext: vi.fn(),
  };
  const props: Props = {
    piece: piece(),
    staff,
    selected: false,
    busy: false,
    nextStageName: "Finishing",
    ...handlers,
    ...overrides,
  };
  render(<PieceCard {...props} />);
  return handlers;
}

describe("PieceCard", () => {
  it("shows the product, the fabric and how much is used", () => {
    setup();
    expect(screen.getByText("Stock")).toBeInTheDocument();
    expect(screen.getByText("Kalamkari kurti – peacock")).toBeInTheDocument();
    expect(screen.getByText("Kalamkari cotton · 3 metres")).toBeInTheDocument();
  });

  it("shows the fabric photo and the count of design photos", () => {
    setup({
      piece: piece({
        photos: [
          { id: 1, key: "workshop/pieces/a" },
          { id: 2, key: "workshop/pieces/b" },
        ],
      }),
    });
    expect(screen.getByAltText("Fabric for Kalamkari kurti – peacock")).toHaveAttribute("src", mediaUrl("stock/fabric"));
    expect(screen.getByText(/2 design photos/)).toBeInTheDocument();
  });

  it("uses the singular for one design photo and leaves out the fabric photo when there is none", () => {
    setup({ piece: piece({ photoKey: null, photos: [{ id: 1, key: "workshop/pieces/a" }] }) });
    expect(screen.getByText(/1 design photo$/)).toBeInTheDocument();
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
  });

  it("reports the selection through the checkbox", () => {
    const { onSelect } = setup({ selected: true });
    const box = screen.getByRole("checkbox", { name: "Select Kalamkari kurti – peacock" });
    expect(box).toBeChecked();
    fireEvent.click(box);
    expect(onSelect).toHaveBeenCalledTimes(1);
  });

  it("opens the piece when its name is pressed", () => {
    const { onOpen } = setup();
    fireEvent.click(screen.getByRole("button", { name: /Kalamkari kurti – peacock/ }));
    expect(onOpen).toHaveBeenCalledTimes(1);
  });

  it("lists the staff and gives the piece to the one picked", () => {
    const { onAssign } = setup();
    const select = screen.getByRole("combobox", { name: "Given to (Kalamkari kurti – peacock)" });
    expect(select).toHaveValue("");
    expect(screen.getByRole("option", { name: "Nobody yet" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Sita Cutter (3)" })).toBeInTheDocument();
    fireEvent.change(select, { target: { value: "2" } });
    expect(onAssign).toHaveBeenLastCalledWith(2);
  });

  it("takes the piece back when nobody is picked", () => {
    const { onAssign } = setup({ piece: piece({ assignedTo: { id: 2, name: "Sita Cutter" } }) });
    const select = screen.getByRole("combobox", { name: "Given to (Kalamkari kurti – peacock)" });
    expect(select).toHaveValue("2");
    fireEvent.change(select, { target: { value: "" } });
    expect(onAssign).toHaveBeenLastCalledWith(null);
  });

  it("disables the staff picker while busy", () => {
    setup({ busy: true });
    expect(screen.getByRole("combobox", { name: /Given to/ })).toBeDisabled();
  });

  it("hides the staff picker when the staff list has not loaded", () => {
    setup({ staff: undefined });
    expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
  });

  it("moves the piece on to the next stage", () => {
    const { onNext } = setup();
    fireEvent.click(screen.getByRole("button", { name: "Finishing →" }));
    expect(onNext).toHaveBeenCalledTimes(1);
  });

  it("disables the move button while busy", () => {
    setup({ busy: true });
    expect(screen.getByRole("button", { name: "Finishing →" })).toBeDisabled();
  });

  it("shows no move button on the last work stage", () => {
    setup({ nextStageName: undefined });
    expect(screen.queryByRole("button", { name: /→/ })).not.toBeInTheDocument();
  });

  it("links a ready piece to the product to add photos and publish", () => {
    setup({ piece: piece({ stage: { id: 3, name: "Ready", kind: "READY" } }) });
    expect(screen.getByRole("link", { name: "Add photos & publish" })).toHaveAttribute(
      "href",
      "/products/edit/?id=70",
    );
    expect(screen.queryByRole("button", { name: /→/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
  });
});
