import type { Metadata } from "next";
import type { ShopContact } from "@vaabhi/api-client";
import { buttonClasses } from "@vaabhi/ui";
import { api } from "@/lib/api";
import { socialLinks, whatsappLink } from "@/lib/whatsapp";

export const dynamic = "force-dynamic";

export const metadata: Metadata = {
  title: "Contact us",
  description: "Call, WhatsApp or email Vaabhi about an order, sizes or a custom piece.",
  alternates: { canonical: "/contact" },
};

async function loadShop(): Promise<ShopContact | null> {
  try {
    return await api.store.getShop({ next: { revalidate: 60 } });
  } catch {
    return null;
  }
}

/** "10:00:00" → "10:00 am". */
function time(value: string): string {
  const [h = 0, m = 0] = value.split(":").map(Number);
  return new Date(2000, 0, 1, h, m).toLocaleTimeString("en-IN", { hour: "numeric", minute: "2-digit" });
}

/** How to reach the shop, from the shop profile in the dashboard (Settings → Shop profile). */
export default async function ContactPage() {
  const shop = await loadShop();
  const whatsapp = shop?.whatsapp ? whatsappLink(shop.whatsapp, "Hi Vaabhi, I have a question") : null;
  const socials = shop ? socialLinks(shop) : [];
  const hasDetails = shop && (shop.phone || shop.email || shop.address || whatsapp);

  return (
    <section className="mx-auto flex w-full max-w-3xl flex-col gap-6 px-4 py-8">
      <h1 className="font-serif text-[32px] font-semibold leading-10 text-text-primary">Contact us</h1>
      {!hasDetails ? (
        <p className="text-sm text-text-muted">
          {shop === null
            ? "Our contact details are unavailable right now. Please try again shortly."
            : "Our contact details will be here soon."}
        </p>
      ) : (
        <>
          <p className="text-base text-text-secondary">
            Questions about an order, sizing or a custom piece? We’re happy to help
            {shop.opensAt && shop.closesAt ? `, ${time(shop.opensAt)} – ${time(shop.closesAt)}` : ""}.
          </p>
          <div className="flex flex-wrap gap-3">
            {whatsapp && (
              <a href={whatsapp} className={buttonClasses("primary")}>
                WhatsApp us
              </a>
            )}
            {shop.phone && (
              <a href={`tel:${shop.phone.replace(/[^+\d]/g, "")}`} className={buttonClasses("secondary")}>
                Call {shop.phone}
              </a>
            )}
          </div>
          <dl className="grid gap-4 text-sm sm:grid-cols-[10rem_1fr]">
            {shop.email && (
              <>
                <dt className="font-medium text-text-primary">Email</dt>
                <dd>
                  <a href={`mailto:${shop.email}`} className="text-brand-primary underline">
                    {shop.email}
                  </a>
                </dd>
              </>
            )}
            {shop.address && (
              <>
                <dt className="font-medium text-text-primary">Studio</dt>
                <dd className="text-text-secondary">
                  {shop.address}
                  {shop.mapsLink && (
                    <>
                      {" · "}
                      <a href={shop.mapsLink} rel="noopener" target="_blank" className="text-brand-primary underline">
                        Directions
                      </a>
                    </>
                  )}
                </dd>
              </>
            )}
            {socials.length > 0 && (
              <>
                <dt className="font-medium text-text-primary">Follow us</dt>
                <dd className="flex gap-4">
                  {socials.map(([label, href]) => (
                    <a key={label} href={href} rel="noopener" target="_blank" className="text-brand-primary underline">
                      {label}
                    </a>
                  ))}
                </dd>
              </>
            )}
          </dl>
        </>
      )}
    </section>
  );
}
