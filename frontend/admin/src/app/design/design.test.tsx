import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { GalleryPhoto } from "@vaabhi/api-client";
import { API, server } from "@/test/server";
import { serveSettings } from "@/test/settings-fixtures";
import DesignPage from "./page";

vi.mock("next/navigation", () => ({ useRouter: () => ({ replace: vi.fn() }) }));

// Photos are resized on a canvas, which jsdom does not have; the resize is not what these tests cover.
vi.mock("@/lib/image-resize", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/image-resize")>()),
  resizeToWebp: async () => [{ width: 800, pixelWidth: 800, pixelHeight: 1000, blob: new Blob(["webp"]) }],
}));

const UPLOADS = "https://uploads.test";

const photo = (id: number, patch: Partial<GalleryPhoto> = {}): GalleryPhoto => ({
  id,
  kind: "DESIGN",
  title: "Boat neck blouse",
  tags: "boat neck, silk",
  templateId: 1,
  garment: "Blouse",
  clientId: null,
  clientName: null,
  orderId: null,
  photoKey: `gallery/${id}`,
  createdBy: "Asha Rao",
  createdAt: "2026-11-02T09:00:00+05:30",
  ...patch,
});

let photos: GalleryPhoto[] = [];
let listed: URL[] = [];
let created: unknown;

beforeEach(() => {
  serveSettings();
  photos = [];
  listed = [];
  created = undefined;
  server.use(
    http.get(`${API}/api/v1/admin/gallery`, ({ request }) => {
      const url = new URL(request.url);
      listed.push(url);
      const kind = url.searchParams.get("kind") ?? "DESIGN";
      return HttpResponse.json(photos.filter((p) => p.kind === kind));
    }),
    http.post(`${API}/api/v1/admin/gallery/uploads`, () =>
      HttpResponse.json({
        key: "gallery/new-1",
        uploadUrl: `${UPLOADS}/gallery/new-1`,
        expiresAt: "2026-11-09T10:00:00Z",
      }),
    ),
    http.put(`${UPLOADS}/*`, () => new HttpResponse(null, { status: 200 })),
    http.post(`${API}/api/v1/admin/gallery`, async ({ request }) => {
      created = await request.json();
      return HttpResponse.json(photo(9, { title: "Boat neck kurta" }), { status: 201 });
    }),
    http.delete(`${API}/api/v1/admin/gallery/:id`, ({ params }) => {
      photos = photos.filter((p) => p.id !== Number(params.id));
      return new HttpResponse(null, { status: 204 });
    }),
  );
});

const chooseFile = (input: HTMLElement) =>
  fireEvent.change(input, { target: { files: [new File(["x"], "kurta.jpg", { type: "image/jpeg" })] } });

