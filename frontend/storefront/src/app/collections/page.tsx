import type { Metadata } from "next";
import Link from "next/link";
import type { Banner, Category } from "@vaabhi/api-client";
import { api } from "@/lib/api";
import { categoryColors, fallbackCategoryColors, occasionTones } from "@/lib/home-content";
import { mediaUrl, photoSrcSet } from "@/lib/listing";

export const dynamic = "force-dynamic";

export const metadata: Metadata = {
  title: "Collections",
  description: "Shop Vaabhi by category and by occasion: sarees, lehengas, kurta sets and more.",
  alternates: { canonical: "/collections" },
};

const cached = { next: { revalidate: 60 } };

async function load(): Promise<{ categories: Category[] | null; occasions: Banner[] }> {
  const [categories, home] = await Promise.all([
    api.store.listCategories(cached).catch(() => null),
    api.store.getHome(cached).catch(() => null),
  ]);
  return { categories, occasions: home?.occasions ?? [] };
}

const tile = "relative isolate flex aspect-[4/5] flex-col justify-end gap-1 overflow-hidden rounded-sm p-4";
const shade = "absolute inset-0 -z-10 bg-gradient-to-t from-black/50 to-transparent";
const tileTitle = "font-serif text-[22px] font-semibold leading-7 text-text-on-brand";

/** A tile's photo, or its colour placeholder until the admin uploads one. */
function TilePhoto({
  imageKey,
  alt,
  sizes,
  colors,
}: Readonly<{ imageKey: string | null; alt: string; sizes: string; colors: readonly [string, string] }>) {
  const src = imageKey ? mediaUrl(imageKey, 800) : undefined;
  return (
    <>
      {src && imageKey ? (
        // eslint-disable-next-line @next/next/no-img-element -- R2 serves fixed WebP sizes
        <img
          src={src}
          srcSet={photoSrcSet(imageKey)}
          sizes={sizes}
          alt={alt}
          loading="lazy"
          className="absolute inset-0 -z-10 size-full object-cover"
        />
      ) : (
        <span
          aria-hidden="true"
          className="absolute inset-0 -z-10"
          style={{ backgroundImage: `linear-gradient(180deg, ${colors[0]}, ${colors[1]})` }}
        />
      )}
      <span aria-hidden="true" className={shade} />
    </>
  );
}

/** Every category (with its photo) and every live occasion banner, in one place. */
export default async function CollectionsPage() {
  const { categories, occasions } = await load();
  return (
    <div className="mx-auto flex w-full max-w-6xl flex-col gap-10 px-4 py-8">
      <h1 className="font-serif text-[32px] font-semibold leading-10 text-text-primary">Collections</h1>

      <section aria-labelledby="by-category" className="flex flex-col gap-4">
        <h2 id="by-category" className="font-serif text-[24px] font-semibold text-text-primary">
          By category
        </h2>
        {categories === null ? (
          <p className="text-sm text-feedback-error">The catalogue is unavailable right now.</p>
        ) : categories.length === 0 ? (
          <p className="text-sm text-text-muted">Collections are coming soon.</p>
        ) : (
          <ul className="grid grid-cols-2 gap-3 md:grid-cols-3 lg:grid-cols-5">
            {categories.map((category) => (
              <li key={category.id}>
                <Link href={`/c/${category.slug}`} className={`${tile} hover:opacity-90`}>
                  <TilePhoto
                    imageKey={category.imageKey}
                    alt=""
                    sizes="(min-width: 1024px) 20vw, (min-width: 768px) 33vw, 50vw"
                    colors={categoryColors[category.slug] ?? fallbackCategoryColors}
                  />
                  <span className={tileTitle}>{category.name}</span>
                  {category.description && (
                    <span className="line-clamp-2 text-xs text-text-on-brand">{category.description}</span>
                  )}
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>

      {occasions.length > 0 && (
        <section aria-labelledby="by-occasion" className="flex flex-col gap-4">
          <h2 id="by-occasion" className="font-serif text-[24px] font-semibold text-text-primary">
            By occasion
          </h2>
          <ul className="grid grid-cols-2 gap-3 md:grid-cols-4">
            {occasions.map((occasion, index) => {
              const body = (
                <>
                  <TilePhoto
                    imageKey={occasion.imageKey}
                    alt={occasion.altText ?? ""}
                    sizes="(min-width: 768px) 25vw, 50vw"
                    colors={occasionTones[index % occasionTones.length]!.colors}
                  />
                  <span className={tileTitle}>{occasion.title}</span>
                </>
              );
              return (
                <li key={occasion.id}>
                  {occasion.link ? (
                    <Link href={occasion.link} className={`${tile} hover:opacity-90`}>
                      {body}
                    </Link>
                  ) : (
                    <div className={tile}>{body}</div>
                  )}
                </li>
              );
            })}
          </ul>
        </section>
      )}
    </div>
  );
}
