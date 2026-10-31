import type { Metadata } from "next";
import Link from "next/link";
import { Faro } from "@/components/faro";
import { appVersion } from "@/lib/version";
import "./globals.css";

export const metadata: Metadata = {
  title: { default: "Vaabhi Admin", template: "%s · Vaabhi Admin" },
  robots: { index: false, follow: false },
};

const nav = [
  { href: "/", label: "Dashboard" },
  { href: "/login/", label: "Sign in" },
];

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body className="min-h-screen antialiased">
        <Faro appName="vaabhi-admin" />
        <div className="flex min-h-screen">
          <aside className="flex w-56 shrink-0 flex-col border-r border-neutral-200 bg-neutral-50 p-4">
            <p className="mb-6 font-semibold tracking-wide">VAABHI ADMIN</p>
            <nav className="flex flex-col gap-1 text-sm">
              {nav.map((item) => (
                <Link key={item.href} href={item.href} className="rounded px-2 py-1.5 hover:bg-neutral-200">
                  {item.label}
                </Link>
              ))}
            </nav>
            <p className="mt-auto pt-6 text-xs text-neutral-500" data-testid="app-version">
              {appVersion().label}
            </p>
          </aside>
          <main className="flex-1 p-8">{children}</main>
        </div>
      </body>
    </html>
  );
}
