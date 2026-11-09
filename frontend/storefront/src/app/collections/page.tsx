import type { Metadata } from "next";
import Link from "next/link";
import { buttonClasses } from "@vaabhi/ui";
import { mediaUrl, photoSrcSet } from "@/lib/listing";
import { loadCategoryTiles, loadOccasionTiles, type ShopTile } from "@/lib/shop-sections";

export const dynamic = "force-dynamic";

export const metadata: Metadata = {
  title: "Collections",
  description: "Shop Vaabhi by category and by occasion: sarees, lehengas, kurtis and more.",
  alternates: { canonical: "/collections" },
};

/** One collection: its real photo when there is one, else just its name on a plain card. */
function Tile({ tile, sizes }: Readonly<{ tile: ShopTile; sizes: string }>) {
  const src = tile.imageKey ? mediaUrl(tile.imageKey, 800) : undefined;
  const text = src ? "text-text-on-brand" : "text-text-primary";
  return (
    <Link
      href={tile.href}
      className={`relative isolate flex aspect-[4/5] flex-col justify-end gap-1 overflow-hidden rounded-sm p-4 hover:opacity-90 ${src ? "" : "border border-border-default bg-bg-muted"}`}
    >
      {src && tile.imageKey && (
        <>
          {/* eslint-disable-next-line @next/next/no-img-element -- R2 serves fixed WebP sizes */}
          <img
            src={src}
            srcSet={photoSrcSet(tile.imageKey)}
            sizes={sizes}
            alt=""
            loading="lazy"
            className="absolute inset-0 -z-10 size-full object-cover"
          />
          <span aria-hidden="true" className="absolute inset-0 -z-10 bg-gradient-to-t from-black/50 to-transparent" />
        </>
      )}
      <span className={`font-serif text-[22px] font-semibold leading-7 ${text}`}>{tile.label}</span>{" "}
      <span className={`text-xs ${text}`}>
        {tile.count} {tile.count === 1 ? "piece" : "pieces"}
      </span>
    </Link>
  );
}

/** The shop's categories and occasions, worked out from the products listed (nothing made up). */
export default async function CollectionsPage() {
  const [categories, occasions] = await Promise.all([loadCategoryTiles(), loadOccasionTiles()]);
  const empty = categories !== null && categories.length === 0 && occasions.length === 0;

  return (
    <div className="mx-auto flex w-full max-w-6xl flex-col gap-10 px-4 py-8">
      <h1 className="font-serif text-[32px] font-semibold leading-10 text-text-primary">Collections</h1>

      {categories === null && <p className="text-sm text-feedback-error">The catalogue is unavailable right now.</p>}
      {empty && (
        <div className="flex flex-col items-start gap-4">
          <p className="text-sm text-text-muted">Collections appear here as soon as products are listed.</p>
          <Link href="/new" className={buttonClasses("secondary")}>
            New arrivals
          </Link>
        </div>
      )}

      {categories && categories.length > 0 && (
        <section aria-labelledby="by-category" className="flex flex-col gap-4">
          <h2 id="by-category" className="font-serif text-[24px] font-semibold text-text-primary">
            By category
          </h2>
          <ul className="grid grid-cols-2 gap-3 md:grid-cols-3 lg:grid-cols-5">
            {categories.map((tile) => (
              <li key={tile.href}>
                <Tile tile={tile} sizes="(min-width: 1024px) 20vw, (min-width: 768px) 33vw, 50vw" />
              </li>
            ))}
          </ul>
        </section>
      )}

      {occasions.length > 0 && (
        <section aria-labelledby="by-occasion" className="flex flex-col gap-4">
          <h2 id="by-occasion" className="font-serif text-[24px] font-semibold text-text-primary">
            By occasion
          </h2>
          <ul className="grid grid-cols-2 gap-3 md:grid-cols-4">
            {occasions.map((tile) => (
              <li key={tile.href}>
                <Tile tile={tile} sizes="(min-width: 768px) 25vw, 50vw" />
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}
