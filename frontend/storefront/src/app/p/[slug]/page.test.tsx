import { render, screen, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { afterEach, describe, expect, it, vi } from "vitest";
import { listing, product as card } from "@/test/listing-fixture";
import { saree } from "@/test/product-fixture";
import { API, server } from "@/test/server";
import ProductPage, { generateMetadata } from "./page";

const PRODUCT = `${API}/api/v1/store/products/:slug`;
const LISTING = `${API}/api/v1/store/categories/:slug/products`;

function props(slug = "kanjeevaram-silk-saree", search: Record<string, string> = {}) {
  return { params: Promise.resolve({ slug }), searchParams: Promise.resolve(search) };
}

describe("ProductPage", () => {
  afterEach(() => vi.unstubAllEnvs());

  it("renders the product, details, structured data and related products", async () => {
    server.use(
      http.get(PRODUCT, () => HttpResponse.json(saree())),
      http.get(LISTING, () =>
        HttpResponse.json(
          listing({ items: [card({ slug: "kanjeevaram-silk-saree" }), card({ slug: "other", name: "Other saree" })] }),
        ),
      ),
    );

    const { container } = render(await ProductPage(props("kanjeevaram-silk-saree", { colour: "Mustard" })));

    const crumbs = within(screen.getByRole("navigation", { name: "Breadcrumb" }));
    expect(crumbs.getByRole("link", { name: "Sarees" })).toHaveAttribute("href", "/c/sarees");
    expect(screen.getByRole("radio", { name: /Mustard/ })).toBeChecked();
    expect(screen.getByText("Woven on a pit loom.")).toBeInTheDocument();
    expect(screen.getByText("For weddings.")).toBeInTheDocument();
    expect(screen.getByText("Saree length")).toBeInTheDocument();
    expect(screen.getByText("0.8 m, included")).toBeInTheDocument();
    expect(screen.getByText("Dry clean only.")).toBeInTheDocument();
    expect(screen.getByText(/Stitched blouses cannot be returned/)).toBeInTheDocument();

    const related = within(screen.getByRole("region", { name: "You may also like" }));
    expect(related.getAllByRole("article")).toHaveLength(1);
    expect(related.getByText("Other saree")).toBeInTheDocument();

    const ld = JSON.parse(container.querySelector('script[type="application/ld+json"]')!.textContent!);
    expect(ld).toMatchObject({
      "@type": "Product",
      name: "Kanjeevaram silk saree",
      offers: { priceCurrency: "INR", lowPrice: 18500, availability: "https://schema.org/InStock" },
    });
  });

  it("works without a description, care text or related products", async () => {
    server.use(
      http.get(PRODUCT, () =>
        HttpResponse.json(
          saree({
            description: null,
            careInstructions: null,
            sareeLengthM: null,
            blouseIncluded: false,
            variants: saree().variants.map((v) => ({ ...v, inStock: false })),
          }),
        ),
      ),
      http.get(LISTING, () => new HttpResponse(null, { status: 503 })),
    );

    const { container } = render(await ProductPage(props()));

    expect(screen.queryByText("Description")).not.toBeInTheDocument();
    expect(screen.queryByText("Care")).not.toBeInTheDocument();
    expect(screen.queryByRole("region", { name: "You may also like" })).not.toBeInTheDocument();
    expect(screen.getByText("One size")).toBeInTheDocument();
    expect(
      JSON.parse(container.querySelector('script[type="application/ld+json"]')!.textContent!).offers.availability,
    ).toBe("https://schema.org/OutOfStock");
  });

  it("returns 404 for unknown products and a message when the API is down", async () => {
    server.use(http.get(PRODUCT, () => HttpResponse.json({ status: 404 }, { status: 404 })));
    await expect(ProductPage(props("nope"))).rejects.toThrow();

    server.use(http.get(PRODUCT, () => new HttpResponse(null, { status: 503 })));
    render(await ProductPage(props("down")));
    expect(screen.getByText(/unavailable right now/)).toBeInTheDocument();
    await expect(generateMetadata(props("down"))).resolves.toEqual({ title: "Vaabhi" });
  });

  it("has a title, description, canonical URL and share image", async () => {
    vi.stubEnv("MEDIA_URL", "https://media.vaabhi.com");
    server.use(http.get(PRODUCT, () => HttpResponse.json(saree())));
    await expect(generateMetadata(props("meta"))).resolves.toEqual({
      title: "Kanjeevaram silk saree | Vaabhi",
      description: "Temple border, pure zari",
      alternates: { canonical: "/p/kanjeevaram-silk-saree" },
      openGraph: {
        title: "Kanjeevaram silk saree",
        description: "Temple border, pure zari",
        images: ["https://media.vaabhi.com/products/1/front/1600.webp"],
      },
    });
  });
});
