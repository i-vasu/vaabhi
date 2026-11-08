import type { Metadata, Viewport } from "next";
import { Faro } from "@vaabhi/observability";
import { AppShell } from "@/components/app-shell";
import "./globals.css";

export const metadata: Metadata = {
  title: { default: "Vaabhi Admin", template: "%s · Vaabhi Admin" },
  robots: { index: false, follow: false },
};

export const viewport: Viewport = { themeColor: "#6e2a35", colorScheme: "light" };

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body className="min-h-screen antialiased">
        <Faro appName="vaabhi-admin" />
        <AppShell>{children}</AppShell>
      </body>
    </html>
  );
}
