/** The storefront's public address, for canonical links, the sitemap and robots.txt (SITE_URL, read on the server). */
export function siteUrl(): string {
  return (process.env.SITE_URL || process.env.NEXT_PUBLIC_SITE_URL || "https://vaabhi.com").replace(/\/$/, "");
}
