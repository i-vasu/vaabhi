import type { Metadata } from "next";
import Link from "next/link";
import { ApiError, type ContentPage, type ShopContact } from "@vaabhi/api-client";
import { buttonClasses, WhatsAppIcon } from "@vaabhi/ui";
import { PageBody } from "@/components/page-body";
import { api } from "@/lib/api";
import { CUSTOMISE_PAGE } from "@/lib/site-links";
import { whatsappLink } from "@/lib/whatsapp";

export const dynamic = "force-dynamic";

const cached = { next: { revalidate: 60 } };

/** The shop's own words about custom orders (Storefront → Website pages, address "customise"), if published. */
async function loadPage(): Promise<ContentPage | null> {
  try {
    return await api.store.getPage(CUSTOMISE_PAGE, cached);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null;
    return null;
  }
}

async function loadShop(): Promise<ShopContact | null> {
  try {
    return await api.store.getShop(cached);
  } catch {
    return null;
  }
}

export async function generateMetadata(): Promise<Metadata> {
  const page = await loadPage();
  return {
    title: page?.title ?? "Customise",
    description: page?.summary ?? "Have a saree, lehenga or blouse made or altered to your measurements by Vaabhi.",
    alternates: { canonical: "/customise" },
  };
}

/**
 * Made-to-order and alterations: the shop's page about custom work when it has written one, and a way to start a
 * conversation either way (WhatsApp with the shop's number, or the contact page).
 */
export default async function CustomisePage() {
  const [page, shop] = await Promise.all([loadPage(), loadShop()]);
  const whatsapp = shop?.whatsapp ? whatsappLink(shop.whatsapp, "Hi Vaabhi, I'd like something customised") : null;

  return (
    <article className="mx-auto flex w-full max-w-3xl flex-col gap-5 px-4 py-8">
      <h1 className="font-serif text-[32px] font-semibold leading-10 text-text-primary">
        {page?.title ?? "Customise"}
      </h1>
      {page ? (
        <PageBody body={page.body} />
      ) : (
        <p className="text-base text-text-secondary">
          Want a piece made to your measurements, a blouse stitched or something altered? Tell us what you have in mind
          and we’ll take it from there.
        </p>
      )}
      <div className="flex flex-wrap gap-3 pt-2">
        {whatsapp && (
          <a href={whatsapp} target="_blank" rel="noopener" className={buttonClasses("primary", "gap-2")}>
            <WhatsAppIcon size={18} />
            Chat on WhatsApp
          </a>
        )}
        <Link href="/contact" className={buttonClasses(whatsapp ? "secondary" : "primary")}>
          Contact us
        </Link>
      </div>
    </article>
  );
}
