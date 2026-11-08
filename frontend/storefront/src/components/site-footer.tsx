import Link from "next/link";
import type { Category, ContentPageLink, ShopContact } from "@vaabhi/api-client";
import { api } from "@/lib/api";
import { footerColumns } from "@/lib/site-links";
import { socialLinks } from "@/lib/whatsapp";

const cached = { next: { revalidate: 60 } };

async function load<T>(call: () => Promise<T>, fallback: T): Promise<T> {
  try {
    return await call();
  } catch {
    return fallback;
  }
}

/** Footer: categories and published pages from the API, and the shop's contact details when it has shared them. */
export async function SiteFooter() {
  const [categories, pages, shop] = await Promise.all([
    load<Category[]>(() => api.store.listCategories(cached), []),
    load<ContentPageLink[]>(() => api.store.listPages(cached), []),
    load<ShopContact | null>(() => api.store.getShop(cached), null),
  ]);
  const socials = shop ? socialLinks(shop) : [];

  return (
    <footer className="bg-bg-muted">
      <div className="mx-auto flex max-w-6xl flex-col gap-8 px-4 py-10">
        <div className="grid gap-8 md:grid-cols-[minmax(0,1.4fr)_repeat(4,minmax(0,1fr))]">
          <div className="flex flex-col gap-3">
            <p className="font-serif text-[22px] font-semibold tracking-[0.14em] text-brand-primary">VAABHI</p>
            <p className="max-w-[38ch] text-sm leading-relaxed text-text-secondary">
              Handwoven sarees, lehengas and kurta sets, made with weaver families across India. Every piece is checked
              by hand, packed with care and easy to return.
            </p>
            {shop && (shop.phone || shop.email) && (
              <ul className="flex flex-col gap-1 text-sm text-text-secondary">
                {shop.phone && (
                  <li>
                    <a href={`tel:${shop.phone.replace(/[^+\d]/g, "")}`} className="hover:text-brand-primary">
                      {shop.phone}
                    </a>
                  </li>
                )}
                {shop.email && (
                  <li>
                    <a href={`mailto:${shop.email}`} className="hover:text-brand-primary">
                      {shop.email}
                    </a>
                  </li>
                )}
              </ul>
            )}
            {socials.length > 0 && (
              <ul className="flex gap-4 text-sm">
                {socials.map(([label, href]) => (
                  <li key={label}>
                    <a href={href} rel="noopener" target="_blank" className="text-brand-primary hover:underline">
                      {label}
                    </a>
                  </li>
                ))}
              </ul>
            )}
          </div>
          <div className="grid grid-cols-2 gap-x-6 gap-y-8 sm:grid-cols-4 md:contents">
            {footerColumns(categories, pages).map((column) => (
              <nav key={column.title} aria-label={column.title}>
                <h2 className="mb-3 text-xs font-semibold uppercase tracking-[0.08em] text-text-primary">
                  {column.title}
                </h2>
                <ul className="flex flex-col gap-2">
                  {column.links.map(([label, href]) => (
                    <li key={href}>
                      <Link href={href} className="text-sm text-text-secondary hover:text-brand-primary">
                        {label}
                      </Link>
                    </li>
                  ))}
                </ul>
              </nav>
            ))}
          </div>
        </div>
        <hr className="border-border-default" />
        <p className="text-xs text-text-muted">© 2026 Vaabhi · Made in India · UPI, cards and COD accepted</p>
      </div>
    </footer>
  );
}
