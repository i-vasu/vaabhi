import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ApiError } from "@vaabhi/api-client";
import { mediaUrl } from "@/lib/photo-upload";
import { PhotoStrip, type StripPhoto } from "./photo-strip";

const photos: StripPhoto[] = [
  { id: 1, key: "workshop/pieces/a" },
  { id: 2, key: "workshop/pieces/b" },
];

const many = (count: number): StripPhoto[] => Array.from({ length: count }, (_, i) => ({ id: i + 1, key: `k/${i}` }));

type Props = Parameters<typeof PhotoStrip>[0];

// Rejections are thrown from plain functions: Vitest reports a rejected vi.fn() promise as a failure even when the
// component catches it (see product-photos.test.tsx).
function setup(overrides: Partial<Props> = {}) {
  const onAdd = vi.fn(async () => {});
  const onRemove = vi.fn(async () => {});
  const props: Props = { label: "Design photo", photos, onAdd, onRemove, ...overrides };
  render(<PhotoStrip {...props} />);
  return { onAdd, onRemove };
}

const pick = (file: File) => ({ target: { files: [file] } });

describe("PhotoStrip", () => {
  it("shows each photo, numbered from the label", () => {
    setup();
    expect(screen.getByAltText("Design photo 1")).toHaveAttribute("src", mediaUrl("workshop/pieces/a"));
    expect(screen.getByAltText("Design photo 2")).toHaveAttribute("src", mediaUrl("workshop/pieces/b"));
    expect(screen.getByRole("button", { name: "Open design photo 1" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Remove design photo 2" })).toBeInTheDocument();
  });

  it("shows an empty strip with just the add tile when there are no photos", () => {
    setup({ photos: [] });
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
    expect(screen.getByLabelText("Add design photo")).toBeInTheDocument();
  });

  it("offers the add tile until the limit of 6 photos, then hides it", () => {
    const { unmount } = render(<PhotoStrip label="Design photo" photos={many(5)} onAdd={vi.fn()} onRemove={vi.fn()} />);
    expect(screen.getByLabelText("Add design photo")).toBeInTheDocument();
    unmount();

    render(<PhotoStrip label="Design photo" photos={many(6)} onAdd={vi.fn()} onRemove={vi.fn()} />);
    expect(screen.queryByLabelText("Add design photo")).not.toBeInTheDocument();
  });

  it("takes a custom limit", () => {
    setup({ photos: many(2), max: 2 });
    expect(screen.queryByLabelText("Add design photo")).not.toBeInTheDocument();
  });

  it("hands the picked file to onAdd", async () => {
    const { onAdd } = setup();
    const file = new File(["x"], "design.jpg", { type: "image/jpeg" });
    fireEvent.change(screen.getByLabelText("Add design photo"), pick(file));
    await waitFor(() => expect(onAdd).toHaveBeenCalledWith(file));
  });

  it("does nothing when the picker closes without a file", () => {
    const { onAdd } = setup();
    fireEvent.change(screen.getByLabelText("Add design photo"), { target: { files: [] } });
    expect(onAdd).not.toHaveBeenCalled();
  });

  it("shows Uploading and holds the other actions until the add finishes", async () => {
    let finish!: () => void;
    const onAdd = vi.fn(() => new Promise<void>((resolve) => (finish = resolve)));
    setup({ onAdd });
    fireEvent.change(screen.getByLabelText("Add design photo"), pick(new File(["x"], "a.jpg")));

    expect(screen.getByText("Uploading…")).toBeInTheDocument();
    expect(screen.getByLabelText("Add design photo")).toBeDisabled();
    expect(screen.getByRole("button", { name: "Remove design photo 1" })).toBeDisabled();

    await act(async () => finish());
    await waitFor(() => expect(screen.queryByText("Uploading…")).not.toBeInTheDocument());
    expect(screen.getByRole("button", { name: "Remove design photo 1" })).toBeEnabled();
  });

  it("shows the server's message when adding fails", async () => {
    setup({
      onAdd: async () => {
        throw new ApiError(413, "Upload failed", [], "The photo is too large");
      },
    });
    fireEvent.change(screen.getByLabelText("Add design photo"), pick(new File(["x"], "a.jpg")));
    expect(await screen.findByRole("alert")).toHaveTextContent("The photo is too large");
  });

  it("shows a connection message when adding fails without a server answer", async () => {
    setup({
      onAdd: async () => {
        throw new TypeError("Failed to fetch");
      },
    });
    fireEvent.change(screen.getByLabelText("Add design photo"), pick(new File(["x"], "a.jpg")));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Could not reach the server. Check your connection and try again.",
    );
  });

  it("removes the photo that was pressed", async () => {
    const { onRemove } = setup();
    fireEvent.click(screen.getByRole("button", { name: "Remove design photo 2" }));
    await waitFor(() => expect(onRemove).toHaveBeenCalledWith(2));
  });

  it("shows the server's message when removing fails", async () => {
    setup({
      onRemove: async () => {
        throw new ApiError(404, "Not found", [], "That photo was already removed");
      },
    });
    fireEvent.click(screen.getByRole("button", { name: "Remove design photo 1" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("That photo was already removed");
  });

  it("opens a photo large on tap and closes it with Escape", () => {
    setup();
    fireEvent.click(screen.getByRole("button", { name: "Open design photo 2" }));

    const dialog = screen.getByRole("dialog", { name: "Design photo" });
    expect(dialog).toBeInTheDocument();
    expect(screen.getByAltText("Design photo")).toHaveAttribute("src", mediaUrl("workshop/pieces/b"));

    fireEvent.keyDown(document, { key: "Escape" });
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("closes the enlarged photo from its close button", () => {
    setup();
    fireEvent.click(screen.getByRole("button", { name: "Open design photo 1" }));
    fireEvent.click(screen.getAllByRole("button", { name: "Close" })[0]!);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});
