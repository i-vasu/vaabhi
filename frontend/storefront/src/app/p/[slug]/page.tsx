import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { cache } from "react";
import { ApiError, type ProductDetail, type ProductSummary } from "@vaabhi/api-client";
import { ChevronDownIcon, ProductCard } from "@vaabhi/ui";
import { ProductView } from "@/components/product/product-view";
import { TrustStrip } from "@/components/trust-strip";
import { api } from "@/lib/api";
import { mediaUrl, productCardProps, type SearchParams } from "@/lib/listing";
import { SHIPPING_AND_RETURNS } from "@/lib/policies";
import { oneSizeLine } from "@/lib/product";

export const dynamic = "force-dynamic";

type Props = { params: Promise<{ slug: string }>; searchParams: Promise<SearchParams> };

const container = "mx-auto w-full max-w-6xl";

/** One API call per request, shared by generateMetadata and the page. 404 → Next's not-found page. */
const loadProduct = cache(async (slug: string): Promise<ProductDetail | null> => {
  try {
    return await api.store.getProduct(slug, { next: { revalidate: 60 } });
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) notFound();
    return null;
  }
});

async function loadRelated(product: ProductDetail): Promise<ProductSummary[]> {
  try {
    const listing = await api.store.listProducts(
      product.category.slug,
      { sort: "popular", pageSize: 5 },
      { next: { revalidate: 300 } },
    );
    return listing.items.filter((p) => p.slug !== product.slug).slice(0, 4);
  } catch {
    return [];
  }
}

export async function generateMetadata({ params }: Readonly<Props>): Promise<Metadata> {
  const { slug } = await params;
  const product = await loadProduct(slug);
  if (!product) return { title: "Vaabhi" };
  const image = product.media[0] ? mediaUrl(product.media[0].key, 1600) : undefined;
  const description = product.subtitle ?? product.description ?? undefined;
  return {
    title: `${product.name} | Vaabhi`,
    description,
    alternates: { canonical: `/p/${product.slug}` },
    openGraph: { title: product.name, description, images: image ? [image] : undefined },
  };
}

/** schema.org Product data so search engines can show price and stock. */
function structuredData(product: ProductDetail) {
  const prices = product.variants.map((v) => v.pricePaise / 100);
  return {
    "@context": "https://schema.org",
    "@type": "Product",
    name: product.name,
    description: product.description ?? product.subtitle ?? undefined,
    sku: product.variants[0]?.sku,
    image: product.media.map((m) => mediaUrl(m.key, 1600)).filter(Boolean),
    brand: { "@type": "Brand", name: "Vaabhi" },
    offers: {
      "@type": "AggregateOffer",
      priceCurrency: "INR",
      lowPrice: Math.min(...prices),
      highPrice: Math.max(...prices),
      offerCount: product.variants.length,
      availability: product.variants.some((v) => v.inStock)
        ? "https://schema.org/InStock"
        : "https://schema.org/OutOfStock",
    },
  };
}

function Section({ title, open, children }: Readonly<{ title: string; open?: boolean; children: React.ReactNode }>) {
  return (
    <details open={open} className="group border-t border-border-default last:border-b">
      <summary className="flex cursor-pointer list-none items-center justify-between py-4 font-medium tracking-[0.02em] [&::-webkit-details-marker]:hidden">
        {title}
        <ChevronDownIcon size={18} className="transition-transform group-open:rotate-180" />
      </summary>
      <div className="max-w-prose pb-4 text-sm text-text-secondary">{children}</div>
    </details>
  );
}

export default async function ProductPage({ params, searchParams }: Readonly<Props>) {
  const { slug } = await params;
  const product = await loadProduct(slug);
  if (!product) {
    return (
      <p className={`${container} px-4 py-16 text-center text-sm text-feedback-error`}>
        This product is unavailable right now. Please try again shortly.
      </p>
    );
  }
  const colourParam = (await searchParams).colour;
  const related = await loadRelated(product);
  const details: Array<[string, string]> = [
    ...product.attributes.map((a): [string, string] => [a.name, a.values.map((v) => v.name).join(", ")]),
    ...(product.sareeLengthM === null ? [] : [["Saree length", `${product.sareeLengthM} m`] as [string, string]]),
    ...(product.blouseIncluded && product.blouseLengthM !== null
      ? [["Blouse piece", `${product.blouseLengthM} m, included`] as [string, string]]
      : []),
    ...(product.variants.every((v) => v.size === null) && product.sareeLengthM === null
      ? [["Size", oneSizeLine(product)] as [string, string]]
      : []),
  ];

  return (
    <>
      <script
        type="application/ld+json"
        // JSON.stringify output escapes nothing HTML-specific; "<" is escaped so a product name cannot close the tag.
        dangerouslySetInnerHTML={{
          __html: JSON.stringify(structuredData(product)).replaceAll("<", String.raw`\u003c`),
        }}
      />
      <nav aria-label="Breadcrumb" className={`${container} px-4 py-3`}>
        <ol className="flex flex-wrap gap-1.5 text-xs text-text-muted">
          <li>
            <Link href="/" className="hover:underline">
              Home
            </Link>
          </li>
          <li aria-hidden="true">/</li>
          <li>
            <Link href={`/c/${product.category.slug}`} className="hover:underline">
              {product.category.name}
            </Link>
          </li>
          <li aria-hidden="true">/</li>
          <li aria-current="page" className="text-text-primary">
            {product.name}
          </li>
        </ol>
      </nav>

      <div className={container}>
        <ProductView product={product} colour={Array.isArray(colourParam) ? colourParam[0] : colourParam} />
      </div>

      <TrustStrip />

      <section aria-label="Product information" className={`${container} px-4 py-2`}>
        {product.description && (
          <Section title="Description" open>
            {product.description.split(/\n{2,}/).map((p) => (
              <p key={p} className="mb-2">
                {p}
              </p>
            ))}
          </Section>
        )}
        <Section title="Product details" open>
          <dl className="grid grid-cols-[130px_1fr] gap-x-3 gap-y-2">
            {details.map(([k, v]) => (
              <div key={k} className="contents">
                <dt className="text-text-muted">{k}</dt>
                <dd className="text-text-primary">{v}</dd>
              </div>
            ))}
          </dl>
        </Section>
        {product.careInstructions && (
          <Section title="Care">
            <p>{product.careInstructions}</p>
          </Section>
        )}
        <Section title="Shipping & returns">
          {SHIPPING_AND_RETURNS.map((line) => (
            <p key={line} className="mb-2">
              {line}
            </p>
          ))}
        </Section>
      </section>

      {related.length > 0 && (
        <section aria-labelledby="related" className={`${container} px-4 pb-10 pt-6`}>
          <h2 id="related" className="mb-3.5 font-serif text-[26px] font-semibold">
            You may also like
          </h2>
          <ul className="grid grid-cols-2 gap-x-4 gap-y-6 md:grid-cols-4">
            {related.map((p) => (
              <li key={p.slug}>
                <ProductCard {...productCardProps(p)} />
              </li>
            ))}
          </ul>
        </section>
      )}
    </>
  );
}
