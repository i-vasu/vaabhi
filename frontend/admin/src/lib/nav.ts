import { BellIcon, HomeIcon, MoreIcon, TagIcon, type Icon } from "@/components/icons";

export type NavItem = {
  href: string;
  label: string;
  icon: Icon;
  /** One line shown in the More page and in search results. */
  description?: string;
};

/**
 * Main destinations: the sidebar on wide screens and the bottom bar on phones. Each phase adds its pages here
 * (Orders, Work, Customers…); everything else is reached from More.
 */
export const primaryNav: NavItem[] = [
  { href: "/", label: "Home", icon: HomeIcon, description: "Today at the shop" },
  { href: "/products/", label: "Products", icon: TagIcon, description: "Catalogue, prices and photos" },
];

export const moreNav: NavItem = { href: "/more/", label: "More", icon: MoreIcon };

/** Groups on the More page. */
export const moreSections: { title: string; items: NavItem[] }[] = [
  {
    title: "Catalogue",
    items: [primaryNav[1]!],
  },
  {
    title: "Updates",
    items: [
      { href: "/notifications/", label: "Notifications", icon: BellIcon, description: "Order updates and alerts" },
    ],
  },
];

/** Every page reachable from search, without duplicates. */
export function searchableNav(): NavItem[] {
  const seen = new Set<string>();
  return [...primaryNav, ...moreSections.flatMap((section) => section.items)].filter((item) =>
    seen.has(item.href) ? false : (seen.add(item.href), true),
  );
}

/** True when `pathname` is the item's page or one below it (Home matches only itself). */
export function isActive(item: NavItem, pathname: string): boolean {
  const path = pathname.endsWith("/") ? pathname : `${pathname}/`;
  return item.href === "/" ? path === "/" : path.startsWith(item.href);
}
