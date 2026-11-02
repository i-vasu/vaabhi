import {
  BagIcon,
  BellIcon,
  BoardIcon,
  CalendarIcon,
  HomeIcon,
  MoreIcon,
  PhoneIcon,
  PrinterIcon,
  ReceiptIcon,
  RulerIcon,
  SettingsIcon,
  StoreIcon,
  TagIcon,
  TruckIcon,
  UsersIcon,
  type Icon,
} from "@/components/icons";

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
  { href: "/orders/", label: "Orders", icon: BagIcon, description: "Book, track, deliver and take payments" },
  { href: "/workshop/", label: "Work", icon: BoardIcon, description: "Work board, stage load and who does what" },
  { href: "/customers/", label: "Customers", icon: UsersIcon, description: "Contacts, measurements and history" },
];

const byHref = (href: string) => primaryNav.find((item) => item.href === href)!;

const products: NavItem = {
  href: "/products/",
  label: "Products",
  icon: TagIcon,
  description: "Catalogue, prices and photos",
};

export const moreNav: NavItem = { href: "/more/", label: "More", icon: MoreIcon };

/** Areas of the settings hub. */
export const settingsPages: NavItem[] = [
  {
    href: "/settings/shop/",
    label: "Shop profile",
    icon: StoreIcon,
    description: "Name, logo, address, country, currency, hours",
  },
  {
    href: "/settings/orders/",
    label: "Orders & production",
    icon: TruckIcon,
    description: "Delivery charges, stages, trial date, garments",
  },
  {
    href: "/settings/billing/",
    label: "Billing & tax",
    icon: ReceiptIcon,
    description: "GST, numbering, bank details, terms",
  },
  {
    href: "/settings/printing/",
    label: "Printing & messages",
    icon: PrinterIcon,
    description: "Print formats, form layouts, messages, watermark",
  },
  {
    href: "/settings/holidays/",
    label: "Holidays & weekly off",
    icon: CalendarIcon,
    description: "Days the shop is closed",
  },
  {
    href: "/settings/notifications/",
    label: "Notifications",
    icon: BellIcon,
    description: "Which alerts reach your phone",
  },
  {
    href: "/settings/account/",
    label: "App & account",
    icon: PhoneIcon,
    description: "Password, sign out, app version",
  },
];

/** Groups on the More page. */
export const moreSections: { title: string; items: NavItem[] }[] = [
  {
    title: "Tailoring",
    items: [
      byHref("/orders/"),
      byHref("/workshop/"),
      {
        href: "/workshop/board/",
        label: "Work board",
        icon: BoardIcon,
        description: "Give out and move work by stage",
      },
      {
        href: "/workshop/staff/",
        label: "Who works which stage",
        icon: UsersIcon,
        description: "Stages each person may be given",
      },
      byHref("/customers/"),
      { href: "/alterations/", label: "Alterations", icon: RulerIcon, description: "Fit fixes, length and repairs" },
      { href: "/price-list/", label: "Price list", icon: TagIcon, description: "Stitching prices and piece rates" },
    ],
  },
  {
    title: "Catalogue",
    items: [products],
  },
  {
    title: "Updates",
    items: [
      { href: "/notifications/", label: "Notifications", icon: BellIcon, description: "Order updates and alerts" },
    ],
  },
  {
    title: "Shop",
    items: [
      { href: "/settings/", label: "Settings", icon: SettingsIcon, description: "Shop, billing, printing, account" },
    ],
  },
];

/** Every page reachable from search, without duplicates. */
export function searchableNav(): NavItem[] {
  const seen = new Set<string>();
  return [...primaryNav, ...moreSections.flatMap((section) => section.items), ...settingsPages].filter((item) =>
    seen.has(item.href) ? false : (seen.add(item.href), true),
  );
}

/** True when `pathname` is the item's page or one below it (Home matches only itself). */
export function isActive(item: NavItem, pathname: string): boolean {
  const path = pathname.endsWith("/") ? pathname : `${pathname}/`;
  return item.href === "/" ? path === "/" : path.startsWith(item.href);
}
