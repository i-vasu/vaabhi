import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { AdminMedia } from "@vaabhi/api-client";
import { API, server } from "@/test/server";
import { ProductPhotos } from "./product-photos";

// A plain function rather than vi.fn(): Vitest reports a rejected vi.fn() promise as a test failure even when
// the component catches it, and the failure paths here are exactly that.
type Upload = (...args: unknown[]) => Promise<unknown>;
let uploadImpl: Upload = async () => [];
const uploadCalls: unknown[][] = [];
vi.mock("@/lib/photo-upload", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/photo-upload")>()),
  uploadPhoto: (...args: unknown[]) => {
    uploadCalls.push(args);
    return uploadImpl(...args);
  },
}));

const photo = (id: number, overrides: Partial<AdminMedia> = {}): AdminMedia => ({
  id,
  kind: "IMAGE",
  key: `products/7/p${id}`,
  alt: `Photo ${id}`,
  colourName: null,
  width: 1200,
  height: 1600,
  status: "READY",
  ...overrides,
});

const MEDIA = `${API}/api/v1/admin/products/7/media`;

function setup(media: AdminMedia[], colours = ["Ruby"]) {
  const onChange = vi.fn();
  const view = render(
    <ProductPhotos productId={7} productName="Silk saree" colours={colours} media={media} onChange={onChange} />,
  );
  return { onChange, view };
}