describe("design gallery", () => {
  it("shows a loading state, then the photos with their garment and tags", async () => {
    photos = [photo(1), photo(2, { kind: "CUSTOMER", title: "Customer kurta" })];
    render(<DesignPage />);

    expect(screen.getByText("Loading…")).toBeInTheDocument();
    const img = await screen.findByRole("img", { name: "Boat neck blouse" });
    expect(img.getAttribute("src")).toMatch(/\/gallery\/1\/800\.webp$/);
    expect(screen.getByText("Blouse · boat neck, silk")).toBeInTheDocument();
    expect(screen.queryByRole("img", { name: "Customer kurta" })).not.toBeInTheDocument();
  });

  it("shows an empty state when there are no photos", async () => {
    render(<DesignPage />);
    expect(await screen.findByText("No photos here yet")).toBeInTheDocument();
  });

  it("switches to customer garments and passes the search on", async () => {
    photos = [photo(1), photo(2, { kind: "CUSTOMER", title: "Customer kurta" })];
    render(<DesignPage />);
    await screen.findByRole("img", { name: "Boat neck blouse" });

    fireEvent.click(screen.getByRole("radio", { name: "Customer garments" }));
    expect(await screen.findByRole("img", { name: "Customer kurta" })).toBeInTheDocument();
    expect(listed[listed.length - 1]?.searchParams.get("kind")).toBe("CUSTOMER");

    fireEvent.change(screen.getByRole("searchbox", { name: "Search gallery" }), { target: { value: " boat neck " } });
    fireEvent.submit(screen.getByRole("search"));
    await waitFor(() => expect(listed[listed.length - 1]?.searchParams.get("q")).toBe("boat neck"));
  });

  it("removes a photo and reloads the gallery", async () => {
    photos = [photo(1)];
    render(<DesignPage />);

    fireEvent.click(await screen.findByRole("button", { name: "Remove Boat neck blouse" }));
    expect(await screen.findByText("No photos here yet")).toBeInTheDocument();
  });

  it("shows why a photo could not be removed", async () => {
    photos = [photo(1)];
    server.use(
      http.delete(`${API}/api/v1/admin/gallery/1`, () =>
        HttpResponse.json({ detail: "Photo is used on an order" }, { status: 409 }),
      ),
    );
    render(<DesignPage />);

    fireEvent.click(await screen.findByRole("button", { name: "Remove Boat neck blouse" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Photo is used on an order");
    expect(screen.getByRole("img", { name: "Boat neck blouse" })).toBeInTheDocument();
  });

  it("explains when the gallery cannot load", async () => {
    server.use(http.get(`${API}/api/v1/admin/gallery`, () => HttpResponse.error()));
    render(<DesignPage />);
    expect(await screen.findByRole("alert")).toHaveTextContent("Could not reach the server");
  });
});

describe("add photo sheet", () => {
  it("closes without saving", async () => {
    render(<DesignPage />);

    fireEvent.click(await screen.findByRole("button", { name: "Add photo" }));
    fireEvent.click(
      within(screen.getByRole("dialog", { name: "Add to Design" })).getByRole("button", { name: "Close" }),
    );

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(created).toBeUndefined();
  });

  it("uploads a photo, validates, then saves it with the chosen gallery and garment", async () => {
    render(<DesignPage />);

    fireEvent.click(await screen.findByRole("button", { name: "Add photo" }));
    const sheet = screen.getByRole("dialog", { name: "Add to Design" });

    fireEvent.click(within(sheet).getByRole("button", { name: "Save photo" }));
    expect(await within(sheet).findByRole("alert")).toHaveTextContent("Choose the photo first.");

    chooseFile(within(sheet).getByLabelText("Photo"));
    expect(await within(sheet).findByText("Photo uploaded — change")).toBeInTheDocument();

    fireEvent.click(within(sheet).getByRole("button", { name: "Save photo" }));
    expect(await within(sheet).findByRole("alert")).toHaveTextContent("Give it a title.");

    await within(sheet).findByRole("option", { name: "Blouse" });
    fireEvent.change(within(sheet).getByLabelText("Title"), { target: { value: "  Boat neck kurta " } });
    fireEvent.change(within(sheet).getByLabelText("Tags"), { target: { value: "silk" } });
    fireEvent.change(within(sheet).getByLabelText("Garment"), { target: { value: "1" } });
    fireEvent.change(within(sheet).getByLabelText("Gallery"), { target: { value: "CUSTOMER" } });
    fireEvent.click(within(sheet).getByRole("button", { name: "Save photo" }));

    await waitFor(() =>
      expect(created).toEqual({
        kind: "CUSTOMER",
        title: "Boat neck kurta",
        tags: "silk",
        templateId: 1,
        clientId: null,
        orderId: null,
        photoKey: "gallery/new-1",
      }),
    );
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  });

  it("reports a photo that storage refuses and lets the person choose again", async () => {
    server.use(http.put(`${UPLOADS}/*`, () => new HttpResponse(null, { status: 403 })));
    render(<DesignPage />);

    fireEvent.click(await screen.findByRole("button", { name: "Add photo" }));
    const sheet = screen.getByRole("dialog", { name: "Add to Design" });
    chooseFile(within(sheet).getByLabelText("Photo"));

    expect(await within(sheet).findByRole("alert")).toHaveTextContent("Could not save. Try again.");
    expect(within(sheet).getByText("Choose photo")).toBeInTheDocument();
  });
});
