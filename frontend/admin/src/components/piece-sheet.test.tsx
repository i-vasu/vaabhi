import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi, type MockInstance } from "vitest";
import type { WorkPiece } from "@vaabhi/api-client";
import { API, server } from "@/test/server";
import { PieceSheet, ShareButton, StageWorkPrompt } from "./piece-sheet";

// A plain function rather than vi.fn(): Vitest reports a rejected vi.fn() promise as a test failure even when
// the component catches it (see product-photos.test.tsx).
type Upload = (...args: unknown[]) => Promise<unknown>;
let uploadImpl: Upload = async () => "";
const uploadCalls: unknown[][] = [];
vi.mock("@/lib/tailoring", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/tailoring")>()),
  uploadKeyedPhoto: (...args: unknown[]) => {
    uploadCalls.push(args);
    return uploadImpl(...args);
  },
}));

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
  assignedTo: { id: 4, name: "Lakshmi" },
  assignedPhone: "98765 43210",
  note: "Peacock panel on the front yoke",
  photos: [
    { id: 1, key: "workshop/pieces/a" },
    { id: 2, key: "workshop/pieces/b" },
  ],
  fabric: { material: "Kalamkari cotton", colour: "maroon", widthIn: 44, gsm: 120, threadCount: 60 },
  dimensions: [
    { label: "Length", value: 44, unit: "IN" },
    { label: "Chest", value: 38, unit: "IN" },
  ],
  ...overrides,
});

const PIECE = `${API}/api/v1/admin/workshop/pieces/7`;

/** The value shown under a label in the fabric list. */
const factOf = (label: string) => screen.getByText(label, { selector: "dt" }).parentElement!;

let windowOpen: MockInstance<typeof window.open>;

beforeEach(() => {
  uploadCalls.length = 0;
  uploadImpl = async () => "";
  // Photo downloads for sharing fail, so the share falls back to links; the share tests do not depend on them.
  server.use(http.get("https://media.vaabhi.com/*", () => new HttpResponse(null, { status: 404 })));
  windowOpen = vi.spyOn(window, "open").mockReturnValue({} as Window);
});

afterEach(() => {
  windowOpen.mockRestore();
  delete (navigator as unknown as Record<string, unknown>).share;
  delete (navigator as unknown as Record<string, unknown>).canShare;
});

