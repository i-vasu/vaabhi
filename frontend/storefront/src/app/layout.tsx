import type { Metadata } from "next";
import { Faro } from "@/components/faro";
import "./globals.css";

export const metadata: Metadata = {
  title: { default: "Vaabhi", template: "%s · Vaabhi" },
  description: "Vaabhi — fashion for every occasion.",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body className="min-h-screen antialiased">
        <Faro appName="vaabhi-storefront" />
        <header className="border-b border-neutral-200">
          <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-4">
            <span className="text-xl font-semibold tracking-wide">VAABHI</span>
            <nav className="text-sm text-neutral-600">Cart (0)</nav>
          </div>
        </header>
        <main className="mx-auto max-w-6xl px-4 py-10">{children}</main>
      </body>
    </html>
  );
}
