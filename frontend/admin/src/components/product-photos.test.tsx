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
  uploadVideo: (...args: unknown[]) => {
    uploadCalls.push(["video", ...args]);
    return uploadImpl(...args);
  },
}));

const video = (id: number, overrides: Partial<AdminMedia> = {}): AdminMedia => ({
  id,
  kind: "VIDEO",
  key: `products/7/v${id}`,
  alt: "Silk saree, drape",
  colourName: null,
  width: null,
  height: null,
  status: "READY",
  ...overrides,
});

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

  it("keeps photos and a video picked before the first save, to upload with it", () => {
    const onQueue = vi.fn();
    const props = { productName: "", colours: [], media: [], onChange: vi.fn(), onQueue };
    const { rerender } = render(<ProductPhotos {...props} />);
    expect(screen.getByLabelText("Add video")).toBeDisabled();
    expect(screen.getByText("Add a photo first.")).toBeInTheDocument();

    const front = new File(["x"], "front.jpg", { type: "image/jpeg" });
    const huge = new File(["x"], "huge.jpg", { type: "image/jpeg" });
    Object.defineProperty(huge, "size", { value: 31 * 1024 * 1024 });
    fireEvent.change(screen.getByLabelText("Add photos"), { target: { files: [front, huge] } });
    expect(onQueue).toHaveBeenLastCalledWith([expect.objectContaining({ kind: "IMAGE", file: front })]);
    expect(screen.getByText(/huge.jpg: failed. Larger than 30 MB/)).toBeInTheDocument();
    expect(uploadCalls).toHaveLength(0);

    const queuedPhoto = onQueue.mock.lastCall![0][0];
    rerender(<ProductPhotos {...props} queued={[queuedPhoto]} />);
    expect(screen.getByText("Uploads when you save")).toBeInTheDocument();
    expect(screen.getByText(/1 of 12 photos · no video yet · uploads when you save/)).toBeInTheDocument();
    const add = screen.getByLabelText("Add video");
    expect(add).toBeEnabled();

    fireEvent.change(add, { target: { files: [new File(["x"], "clip.avi", { type: "video/x-msvideo" })] } });
    expect(screen.getByText(/clip.avi: failed. Use an MP4, MOV or WebM video/)).toBeInTheDocument();
    const clip = new File(["x"], "clip.mp4", { type: "video/mp4" });
    fireEvent.change(add, { target: { files: [clip] } });
    expect(onQueue).toHaveBeenLastCalledWith([queuedPhoto, expect.objectContaining({ kind: "VIDEO", file: clip })]);

    const queuedVideo = onQueue.mock.lastCall![0][1];
    rerender(<ProductPhotos {...props} queued={[queuedPhoto, queuedVideo]} />);
    expect(screen.queryByLabelText("Add video")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Remove video" }));
    expect(onQueue).toHaveBeenLastCalledWith([queuedPhoto]);
    fireEvent.click(screen.getByRole("button", { name: "Remove front.jpg" }));
    expect(onQueue).toHaveBeenLastCalledWith([queuedVideo]);
  });

  it("uploads a video straight away for a saved product, then shows and deletes it", async () => {
    uploadImpl = async () => [photo(1), video(9)];
    server.use(http.delete(`${MEDIA}/9`, () => HttpResponse.json([photo(1)])));
    const { onChange, view } = setup([photo(1)]);
    const clip = new File(["x"], "clip.mov", { type: "video/quicktime" });
    const big = new File(["x"], "big.mp4", { type: "video/mp4" });
    Object.defineProperty(big, "size", { value: 201 * 1024 * 1024 });

    fireEvent.change(screen.getByLabelText("Add video"), { target: { files: [big] } });
    expect(screen.getByText(/big.mp4: failed. Larger than 200 MB/)).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Add video"), { target: { files: [clip] } });
    await waitFor(() => expect(onChange).toHaveBeenCalledWith([photo(1), video(9)]));
    expect(uploadCalls).toEqual([["video", 7, clip, { alt: "Silk saree, Ruby", colourName: "Ruby" }]]);

    view.rerender(
      <ProductPhotos
        productId={7}
        productName="Silk saree"
        colours={["Ruby"]}
        media={[photo(1), video(9)]}
        onChange={onChange}
      />,
    );
    expect(screen.getByText(/1 of 12 photos · 1 video/)).toBeInTheDocument();
    expect(screen.getByLabelText("Silk saree, drape")).toHaveAttribute(
      "src",
      "https://media.vaabhi.com/products/7/v9/video",
    );
    fireEvent.click(screen.getByRole("button", { name: "Delete video" }));
    await waitFor(() => expect(onChange).toHaveBeenLastCalledWith([photo(1)]));
  });

  it("keeps the video last when photos are reordered", async () => {
    const saved: unknown[] = [];
    server.use(
      http.put(MEDIA, async ({ request }) => {
        saved.push(await request.json());
        return HttpResponse.json([]);
      }),
    );
    setup([photo(1), video(9, { status: "PENDING" }), photo(2)]);
    expect(screen.getAllByText(/Upload did not finish/)).toHaveLength(1);
    fireEvent.click(screen.getByRole("button", { name: "Move photo 1 later" }));
    await waitFor(() => expect(saved).toHaveLength(1));
    expect((saved[0] as Array<{ id: number }>).map((u) => u.id)).toEqual([2, 1, 9]);
  });

  it("uploads chosen and dropped images with a default description and colour", async () => {
    uploadImpl = async () => [photo(1)];
    const { onChange } = setup([]);
    const file = new File(["x"], "front.jpg", { type: "image/jpeg" });

    fireEvent.change(screen.getByLabelText("Add photos"), { target: { files: [file] } });
    await waitFor(() => expect(onChange).toHaveBeenCalledWith([photo(1)]));
    expect(uploadCalls).toEqual([[7, file, { alt: "Silk saree, Ruby", colourName: "Ruby" }]]);

    const dropZone = screen.getByText("Tap to choose from your gallery, or drop files here").closest("label")!;
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
    const dropZone = screen.getByText("Tap to choose from your gallery, or drop files here").closest("label")!;
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

  it("shows the upload guidelines and the photo count", () => {
    setup([photo(1), photo(2)]);
    expect(screen.getByText(/2 of 12 photos · no video yet/)).toBeInTheDocument();
    const guidelines = within(screen.getByRole("list", { name: "Photo guidelines" })).getAllByRole("listitem");
    expect(guidelines.map((li) => li.textContent?.split(":")[0])).toEqual(expect.arrayContaining(["Formats", "Size"]));
    expect(guidelines.some((li) => li.textContent?.includes("Up to 12 photos per product"))).toBe(true);
  });

  it("stops at 12 photos", async () => {
    const eleven = Array.from({ length: 11 }, (_, i) => photo(i + 1));
    setup(eleven);
    fireEvent.change(screen.getByLabelText("Add photos"), {
      target: {
        files: [new File(["x"], "a.jpg", { type: "image/jpeg" }), new File(["x"], "b.jpg", { type: "image/jpeg" })],
      },
    });
    const list = await screen.findByRole("list", { name: "Uploads" });
    await waitFor(() => expect(within(list).getByText(/b.jpg: failed. Over the 12-photo limit/)).toBeInTheDocument());
    expect(uploadCalls).toHaveLength(1);
  });

  it("disables adding when the product already has 12 photos", () => {
    setup(Array.from({ length: 12 }, (_, i) => photo(i + 1)));
    expect(screen.getByLabelText("Add photos")).toBeDisabled();
    expect(screen.getByText("This product has 12 photos. Delete one to add another.")).toBeInTheDocument();
    fireEvent.drop(screen.getByText(/Delete one to add another/).closest("label")!, {
      dataTransfer: { files: [new File(["x"], "c.jpg", { type: "image/jpeg" })] },
    });
    expect(uploadCalls).toHaveLength(0);
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