describe("ProductPhotos", () => {
  beforeEach(() => {
    uploadCalls.length = 0;
    uploadImpl = async () => [];
  });

  it("asks to save a new product first", () => {
    render(<ProductPhotos productName="" colours={[]} media={[]} onChange={vi.fn()} />);
    expect(screen.getByText("Save the product first, then add photos.")).toBeInTheDocument();
  });

  it("uploads chosen and dropped images with a default description and colour", async () => {
    uploadImpl = async () => [photo(1)];
    const { onChange } = setup([]);
    const file = new File(["x"], "front.jpg", { type: "image/jpeg" });

    fireEvent.change(screen.getByLabelText("Add photos"), { target: { files: [file] } });
    await waitFor(() => expect(onChange).toHaveBeenCalledWith([photo(1)]));
    expect(uploadCalls).toEqual([[7, file, { alt: "Silk saree, Ruby", colourName: "Ruby" }]]);

    const dropZone = screen.getByText("Drop photos here or choose files").closest("label")!;
    fireEvent.dragOver(dropZone);
    fireEvent.dragLeave(dropZone);
    fireEvent.drop(dropZone, {
      dataTransfer: { files: [new File(["y"], "notes.txt", { type: "text/plain" }), file] },
    });
    await waitFor(() => expect(uploadCalls).toHaveLength(2));
  });

  it("shows failed and oversized uploads", async () => {
    // The real upload, which fails at the resize step: jsdom has no createImageBitmap.
    const actual = await vi.importActual<typeof import("@/lib/photo-upload")>("@/lib/photo-upload");
    uploadImpl = actual.uploadPhoto as Upload;
    setup([], ["Ruby", "Emerald"]);
    const big = new File(["x"], "huge.jpg", { type: "image/jpeg" });
    Object.defineProperty(big, "size", { value: 31 * 1024 * 1024 });

    fireEvent.change(screen.getByLabelText("Add photos"), {
      target: { files: [big, new File(["x"], "a.jpg", { type: "image/jpeg" })] },
    });

    const list = await screen.findByRole("list", { name: "Uploads" });
    await waitFor(() =>
      expect(within(list).getByText(/a.jpg: failed. createImageBitmap is not defined/)).toBeInTheDocument(),
    );
    expect(within(list).getByText(/huge.jpg: failed. Larger than 30 MB/)).toBeInTheDocument();
    expect(uploadCalls).toEqual([[7, expect.anything(), { alt: "Silk saree", colourName: null }]]);
  });

  it("explains iPhone HEIC photos the browser cannot open", async () => {
    uploadImpl = async () => {
      throw new Error("The source image could not be decoded.");
    };
    setup([]);
    const dropZone = screen.getByText("Drop photos here or choose files").closest("label")!;
    fireEvent.drop(dropZone, {
      dataTransfer: {
        files: [new File(["x"], "IMG_0001.HEIC", { type: "" }), new File(["x"], "b.heic", { type: "image/heic" })],
      },
    });
    // Windows often gives .HEIC files no type; both are tried and explained.
    const list = await screen.findByRole("list", { name: "Uploads" });
    await waitFor(() =>
      expect(within(list).getByText(/b.heic: failed. This is an iPhone HEIC photo/)).toBeInTheDocument(),
    );
    expect(within(list).getByText(/IMG_0001.HEIC: failed. This is an iPhone HEIC photo/)).toBeInTheDocument();
    expect(uploadCalls).toHaveLength(2);

    fireEvent.change(screen.getByLabelText("Add photos"), {
      target: { files: [new File(["x"], "IMG_0002.HEIC", { type: "" })] },
    });
    await waitFor(() =>
      expect(within(list).getByText(/IMG_0002.HEIC: failed. This is an iPhone HEIC photo/)).toBeInTheDocument(),
    );
  });

  it("reorders, edits and deletes photos", async () => {
    const saved: unknown[] = [];
    server.use(
      http.put(MEDIA, async ({ request }) => {
        saved.push(await request.json());
        return HttpResponse.json([photo(2), photo(1)]);
      }),
      http.delete(`${MEDIA}/2`, () => HttpResponse.json([photo(1)])),
    );
    const { onChange } = setup([photo(1), photo(2, { status: "PENDING", colourName: "Old" })]);

    expect(screen.getByAltText("Photo 1")).toHaveAttribute("src", "https://media.vaabhi.com/products/7/p1/400.webp");
    expect(screen.getByText(/Upload did not finish/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Move photo 1 earlier" })).toBeDisabled();

    fireEvent.click(screen.getByRole("button", { name: "Move photo 1 later" }));
    await waitFor(() => expect(saved).toHaveLength(1));
    expect(saved[0]).toEqual([
      { id: 2, alt: "Photo 2", colourName: "Old" },
      { id: 1, alt: "Photo 1", colourName: null },
    ]);
    expect(onChange).toHaveBeenLastCalledWith([photo(2), photo(1)]);

    fireEvent.change(screen.getAllByRole("combobox", { name: "Colour" })[0]!, { target: { value: "Ruby" } });
    await waitFor(() => expect(saved).toHaveLength(2));
    fireEvent.change(screen.getAllByRole("textbox", { name: "Description (alt text)" })[0]!, {
      target: { value: "Front" },
    });
    expect(onChange).toHaveBeenLastCalledWith([expect.objectContaining({ alt: "Front" }), expect.anything()]);
    fireEvent.blur(screen.getAllByRole("textbox", { name: "Description (alt text)" })[0]!);
    await waitFor(() => expect(saved).toHaveLength(3));
    fireEvent.click(screen.getByRole("button", { name: "Move photo 2 earlier" }));
    await waitFor(() => expect(saved).toHaveLength(4));

    fireEvent.click(screen.getByRole("button", { name: "Delete photo 2" }));
    await waitFor(() => expect(onChange).toHaveBeenLastCalledWith([photo(1)]));
  });

  it("reports API errors", async () => {
    server.use(
      http.put(MEDIA, () =>
        HttpResponse.json({ detail: "Send every photo of the product exactly once" }, { status: 400 }),
      ),
      http.delete(`${MEDIA}/1`, () => HttpResponse.error()),
    );
    setup([photo(1), photo(2)]);

    fireEvent.click(screen.getByRole("button", { name: "Move photo 1 later" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Send every photo of the product exactly once");
    fireEvent.click(screen.getByRole("button", { name: "Delete photo 1" }));
    await waitFor(() => expect(screen.getByRole("alert")).not.toHaveTextContent("exactly once"));
  });
});