describe("PieceSheet", () => {
  it("shows the piece: stage, who has it, fabric, measurements and the note", () => {
    render(<PieceSheet piece={piece()} onChange={vi.fn()} onClose={vi.fn()} />);

    expect(screen.getByRole("dialog", { name: "Kalamkari kurti – peacock" })).toBeInTheDocument();
    expect(screen.getByText("Stitching · with Lakshmi")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Kalamkari cotton" })).toHaveAttribute("href", "/stock/view/?id=12");
    expect(factOf("Used")).toHaveTextContent("3 metres");
    expect(factOf("Material")).toHaveTextContent("Kalamkari cotton, maroon");
    expect(factOf("Width")).toHaveTextContent("44 in");
    expect(factOf("GSM")).toHaveTextContent("120");
    expect(factOf("Length")).toHaveTextContent("44 in");
    expect(factOf("Chest")).toHaveTextContent("38 in");
    expect(screen.getByLabelText("Note for the tailor")).toHaveValue("Peacock panel on the front yoke");
    expect(screen.getByRole("link", { name: "Open product" })).toHaveAttribute("href", "/products/edit/?id=70");
    expect(screen.getByAltText("Design photo 1")).toBeInTheDocument();
  });

  it("leaves out the parts of the piece that are not known yet", () => {
    render(
      <PieceSheet
        piece={piece({
          assignedTo: null,
          note: null,
          photoKey: null,
          photos: [],
          fabric: { material: null, colour: null, widthIn: null, gsm: null, threadCount: null },
          dimensions: [],
        })}
        onChange={vi.fn()}
        onClose={vi.fn()}
      />,
    );

    expect(screen.getByText("Stitching")).toBeInTheDocument();
    expect(screen.queryByText(/with /)).not.toBeInTheDocument();
    expect(screen.queryByText("Material")).not.toBeInTheDocument();
    expect(screen.queryByText("Width")).not.toBeInTheDocument();
    expect(screen.queryByText("GSM")).not.toBeInTheDocument();
    expect(screen.queryByText("Length")).not.toBeInTheDocument();
    expect(factOf("Used")).toHaveTextContent("3 metres");
    expect(screen.getByLabelText("Note for the tailor")).toHaveValue("");
    expect(screen.queryByRole("button", { name: "Save note" })).not.toBeInTheDocument();
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
  });

  it("saves a changed note and passes the saved piece back", async () => {
    let body: unknown;
    const saved = piece({ note: "Pipe the neck" });
    server.use(
      http.put(`${PIECE}/note`, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(saved);
      }),
    );
    const onChange = vi.fn();
    render(<PieceSheet piece={piece({ note: null })} onChange={onChange} onClose={vi.fn()} />);

    expect(screen.queryByRole("button", { name: "Save note" })).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Note for the tailor"), { target: { value: "Pipe the neck" } });
    fireEvent.click(screen.getByRole("button", { name: "Save note" }));

    await waitFor(() => expect(onChange).toHaveBeenCalledWith(saved));
    expect(body).toEqual({ note: "Pipe the neck" });
  });

  it("clears the note to null when it is emptied", async () => {
    let body: unknown;
    server.use(
      http.put(`${PIECE}/note`, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(piece({ note: null }));
      }),
    );
    render(<PieceSheet piece={piece({ note: "Old note" })} onChange={vi.fn()} onClose={vi.fn()} />);

    fireEvent.change(screen.getByLabelText("Note for the tailor"), { target: { value: "" } });
    fireEvent.click(screen.getByRole("button", { name: "Save note" }));

    await waitFor(() => expect(body).toEqual({ note: null }));
  });

  it("shows the server's message when the note cannot be saved", async () => {
    server.use(
      http.put(`${PIECE}/note`, () => HttpResponse.json({ detail: "The note is too long" }, { status: 400 })),
    );
    render(<PieceSheet piece={piece({ note: null })} onChange={vi.fn()} onClose={vi.fn()} />);

    fireEvent.change(screen.getByLabelText("Note for the tailor"), { target: { value: "Pipe" } });
    fireEvent.click(screen.getByRole("button", { name: "Save note" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("The note is too long");
  });

  it("uploads a design photo and adds it to the piece", async () => {
    const started = vi.fn();
    uploadImpl = async (...args) => {
      await (args[1] as () => Promise<unknown>)();
      return "workshop/pieces/new";
    };
    let body: unknown;
    const withNew = piece({
      photos: [
        { id: 1, key: "workshop/pieces/a" },
        { id: 2, key: "workshop/pieces/b" },
        { id: 3, key: "workshop/pieces/new" },
      ],
    });
    server.use(
      http.post(`${PIECE}/photos/upload`, () => {
        started();
        return HttpResponse.json({ key: "workshop/pieces/new", uploadUrl: "https://upload.example/new" });
      }),
      http.post(`${PIECE}/photos`, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(withNew);
      }),
    );
    const onChange = vi.fn();
    render(<PieceSheet piece={piece()} onChange={onChange} onClose={vi.fn()} />);
    const file = new File(["x"], "design.jpg", { type: "image/jpeg" });

    fireEvent.change(screen.getByLabelText("Add design photo"), { target: { files: [file] } });

    await waitFor(() => expect(onChange).toHaveBeenCalledWith(withNew));
    expect(uploadCalls).toEqual([[file, expect.any(Function)]]);
    expect(started).toHaveBeenCalledTimes(1);
    expect(body).toEqual({ key: "workshop/pieces/new" });
  });

  it("removes a design photo and passes the piece back", async () => {
    const without = piece({ photos: [{ id: 2, key: "workshop/pieces/b" }] });
    server.use(http.delete(`${PIECE}/photos/1`, () => HttpResponse.json(without)));
    const onChange = vi.fn();
    render(<PieceSheet piece={piece()} onChange={onChange} onClose={vi.fn()} />);

    fireEvent.click(screen.getByRole("button", { name: "Remove design photo 1" }));

    await waitFor(() => expect(onChange).toHaveBeenCalledWith(without));
  });

  it("closes with Escape or the close button", () => {
    const onClose = vi.fn();
    render(<PieceSheet piece={piece()} onChange={vi.fn()} onClose={onClose} />);

    fireEvent.keyDown(document, { key: "Escape" });
    expect(onClose).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getAllByRole("button", { name: "Close" })[0]!);
    expect(onClose).toHaveBeenCalledTimes(2);
  });
});

