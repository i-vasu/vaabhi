import Link from "next/link";
import type { Category } from "@vaabhi/api-client";
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
import { discountLabel, formatPrice } from "@/lib/format";
import { bestsellers, categoryColors, fallbackCategoryColors, occasions, weaves } from "@/lib/home-content";

// Rendered on request; the API response is cached for 60 s, so builds do not need the API.
export const dynamic = "force-dynamic";

async function loadCategories(): Promise<Category[] | null> {
  try {
    return await api.store.listCategories({ next: { revalidate: 60 } });
  } catch {
    return null;
  }
}

const container = "mx-auto w-full max-w-6xl px-4";
const whatsappHref = process.env.NEXT_PUBLIC_WHATSAPP_NUMBER
  ? `https://wa.me/${process.env.NEXT_PUBLIC_WHATSAPP_NUMBER}?text=${encodeURIComponent("Hi Vaabhi, I'd like festive drop updates")}`
  : "/contact";

const occasionText = { light: "text-text-on-brand", olive: "text-brand-olive", dark: "text-text-primary" } as const;

export default async function HomePage() {
  const categories = await loadCategories();

  return (
    <>
      {/* Hero */}
      <section
        aria-labelledby="hero-title"
        className="flex min-h-[520px] items-end bg-gradient-to-b from-[#C8B8A2] to-[#4E2F2B] md:min-h-[600px]"
      >
        <div className={`${container} flex flex-col gap-3 py-6 md:py-16`}>
          <p className="text-xs tracking-[0.16em] text-brand-accent-soft">THE WEDDING EDIT · 2026</p>
          <h1
            id="hero-title"
            className="max-w-xl font-serif text-[40px] font-semibold leading-[44px] text-text-on-brand md:text-[56px] md:leading-[64px]"
          >
            Heirloom silks for the season
          </h1>
          <p className="max-w-md text-base text-text-on-brand">
            Banarasi, Kanjeevaram and Paithani — handwoven, ready to ship.
          </p>
          <Link href="/edit/wedding" className={buttonClasses("primary", "self-start")}>
            Explore the edit
          </Link>
        </div>
      </section>

      {/* Shop by category */}
      <section aria-labelledby="categories-title" className={`${container} flex flex-col gap-4 py-8`}>
        <SectionHeader id="categories-title" title="Shop by category" />
        {categories === null ? (
          <p className="text-sm text-feedback-error">The catalogue is unavailable right now.</p>
        ) : (
          <nav aria-label="Shop by category" className="-mx-4 overflow-x-auto px-4">
            <ul className="flex gap-4 md:justify-center md:gap-10">
              {categories.map((category) => (
                <li key={category.id}>
                  <CategoryTile
                    label={category.name ?? ""}
                    href={`/c/${category.slug}`}
                    colors={categoryColors[category.slug ?? ""] ?? fallbackCategoryColors}
                  />
                </li>
              ))}
            </ul>
          </nav>
        )}
      </section>

      {/* Shop by occasion */}
      <section aria-labelledby="occasions-title" className={`${container} flex flex-col gap-4 py-8`}>
        <SectionHeader
          id="occasions-title"
          title="Shop by occasion"
          action={{ label: "View all", href: "/occasion" }}
        />
        <ul className="grid grid-cols-2 gap-3 md:grid-cols-4">
          {occasions.map((occasion) => (
            <li key={occasion.href}>
              <Link
                href={occasion.href}
                className="flex h-[150px] flex-col justify-end gap-1 rounded-sm p-4 transition-opacity hover:opacity-90 md:h-[220px]"
                style={{ backgroundImage: `linear-gradient(180deg, ${occasion.colors[0]}, ${occasion.colors[1]})` }}
              >
                <span className={`font-serif text-[22px] font-semibold leading-7 ${occasionText[occasion.tone]}`}>
                  {occasion.label}
                </span>
                <span className={`text-xs tracking-[0.04em] ${occasionText[occasion.tone]}`}>Shop now →</span>
              </Link>
            </li>
          ))}
        </ul>
      </section>

      {/* Bestsellers */}
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
                <ProductCard
                  name={product.name}
                  detail={product.detail}
                  price={formatPrice(product.price)}
                  mrp={product.mrp ? formatPrice(product.mrp) : undefined}
                  discount={discountLabel(product.price, product.mrp)}
                  href={`/p/${product.slug}`}
                  badge={product.badge}
                  image={{ placeholder: product.colors }}
                />
              </li>
            ))}
          </ul>
        </div>
      </section>

      {/* The craft (olive accent) */}
      <section aria-labelledby="craft-title" className="bg-brand-olive-soft">
        <div className={`${container} flex max-w-6xl flex-col gap-3.5 py-8`}>
          <p className="text-xs tracking-[0.16em] text-brand-olive">THE CRAFT</p>
          <h2 id="craft-title" className="font-serif text-[26px] font-semibold leading-tight text-text-primary">
            Woven by hand, worn for generations
          </h2>
          <p className="max-w-xl text-base text-text-secondary">
            We work directly with weaving clusters in Varanasi, Kanchipuram and Chanderi — every piece carries its
            weaver’s story.
          </p>
          <ul className="flex flex-wrap gap-2" aria-label="Weaves">
            {weaves.map((weave) => (
              <li
                key={weave}
                className="rounded-full border border-brand-olive bg-bg-page px-3 py-1.5 text-sm text-brand-olive"
              >
                {weave}
              </li>
            ))}
          </ul>
          <Link href="/weavers" className={buttonClasses("secondary", "mt-1 self-start")}>
            Meet the weavers
          </Link>
        </div>
      </section>

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
        <a href={whatsappHref} className={buttonClasses("primary")}>
          Join on WhatsApp
        </a>
      </section>
    </>
  );
}
