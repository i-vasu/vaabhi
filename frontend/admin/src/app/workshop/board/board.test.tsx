import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { BoardColumn, WorkPiece } from "@vaabhi/api-client";
import { API, server } from "@/test/server";
import { stages } from "@/test/settings-fixtures";
import { serveTailoring, workItem } from "@/test/tailoring-fixtures";
import WorkBoardPage from "./page";

let search = new URLSearchParams();
vi.mock("next/navigation", () => ({ useSearchParams: () => search }));

beforeEach(() => {
  serveTailoring();
  search = new URLSearchParams();
});

const base = `${API}/api/v1/admin`;

const kurti: WorkPiece = {
  pieceId: 7,
  productId: 70,
  productName: "Kalamkari kurti – peacock",
  productSlug: "kalamkari-kurti-peacock",
  productStatus: "DRAFT",
  stockItemId: 12,
  stockItemName: "Kalamkari cotton",
  quantity: 3,
  unit: "M",
  photoKey: null,
  stage: stages[0]!,
  stageSince: "2026-11-09T10:00:00Z",
  assignedTo: null,
  assignedPhone: null,
  note: null,
  photos: [],
  fabric: { material: "Kalamkari cotton", colour: "maroon", widthIn: 44, gsm: 120, threadCount: 60 },
  dimensions: [{ label: "Chest", value: 38, unit: "IN" }],
};

const dupatta: WorkPiece = {
  ...kurti,
  pieceId: 8,
  productId: 71,
  productName: "Kalamkari dupatta",
  productSlug: "kalamkari-dupatta",
  stage: stages[3]!,
  dimensions: [],
};

const columns: BoardColumn[] = [
  { stage: stages[0]!, removed: false, items: [workItem], pieces: [kurti] },
  { stage: stages[1]!, removed: false, items: [], pieces: [] },
  { stage: stages[2]!, removed: false, items: [], pieces: [] },
  { stage: stages[3]!, removed: false, items: [], pieces: [dupatta] },
  { stage: { id: 9, name: "Old fabric", kind: "WORK" }, removed: true, items: [], pieces: [] },
];

/** Serves the board above; a move answers with the same columns, and every write is recorded. */
function serveBoard(cols: BoardColumn[] = columns) {
  const calls = { move: [] as unknown[], assign: [] as unknown[], note: [] as unknown[] };
  server.use(
    http.get(`${base}/workshop/board`, () => HttpResponse.json(cols)),
    http.post(`${base}/workshop/items/move`, async ({ request }) => {
      calls.move.push(await request.json());
      return HttpResponse.json(cols);
    }),
    http.put(`${base}/workshop/pieces/:id/assignee`, async ({ request }) => {
      calls.assign.push(await request.json());
      return HttpResponse.json({ ...kurti, assignedTo: { id: 2, name: "Sita Cutter" } });
    }),
    http.put(`${base}/workshop/pieces/:id/note`, async ({ request }) => {
      calls.note.push(await request.json());
      return HttpResponse.json({ ...kurti, note: "Gold piping" });
    }),
  );
  return calls;
}

const pieceButton = () => screen.findByRole("button", { name: /Kalamkari kurti/ });

