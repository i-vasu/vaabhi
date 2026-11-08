import type { Metadata } from "next";
import { Cormorant_Garamond, Inter } from "next/font/google";
import { Faro, appVersion } from "@vaabhi/observability";
import { MediaBaseProvider } from "@/components/media-base";
import { ShopWhatsApp } from "@/components/shop-whatsapp";
import { SiteFooter } from "@/components/site-footer";
import { serverMediaBase } from "@/lib/listing";
import { siteUrl } from "@/lib/site";
import { SiteHeader } from "@/components/site-header";
import "./globals.css";

const cormorant = Cormorant_Garamond({
  subsets: ["latin"],
  weight: ["600"],
  variable: "--font-cormorant",
  display: "swap",
});
const inter = Inter({ subsets: ["latin"], variable: "--font-inter", display: "swap" });

/** A function, so SITE_URL is read when the server runs rather than when the image is built. */
export function generateMetadata(): Metadata {
  return {
    // Makes the pages' relative canonical and share-image URLs absolute.
    metadataBase: new URL(siteUrl()),
    title: { default: "Vaabhi · Handwoven sarees, lehengas & kurta sets", template: "%s · Vaabhi" },
    description:
      "Handwoven Banarasi, Kanjeevaram and Chanderi sarees, lehengas, kurta sets, salwar suits and dupattas. " +
      "Free shipping above ₹1,999, cash on delivery and 7-day easy returns across India.",
    // Which release is live: view source → <meta name="app-version">.
    other: { "app-version": appVersion().label },
  };
}

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en" className={`${cormorant.variable} ${inter.variable}`}>
      <body className="min-h-screen">
        <Faro appName="vaabhi-storefront" />
        <MediaBaseProvider base={serverMediaBase()}>
          <SiteHeader />
          <main>{children}</main>
          <SiteFooter />
          <ShopWhatsApp />
        </MediaBaseProvider>
      </body>
    </html>
  );
}
