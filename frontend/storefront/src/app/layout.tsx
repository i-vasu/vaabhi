import type { Metadata } from "next";
import { Cormorant_Garamond, Inter } from "next/font/google";
import { Faro, appVersion } from "@vaabhi/observability";
import { SiteFooter } from "@/components/site-footer";
import { SiteHeader } from "@/components/site-header";
import "./globals.css";

const cormorant = Cormorant_Garamond({
  subsets: ["latin"],
  weight: ["600"],
  variable: "--font-cormorant",
  display: "swap",
});
const inter = Inter({ subsets: ["latin"], variable: "--font-inter", display: "swap" });

export const metadata: Metadata = {
  title: { default: "Vaabhi · Handwoven sarees, lehengas & kurta sets", template: "%s · Vaabhi" },
  description:
    "Women's traditional wear: Banarasi, Kanjeevaram and Chanderi, handwoven and ready to ship across India.",
  // Which release is live: view source → <meta name="app-version">.
  other: { "app-version": appVersion().label },
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en" className={`${cormorant.variable} ${inter.variable}`}>
      <body className="min-h-screen">
        <Faro appName="vaabhi-storefront" />
        <SiteHeader />
        <main>{children}</main>
        <SiteFooter />
      </body>
    </html>
  );
}