describe("work board", () => {
  it("counts work items and stock pieces per stage, and lists removed stages", async () => {
    serveBoard();
    render(<WorkBoardPage />);
    expect(await pieceButton()).toHaveTextContent("Kalamkari cotton · 3 metres");
    expect(screen.getByRole("radio", { name: "Cutting · 2" })).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "Ready for pickup · 1" })).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "Old fabric (removed) · 0" })).toBeInTheDocument();
  });

  it("switches stage and shows an empty state, then a search with no match", async () => {
    serveBoard();
    render(<WorkBoardPage />);
    await pieceButton();
    fireEvent.click(screen.getByRole("radio", { name: "Stitching · 0" }));
    expect(screen.getByText("Nothing in Stitching")).toBeInTheDocument();
    expect(screen.getByText("Pick another stage above.")).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Search work"), { target: { value: "kurti" } });
    fireEvent.submit(screen.getByRole("search"));
    expect(await screen.findByText("No work matches “kurti”.")).toBeInTheDocument();
  });

  it("lists finished pieces with a link to publish them", async () => {
    serveBoard();
    render(<WorkBoardPage />);
    await pieceButton();
    fireEvent.click(screen.getByRole("radio", { name: "Ready for pickup · 1" }));
    expect(await screen.findByRole("link", { name: "Add photos & publish" })).toHaveAttribute(
      "href",
      "/products/edit/?id=71",
    );
  });

  it("gives a piece to someone and offers to share it with them", async () => {
    const calls = serveBoard();
    render(<WorkBoardPage />);
    await pieceButton();
    fireEvent.change(screen.getByLabelText("Given to (Kalamkari kurti – peacock)"), { target: { value: "2" } });
    await waitFor(() => expect(calls.assign).toEqual([{ staffId: 2 }]));
    expect(await screen.findByText("Kalamkari kurti – peacock is with Sita Cutter.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Share with Sita Cutter on WhatsApp" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Dismiss" }));
    expect(screen.queryByText("Kalamkari kurti – peacock is with Sita Cutter.")).not.toBeInTheDocument();
  });

  it("asks who did the work, and what it cost, before moving a piece on", async () => {
    const calls = serveBoard();
    render(<WorkBoardPage />);
    const card = (await pieceButton()).closest("li")!;
    fireEvent.click(within(card).getByRole("button", { name: "Stitching →" }));

    const dialog = screen.getByRole("dialog", { name: "Who did cutting?" });
    const save = within(dialog).getByRole("button", { name: "Save and move" });
    expect(save).toBeDisabled();
    fireEvent.change(within(dialog).getByLabelText("Done by"), { target: { value: "Sita Cutter" } });
    fireEvent.change(within(dialog).getByLabelText("Cost (₹)"), { target: { value: "150" } });
    fireEvent.click(save);

    await waitFor(() => expect(calls.move).toHaveLength(1));
    expect(calls.move[0]).toEqual({
      stageId: 2,
      itemIds: [],
      pieceIds: [7],
      work: { staffId: null, workerName: "Sita Cutter", costRupees: 150 },
    });
  });

  it("moves a piece on without logging work when the prompt is skipped", async () => {
    const calls = serveBoard();
    render(<WorkBoardPage />);
    const card = (await pieceButton()).closest("li")!;
    fireEvent.click(within(card).getByRole("button", { name: "Stitching →" }));
    fireEvent.click(screen.getByRole("button", { name: "Skip" }));

    await waitFor(() => expect(calls.move).toHaveLength(1));
    expect(calls.move[0]).toEqual({ stageId: 2, itemIds: [], pieceIds: [7], work: null });
  });

  it("moves selected pieces in bulk", async () => {
    const calls = serveBoard();
    render(<WorkBoardPage />);
    fireEvent.click(await screen.findByLabelText("Select Kalamkari kurti – peacock"));
    expect(screen.getByText("1 selected")).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Move to"), { target: { value: String(stages[1]!.id) } });
    fireEvent.click(screen.getByRole("button", { name: "Move" }));

    await waitFor(() => expect(calls.move).toHaveLength(1));
    expect(calls.move[0]).toEqual({ stageId: 2, itemIds: [], pieceIds: [7], work: null });
  });

  it("opens a piece, with its fabric and product, and saves a note for the tailor", async () => {
    const calls = serveBoard();
    render(<WorkBoardPage />);
    fireEvent.click(await pieceButton());

    const dialog = screen.getByRole("dialog", { name: "Kalamkari kurti – peacock" });
    expect(within(dialog).getByRole("link", { name: "Kalamkari cotton" })).toHaveAttribute(
      "href",
      "/stock/view/?id=12",
    );
    expect(within(dialog).getByRole("link", { name: "Open product" })).toHaveAttribute(
      "href",
      "/products/edit/?id=70",
    );
    fireEvent.change(within(dialog).getByLabelText("Note for the tailor"), { target: { value: "Gold piping" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Save note" }));

    await waitFor(() => expect(calls.note).toEqual([{ note: "Gold piping" }]));
  });

  it("shows the reason when a move is refused", async () => {
    serveBoard();
    server.use(
      http.post(`${base}/workshop/items/move`, () =>
        HttpResponse.json({ detail: "That piece is no longer on the board" }, { status: 409 }),
      ),
    );
    render(<WorkBoardPage />);
    fireEvent.click(await screen.findByLabelText("Select Kalamkari kurti – peacock"));
    fireEvent.click(screen.getByRole("button", { name: "Move" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("That piece is no longer on the board");
  });

  it("shows the reason when the board cannot be loaded", async () => {
    server.use(
      http.get(`${base}/workshop/board`, () =>
        HttpResponse.json({ detail: "The board is unavailable" }, { status: 500 }),
      ),
    );
    render(<WorkBoardPage />);
    expect(await screen.findByRole("alert")).toHaveTextContent("The board is unavailable");
    expect(screen.getByRole("heading", { name: "Work board" })).toBeInTheDocument();
  });
});
