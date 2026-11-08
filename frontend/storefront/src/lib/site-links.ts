/**
 * Site navigation. The drawer menu lists pages the storefront always has; the footer adds the categories and the
 * content pages published in the dashboard (Storefront → Pages), so it never links to a page that is not there.
 * Plain [label, href] lists, so this file is excluded from Sonar's copy-paste detection (see sonar-project.properties).
 */
import type { Category, ContentPageLink, PageSection } from "@vaabhi/api-client";

export type NavLink = readonly [label: string, href: string];

export const menuLinks: readonly NavLink[] = [
  ["Best sellers", "/bestsellers"],
  ["New arrivals", "/new"],
  ["Search", "/search"],
  ["Your orders", "/account/orders"],
  ["Contact us", "/contact"],
];

/** Where a content page lives: policies under /policies, the rest at the top level. */
export function pageHref(page: Pick<ContentPageLink, "slug" | "section">): string {
  return page.section === "POLICY" ? `/policies/${page.slug}` : `/${page.slug}`;
}

/** The footer columns; a column with no links is left out. */
export function footerColumns(
  categories: readonly Pick<Category, "name" | "slug">[],
  pages: readonly ContentPageLink[],
): { title: string; links: NavLink[] }[] {
  const section = (s: PageSection): NavLink[] =>
    pages.filter((p) => p.section === s).map((p) => [p.title, pageHref(p)] as const);
  const columns: { title: string; links: NavLink[] }[] = [
    {
      title: "Shop",
      links: [
        ...categories.map((c): NavLink => [c.name, `/c/${c.slug}`]),
        ["New arrivals", "/new"],
        ["Best sellers", "/bestsellers"],
      ],
    },
    {
      title: "Help",
      links: [["Track your order", "/account/orders"], ["Contact us", "/contact"], ...section("HELP")],
    },
    { title: "Policies", links: section("POLICY") },
    { title: "About", links: section("ABOUT") },
  ];
  return columns.filter((column) => column.links.length > 0);
}