describe("ShareButton", () => {
  it("opens WhatsApp to the tailor's number when the phone cannot share files", async () => {
    render(<ShareButton piece={piece()} />);

    fireEvent.click(screen.getByRole("button", { name: "Share on WhatsApp" }));

    await waitFor(() => expect(windowOpen).toHaveBeenCalledTimes(1));
    expect(windowOpen.mock.calls[0]![0]).toMatch(/^https:\/\/wa\.me\/919876543210\?text=/);
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("uses the phone's share sheet when it can share", async () => {
    const share = vi.fn<(data: ShareData) => Promise<void>>(() => Promise.resolve());
    Object.defineProperty(navigator, "share", { value: share, configurable: true });
    Object.defineProperty(navigator, "canShare", { value: () => true, configurable: true });
    render(<ShareButton piece={piece()} label="Share" />);

    fireEvent.click(screen.getByRole("button", { name: "Share" }));

    await waitFor(() => expect(share).toHaveBeenCalledTimes(1));
    expect(share.mock.calls[0]![0].text).toContain("Kalamkari kurti – peacock");
    expect(windowOpen).not.toHaveBeenCalled();
  });

  it("says so when the browser blocks the WhatsApp window", async () => {
    windowOpen.mockReturnValueOnce(null);
    render(<ShareButton piece={piece()} />);

    fireEvent.click(screen.getByRole("button", { name: "Share on WhatsApp" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "The browser blocked WhatsApp from opening. Allow pop-ups for this site and try again.",
    );
  });
});

describe("StageWorkPrompt", () => {
  it("asks who did the stage, prefilled with the piece's assignee", () => {
    render(
      <StageWorkPrompt piece={piece()} busy={false} onSave={vi.fn()} onSkip={vi.fn()} onClose={vi.fn()} />,
    );

    expect(screen.getByRole("dialog", { name: "Who did stitching?" })).toBeInTheDocument();
    expect(
      screen.getByText("Logged on Kalamkari kurti – peacock as part of its making cost."),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("Done by")).toHaveValue("Lakshmi");
  });

  it("saves the cost against the assignee when the name is unchanged", () => {
    const onSave = vi.fn();
    render(<StageWorkPrompt piece={piece()} busy={false} onSave={onSave} onSkip={vi.fn()} onClose={vi.fn()} />);

    fireEvent.change(screen.getByLabelText("Cost (₹)"), { target: { value: "250" } });
    fireEvent.click(screen.getByRole("button", { name: "Save and move" }));

    expect(onSave).toHaveBeenCalledWith({ staffId: 4, workerName: "Lakshmi", costRupees: 250 });
  });

  it("saves a different worker without a staff link, with the name trimmed and the cost read with commas", () => {
    const onSave = vi.fn();
    render(<StageWorkPrompt piece={piece()} busy={false} onSave={onSave} onSkip={vi.fn()} onClose={vi.fn()} />);

    fireEvent.change(screen.getByLabelText("Done by"), { target: { value: "  Ravi Tailor " } });
    fireEvent.change(screen.getByLabelText("Cost (₹)"), { target: { value: "1,250" } });
    fireEvent.click(screen.getByRole("button", { name: "Save and move" }));

    expect(onSave).toHaveBeenCalledWith({ staffId: null, workerName: "Ravi Tailor", costRupees: 1250 });
  });

  it("saves with no staff link when the piece has no assignee", () => {
    const onSave = vi.fn();
    render(
      <StageWorkPrompt
        piece={piece({ assignedTo: null })}
        busy={false}
        onSave={onSave}
        onSkip={vi.fn()}
        onClose={vi.fn()}
      />,
    );

    expect(screen.getByLabelText("Done by")).toHaveValue("");
    fireEvent.change(screen.getByLabelText("Done by"), { target: { value: "Ravi" } });
    fireEvent.change(screen.getByLabelText("Cost (₹)"), { target: { value: "100" } });
    fireEvent.click(screen.getByRole("button", { name: "Save and move" }));

    expect(onSave).toHaveBeenCalledWith({ staffId: null, workerName: "Ravi", costRupees: 100 });
  });

  it("holds Save until there is a name and a cost that is a number", () => {
    render(<StageWorkPrompt piece={piece()} busy={false} onSave={vi.fn()} onSkip={vi.fn()} onClose={vi.fn()} />);
    const save = screen.getByRole("button", { name: "Save and move" });

    expect(save).toBeDisabled(); // no cost yet
    fireEvent.change(screen.getByLabelText("Cost (₹)"), { target: { value: "abc" } });
    expect(save).toBeDisabled(); // not a number
    fireEvent.change(screen.getByLabelText("Cost (₹)"), { target: { value: "120" } });
    expect(save).toBeEnabled();
    fireEvent.change(screen.getByLabelText("Done by"), { target: { value: "   " } });
    expect(save).toBeDisabled(); // blank name
  });

  it("disables both buttons while busy and skips when Skip is pressed", () => {
    const onSkip = vi.fn();
    const { rerender } = render(
      <StageWorkPrompt piece={piece()} busy={false} onSave={vi.fn()} onSkip={onSkip} onClose={vi.fn()} />,
    );
    fireEvent.click(screen.getByRole("button", { name: "Skip" }));
    expect(onSkip).toHaveBeenCalledTimes(1);

    rerender(<StageWorkPrompt piece={piece()} busy onSave={vi.fn()} onSkip={onSkip} onClose={vi.fn()} />);
    expect(screen.getByRole("button", { name: "Skip" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Save and move" })).toBeDisabled();
  });
});
