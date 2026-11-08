import Link from "next/link";
import type { Banner, HomeContent, ProductSummary } from "@vaabhi/api-client";
import {
  buttonClasses,
  CashIcon,
  CategoryTile,
  HandloomIcon,
  ProductCard,
  ReturnIcon,
  SectionHeader,
  TruckIcon,
} from "@vaabhi/ui";
import { api } from "@/lib/api";
import { whatsappLink } from "@/lib/whatsapp";
import { mediaUrl, photoSrcSet, productCardProps } from "@/lib/listing";
import { loadCategoryTiles, loadOccasionTiles } from "@/lib/shop-sections";

// Rendered on request; the API response is cached for 60 s, so builds do not need the API.
export const dynamic = "force-dynamic";

/** Products marked Bestseller in the admin, most popular first; an empty list hides the section. */
async function loadBestsellers(): Promise<ProductSummary[]> {
  try {
    return await api.store.listFeatured({ badge: "BESTSELLER", limit: 8 }, { next: { revalidate: 60 } });
  } catch {
    return [];
  }
}

const NO_BANNERS: HomeContent = { hero: [], occasions: [], craft: null };

/** Banners set in the admin (Storefront); sections without a live banner are left out. */
async function loadHome(): Promise<HomeContent> {
  try {
    return await api.store.getHome({ next: { revalidate: 60 } });
  } catch {
    return NO_BANNERS;
  }
}

/** A banner's photo at full width; undefined without a media domain (local development). */
function bannerPhoto(banner: Banner, sizes: string) {
  const src = mediaUrl(banner.imageKey, 1600);
  return src ? { src, srcSet: photoSrcSet(banner.imageKey), sizes, alt: banner.altText ?? "" } : undefined;
}

/**
 * Where "Join on WhatsApp" goes, from Settings → Shop profile: the shop's WhatsApp channel (or community) when it
 * has one, else a chat with the shop's number, else the contact page (no number, or the API is down).
 */
async function loadJoinLink(): Promise<{ href: string; external: boolean }> {
  try {
    const shop = await api.store.getShop({ next: { revalidate: 60 } });
    if (shop.whatsappChannel) return { href: shop.whatsappChannel, external: true };
    if (shop.whatsapp) {
      return { href: whatsappLink(shop.whatsapp, "Hi Vaabhi, I'd like festive drop updates"), external: true };
    }
  } catch {
    // Falls through to the contact page.
  }
  return { href: "/contact", external: false };
}

const container = "mx-auto w-full max-w-6xl px-4";

