import Link from "next/link";
import type { ProductSummary } from "@vaabhi/api-client";
import { buttonClasses, ProductCard } from "@vaabhi/ui";
import { productCardProps } from "@/lib/listing";

const container = "mx-auto w-full max-w-6xl px-4";

/**
 * A titled product grid across categories (New arrivals, Bestsellers), laid out like a category page.
 * `more` is the "Load more" link when there are further pages; null products means the API was unreachable.
 */
export function CollectionView({
  title,
  intro,
  products,
  total,
  more,
  empty,
}: Readonly<{
  title: string;
  intro: string;
  products: ProductSummary[] | null;
  total?: number;
  more?: string;
  empty: string;
}>) {
  const count = total ?? products?.length ?? 0;
  return (
    <>
      <div className={`${container} flex flex-col gap-1.5 pt-4 pb-4`}>
        <nav aria-label="Breadcrumb">
          <ol className="flex gap-1.5 text-xs text-text-muted">
            <li>
              <Link href="/" className="hover:underline">
                Home
              </Link>
            </li>
            <li aria-hidden="true">/</li>
            <li aria-current="page" className="text-text-primary">
              {title}
            </li>
          </ol>
        </nav>
        <h1 className="font-serif text-[32px] font-semibold leading-10 text-text-primary">{title}</h1>
        <p className="text-sm text-text-muted">
          {intro}
          {products && products.length > 0 && ` — ${count} ${count === 1 ? "style" : "styles"}`}
        </p>
      </div>

      <div className={container}>
        {products === null ? (
          <p className="py-16 text-center text-sm text-feedback-error">
            The catalogue is unavailable right now. Please try again shortly.
          </p>
        ) : products.length === 0 ? (
          <div className="flex flex-col items-center gap-4 py-16 text-center">
            <p className="font-serif text-2xl text-text-primary">{empty}</p>
            <Link href="/" className={buttonClasses("secondary")}>
              Back to the shop
            </Link>
          </div>
        ) : (
          <>
            <ul aria-label={title} className="grid grid-cols-2 gap-x-4 gap-y-6 md:grid-cols-3 lg:grid-cols-4">
              {products.map((product) => (
                <li key={product.slug}>
                  <ProductCard {...productCardProps(product)} />
                </li>
              ))}
            </ul>
            <div className="flex flex-col items-center gap-2.5 px-4 py-8">
              <p className="text-xs tracking-[0.02em] text-text-muted">
                Showing {products.length} of {count}
              </p>
              {more && (
                <Link href={more} scroll={false} className={buttonClasses("secondary")}>
                  Load more
                </Link>
              )}
            </div>
          </>
        )}
      </div>
    </>
  );
}
