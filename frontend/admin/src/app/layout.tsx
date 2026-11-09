import type { Metadata, Viewport } from "next";
import { Faro } from "@vaabhi/observability";
import { InstallableApp } from "@vaabhi/ui";
import { AppShell } from "@/components/app-shell";
import "./globals.css";

export const metadata: Metadata = {
  title: { default: "Vaabhi Admin", template: "%s · Vaabhi Admin" },
  robots: { index: false, follow: false },
  manifest: "/manifest.webmanifest",
  icons: { icon: "/icons/icon-192.png", apple: "/icons/apple-touch-icon.png" },
  appleWebApp: { capable: true, title: "Vaabhi Admin", statusBarStyle: "default" },
};

export const viewport: Viewport = { themeColor: "#6e2a35", colorScheme: "light" };

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body className="min-h-screen antialiased">
        <Faro appName="vaabhi-admin" />
        <InstallableApp />
        <AppShell>{children}</AppShell>
      </body>
    </html>
  );
}