export default async function HomePage() {
  const [categories, occasions, bestsellers, home, join] = await Promise.all([
    loadCategoryTiles(),
    loadOccasionTiles(),
    loadBestsellers(),
    loadHome(),
    loadJoinLink(),
  ]);
  const hero = home.hero[0];
  const heroPhoto = hero && bannerPhoto(hero, "100vw");
  const craftPhoto = home.craft && bannerPhoto(home.craft, "(min-width: 768px) 50vw, 100vw");

  return (
    <>
      {/* Hero: the first live hero banner; without one, a plain brand heading */}
      <section
        aria-labelledby="hero-title"
        className="relative isolate flex min-h-[520px] items-end overflow-hidden bg-gradient-to-b from-[#C8B8A2] to-[#4E2F2B] md:min-h-[600px]"
      >
        {heroPhoto && (
          <>
            {/* eslint-disable-next-line @next/next/no-img-element -- R2 serves fixed WebP sizes; srcset picks one */}
            <img
              {...heroPhoto}
              alt={heroPhoto.alt}
              fetchPriority="high"
              className="absolute inset-0 -z-10 size-full object-cover"
            />
            <span aria-hidden="true" className="absolute inset-0 -z-10 bg-gradient-to-t from-black/60 to-transparent" />
          </>
        )}
        <div className={`${container} flex flex-col gap-3 py-6 md:py-16`}>
          {hero?.eyebrow && (
            <p className="text-xs uppercase tracking-[0.16em] text-brand-accent-soft">{hero.eyebrow}</p>
          )}
          <h1
            id="hero-title"
            className="max-w-xl font-serif text-[40px] font-semibold leading-[44px] text-text-on-brand md:text-[56px] md:leading-[64px]"
          >
            {hero?.title ?? "Handwoven sarees, lehengas & kurta sets"}
          </h1>
          {hero?.subtitle && <p className="max-w-md text-base text-text-on-brand">{hero.subtitle}</p>}
          {hero?.link && (
            <Link href={hero.link} className={buttonClasses("primary", "self-start")}>
              {hero.ctaLabel ?? "Shop now"}
            </Link>
          )}
        </div>
      </section>

      {/* Shop by category: the categories that have products, pictured with a real photo */}
      {categories === null ? (
        <section aria-labelledby="categories-title" className={`${container} flex flex-col gap-4 py-8`}>
          <SectionHeader id="categories-title" title="Shop by category" />
          <p className="text-sm text-feedback-error">The catalogue is unavailable right now.</p>
        </section>
      ) : (
        categories.length > 0 && (
          <section aria-labelledby="categories-title" className={`${container} flex flex-col gap-4 py-8`}>
            <SectionHeader id="categories-title" title="Shop by category" />
            <nav aria-label="Shop by category" className="-mx-4 overflow-x-auto px-4">
              <ul className="flex gap-4 md:justify-center md:gap-10">
                {categories.map((category) => {
                  const src = category.imageKey ? mediaUrl(category.imageKey, 400) : undefined;
                  return (
                    <li key={category.href}>
                      <CategoryTile
                        label={category.label}
                        href={category.href}
                        image={src && category.imageKey ? { src, srcSet: photoSrcSet(category.imageKey) } : undefined}
                      />
                    </li>
                  );
                })}
              </ul>
            </nav>
          </section>
        )
      )}

      {/* Shop by occasion: the occasions products are tagged with, each across every category */}
      {occasions.length > 0 && (
        <section aria-labelledby="occasions-title" className={`${container} flex flex-col gap-4 py-8`}>
          <SectionHeader id="occasions-title" title="Shop by occasion" />
          <ul className="grid grid-cols-2 gap-3 md:grid-cols-4">
            {occasions.map((occasion) => {
              const src = occasion.imageKey ? mediaUrl(occasion.imageKey, 800) : undefined;
              const text = src ? "text-text-on-brand" : "text-text-primary";
              return (
                <li key={occasion.href}>
                  <Link
                    href={occasion.href}
                    className={`relative isolate flex h-[150px] flex-col justify-end gap-1 overflow-hidden rounded-sm p-4 transition-opacity hover:opacity-90 md:h-[220px] ${src ? "" : "border border-border-default bg-bg-muted"}`}
                  >
                    {src && occasion.imageKey && (
                      <>
                        {/* eslint-disable-next-line @next/next/no-img-element -- R2 serves fixed WebP sizes */}
                        <img
                          src={src}
                          srcSet={photoSrcSet(occasion.imageKey)}
                          sizes="(min-width: 768px) 25vw, 50vw"
                          alt=""
                          loading="lazy"
                          className="absolute inset-0 -z-10 size-full object-cover"
                        />
                        <span
                          aria-hidden="true"
                          className="absolute inset-0 -z-10 bg-gradient-to-t from-black/60 to-transparent"
                        />
                      </>
                    )}
                    <span className={`font-serif text-[22px] font-semibold leading-7 ${text}`}>{occasion.label}</span>{" "}
                    <span className={`text-xs tracking-[0.04em] ${text}`}>
                      {occasion.count} {occasion.count === 1 ? "piece" : "pieces"} · Shop now →
                    </span>
                  </Link>
                </li>
              );
            })}
          </ul>
        </section>
      )}

      {/* Bestsellers */}
      {bestsellers.length > 0 && (
        <section aria-labelledby="bestsellers-title" className="bg-bg-surface">
          <div className={`${container} flex flex-col gap-4 py-8`}>
            <SectionHeader
              id="bestsellers-title"
              title="Bestsellers"
              action={{ label: "View all", href: "/bestsellers" }}
            />
            <ul className="grid grid-cols-2 gap-x-4 gap-y-5 md:grid-cols-4 md:gap-6">
              {bestsellers.map((product) => (
                <li key={product.slug}>
                  <ProductCard {...productCardProps(product)} />
                </li>
              ))}
            </ul>
          </div>
        </section>
      )}

      {/* The craft (olive accent): the live craft banner's photo and words */}
      {home.craft && (
        <section aria-labelledby="craft-title" className="bg-brand-olive-soft">
          <div className={`${container} grid items-center gap-6 py-8 md:grid-cols-2`}>
            <div className="flex flex-col gap-3.5">
              {home.craft.eyebrow && (
                <p className="text-xs uppercase tracking-[0.16em] text-brand-olive">{home.craft.eyebrow}</p>
              )}
              <h2 id="craft-title" className="font-serif text-[26px] font-semibold leading-tight text-text-primary">
                {home.craft.title}
              </h2>
              {home.craft.subtitle && <p className="max-w-xl text-base text-text-secondary">{home.craft.subtitle}</p>}
              {home.craft.link && (
                <Link href={home.craft.link} className={buttonClasses("secondary", "mt-1 self-start")}>
                  {home.craft.ctaLabel ?? "Read more"}
                </Link>
              )}
            </div>
            {craftPhoto && (
              // eslint-disable-next-line @next/next/no-img-element -- R2 serves fixed WebP sizes; srcset picks one
              <img
                {...craftPhoto}
                alt={craftPhoto.alt}
                loading="lazy"
                className="aspect-[4/3] w-full rounded-sm object-cover"
              />
            )}
          </div>
        </section>
      )}

      {/* Trust */}
      <section aria-label="Why shop with us" className="border-y border-border-default">
        <ul className={`${container} grid grid-cols-4 gap-2 py-6`}>
          {[
            [HandloomIcon, "Authentic handloom"],
            [TruckIcon, "Free shipping over ₹1,999"],
            [ReturnIcon, "7-day returns"],
            [CashIcon, "Cash on delivery"],
          ].map(([Icon, label]) => {
            const I = Icon as typeof HandloomIcon;
            return (
              <li
                key={label as string}
                className="flex flex-col items-center gap-1.5 text-center text-xs text-text-secondary"
              >
                <I size={24} className="text-brand-accent" />
                {label as string}
              </li>
            );
          })}
        </ul>
      </section>

      {/* Stay in touch */}
      <section
        aria-labelledby="stay-title"
        className={`${container} flex flex-col items-center gap-3 py-8 text-center`}
      >
        <h2 id="stay-title" className="font-serif text-[22px] font-semibold text-text-primary">
          Be first to the festive drops
        </h2>
        <p className="max-w-sm text-sm text-text-muted">
          New collections, restocks and private sale access — on WhatsApp.
        </p>
        <a
          href={join.href}
          {...(join.external ? { target: "_blank", rel: "noopener" } : {})}
          className={buttonClasses("primary")}
        >
          Join on WhatsApp
        </a>
      </section>
    </>
  );
}
