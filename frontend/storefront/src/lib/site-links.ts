/**
 * Site navigation. The drawer menu lists pages the storefront always has; the footer adds the categories and the
 * content pages published in the dashboard (Storefront → Pages), so it never links to a page that is not there.
 * Plain [label, href] lists, so this file is excluded from Sonar's copy-paste detection (see sonar-project.properties).
 */
import type { ContentPageLink } from "@vaabhi/api-client";

export type NavLink = readonly [label: string, href: string];

export const menuLinks: readonly NavLink[] = [
  ["Best sellers", "/bestsellers"],
  ["New arrivals", "/new"],
  ["Customise", "/customise"],
  ["Collections", "/collections"],
  ["Blog", "/blog"],
  ["Contact us", "/contact"],
];

/** Where a content page lives: policies under /policies, blog posts under /blog, the rest at the top level. */
export function pageHref(page: Pick<ContentPageLink, "slug" | "section">): string {
  if (page.section === "POLICY") return `/policies/${page.slug}`;
  if (page.section === "BLOG") return `/blog/${page.slug}`;
  return `/${page.slug}`;
}

/** The website page the shop writes about custom orders, shown on /customise when published. */
export const CUSTOMISE_PAGE = "customise";

/** The end of the footer's Shop column, after the shop's categories (loaded from the API in site-footer.tsx). */
export const footerShopListings: readonly NavLink[] = [
  ["New arrivals", "/new"],
  ["Best sellers", "/bestsellers"],
];

/**
 * The rest of the footer, as the shop designed it. The help, policy and about links are website pages written in the
 * dashboard (Storefront → Website pages, the suggested pages use these same addresses); each shows once published.
 */
export const footerColumns: readonly { title: string; links: readonly NavLink[] }[] = [
  {
    title: "Help",
    links: [
      ["Track order", "/account/orders"],
      ["Size guide", "/size-guide"],
      ["Saree care guide", "/care-guide"],
      ["FAQs", "/faq"],
      ["Contact us", "/contact"],
    ],
  },
  {
    title: "Policies",
    links: [
      ["Shipping", "/policies/shipping"],
      ["Returns & exchanges", "/policies/returns"],
      ["Cancellations", "/policies/cancellation"],
      ["Privacy", "/policies/privacy"],
      ["Terms of use", "/policies/terms"],
    ],
  },
  {
    title: "About",
    links: [
      ["Our story", "/about"],
      ["Meet the weavers", "/weavers"],
      ["Blog", "/blog"],
    ],
  },
];
