import Link from "next/link";
import { api } from "@/lib/api";
import { type NavLink, footerColumns, footerShopListings } from "@/lib/site-links";

/** The shop's categories, in its order; none when the catalogue is down, so the footer still renders. */
async function categoryLinks(): Promise<NavLink[]> {
  try {
    const categories = await api.store.listCategories({ next: { revalidate: 60 } });
    return categories.map((c): NavLink => [c.name, `/c/${c.slug}`]);
  } catch {
    return [];
  }
}

export async function SiteFooter() {
  const columns = [{ title: "Shop", links: [...(await categoryLinks()), ...footerShopListings] }, ...footerColumns];
  return (
    <footer className="bg-bg-muted">
      <div className="mx-auto flex max-w-6xl flex-col gap-8 px-4 py-10">
        <div className="grid gap-8 md:grid-cols-[minmax(0,1.4fr)_repeat(4,minmax(0,1fr))]">
          <div className="flex flex-col gap-3">
            <p className="font-serif text-[22px] font-semibold tracking-[0.14em] text-brand-primary">VAABHI</p>
            <p className="max-w-[38ch] text-sm leading-relaxed text-text-secondary">
              Handwoven sarees, lehengas and kurtis, made with weaver families across India. Every piece is checked by
              hand, packed with care and easy to return.
            </p>
          </div>
          <div className="grid grid-cols-2 gap-x-6 gap-y-8 sm:grid-cols-4 md:contents">
            {columns.map((column) => (
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
