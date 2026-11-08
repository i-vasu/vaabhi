import type { MetadataRoute } from "next";
import { siteUrl } from "@/lib/site";

export const dynamic = "force-dynamic";

/** Shop pages are open to search engines; the bag, checkout, account and API are not worth crawling. */
export default function robots(): MetadataRoute.Robots {
  return {
    rules: {
      userAgent: "*",
      allow: "/",
      disallow: ["/api/", "/bag", "/checkout", "/account", "/wishlist", "/login", "/signup"],
    },
    sitemap: `${siteUrl()}/sitemap.xml`,
  };
}
