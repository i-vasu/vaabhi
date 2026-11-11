import type { Permission, StaffProfile } from "@vaabhi/api-client";
import {
  ActivityIcon,
  BadgeIcon,
  BagIcon,
  BankIcon,
  BellIcon,
  BoardIcon,
  CalendarIcon,
  ChatIcon,
  DocumentIcon,
  HelpIcon,
  ImageIcon,
  HomeIcon,
  InboxIcon,
  LayoutIcon,
  LockIcon,
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
  /** The area permission needed to see it; "OWNER" for the owner alone; none for everyone. */
  permission?: Permission | "OWNER";
};

/**
 * Main destinations: the sidebar on wide screens and the bottom bar on phones. Each phase adds its pages here
 * (Orders, Work, Customers…); everything else is reached from More.
 */
export const primaryNav: NavItem[] = [
  { href: "/", label: "Home", icon: HomeIcon, description: "Today at the shop" },
  {
    href: "/orders/",
    label: "Orders",
    icon: BagIcon,
    description: "Shop and website orders: book, ship, deliver and take payments",
    permission: "ORDERS",
  },
  {
    href: "/workshop/",
    label: "Work",
    icon: BoardIcon,
    description: "Work board, stage load and who does what",
    permission: "WORKSHOP",
  },
  {
    href: "/customers/",
    label: "Customers",
    icon: UsersIcon,
    description: "Contacts, measurements and history",
    permission: "ORDERS",
  },
];

const byHref = (href: string) => primaryNav.find((item) => item.href === href)!;

const products: NavItem = {
  href: "/products/",
  label: "Products",
  icon: TagIcon,
  description: "Catalogue, prices and photos",
  permission: "CATALOGUE",
};

export const moreNav: NavItem = { href: "/more/", label: "More", icon: MoreIcon };

/** Areas of the settings hub. Everyone may read settings; changing them needs SETTINGS. */
export const settingsPages: NavItem[] = [
  {
    href: "/settings/shop/",
    label: "Shop profile",
    icon: StoreIcon,
    description: "Name, logo, address, country, currency, hours",
    permission: "SETTINGS",
  },
  {
    href: "/settings/orders/",
    label: "Orders & production",
    icon: TruckIcon,
    description: "Delivery charges, stages, trial date, garments",
    permission: "SETTINGS",
  },
  {
    href: "/settings/billing/",
    label: "Billing & tax",
    icon: ReceiptIcon,
    description: "GST, numbering, bank details, terms",
    permission: "SETTINGS",
  },
  {
    href: "/settings/printing/",
    label: "Printing & messages",
    icon: PrinterIcon,
    description: "Print formats, form layouts, messages, watermark",
    permission: "SETTINGS",
  },
  {
    href: "/settings/holidays/",
    label: "Holidays & weekly off",
    icon: CalendarIcon,
    description: "Days the shop is closed",
    permission: "SETTINGS",
  },
  {
    href: "/settings/notifications/",
    label: "Notifications",
    icon: BellIcon,
    description: "Which alerts reach your phone",
    permission: "SETTINGS",
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
        permission: "WORKSHOP",
      },
      byHref("/customers/"),
      {
        href: "/alterations/",
        label: "Alterations",
        icon: RulerIcon,
        description: "Fit fixes, length and repairs",
        permission: "ORDERS",
      },
      {
        href: "/price-list/",
        label: "Price list",
        icon: TagIcon,
        description: "Stitching prices and piece rates",
        permission: "ORDERS",
      },
    ],
  },
  {
    title: "Money",
    items: [
      {
        href: "/money/",
        label: "Money",
        icon: BankIcon,
        description: "Money in and out this month, what is owed",
        permission: "MONEY",
      },
      {
        href: "/money/dues/",
        label: "Dues",
        icon: BankIcon,
        description: "Who owes what, oldest first",
        permission: "MONEY",
      },
      {
        href: "/money/invoices/",
        label: "Invoices",
        icon: DocumentIcon,
        description: "Tax invoices and GST",
        permission: "MONEY",
      },
      {
        href: "/money/expenses/",
        label: "Expenses",
        icon: ReceiptIcon,
        description: "Rent, salaries, material, bills",
        permission: "MONEY",
      },
      {
        href: "/money/ledger/",
        label: "Ledger",
        icon: ActivityIcon,
        description: "Every rupee in and out",
        permission: "MONEY",
      },
    ],
  },
  {
    title: "Staff",
    items: [
      {
        href: "/staff/",
        label: "Staff",
        icon: UsersIcon,
        description: "People, pay, shifts and sign-ins",
        permission: "STAFF",
      },
      {
        href: "/staff/attendance/",
        label: "Attendance",
        icon: CalendarIcon,
        description: "Mark a day, or see everyone's month",
        permission: "STAFF",
      },
      {
        href: "/staff/requests/",
        label: "Requests",
        icon: InboxIcon,
        description: "Leave, permission, off days, advances",
        permission: "STAFF",
      },
      {
        href: "/payroll/",
        label: "Payroll",
        icon: BankIcon,
        description: "Payslips, finalise and pay a month",
        permission: "PAYROLL",
      },
      {
        href: "/payroll/production/",
        label: "Production ledger",
        icon: ActivityIcon,
        description: "Stages each person finished, at piece rates",
        permission: "PAYROLL",
      },
      {
        href: "/staff/roles/",
        label: "Roles & permissions",
        icon: LockIcon,
        description: "What managers and staff may do",
        permission: "OWNER",
      },
      { href: "/requests/", label: "My requests", icon: BadgeIcon, description: "Ask for leave or an advance" },
    ],
  },
  {
    title: "Stock & marketing",
    items: [
      {
        href: "/stock/",
        label: "Stock",
        icon: TagIcon,
        description: "Cloth, lining and material in and out",
        permission: "STOCK",
      },
      {
        href: "/coupons/",
        label: "Coupons",
        icon: BadgeIcon,
        description: "Discount codes for New order",
        permission: "MARKETING",
      },
      {
        href: "/rewards/",
        label: "Rewards",
        icon: BadgeIcon,
        description: "Points customers earn on delivered orders",
        permission: "MARKETING",
      },
      {
        href: "/design/",
        label: "Design",
        icon: ImageIcon,
        description: "Design ideas and finished garments",
        permission: "MARKETING",
      },
      {
        href: "/messages/",
        label: "Messages",
        icon: ChatIcon,
        description: "WhatsApp messages sent to customers",
        permission: "ORDERS",
      },
    ],
  },
  {
    title: "Reports",
    items: [
      {
        href: "/reports/",
        label: "Reports",
        icon: ActivityIcon,
        description: "Sales, GST, orders, workshop, staff, stock; CSV export",
        permission: "REPORTS",
      },
    ],
  },
  {
    title: "Catalogue",
    items: [
      products,
      {
        href: "/storefront/",
        label: "Storefront",
        icon: LayoutIcon,
        description: "Home page banners, occasions and category photos",
        permission: "CATALOGUE",
      },
      {
        href: "/storefront/pages/",
        label: "Website pages",
        icon: DocumentIcon,
        description: "Shipping, returns, size guide, about: the website footer pages",
        permission: "CATALOGUE",
      },
    ],
  },
  {
    title: "Updates",
    items: [
      { href: "/notifications/", label: "Notifications", icon: BellIcon, description: "Order updates and alerts" },
      {
        href: "/activity/",
        label: "Activity log",
        icon: ActivityIcon,
        description: "What happened and who did it",
        permission: "REPORTS",
      },
    ],
  },
  {
    title: "Dashboards",
    items: [
      {
        href: "https://dashboard.razorpay.com/",
        label: "Razorpay",
        icon: BankIcon,
        description: "Payments, refunds and settlements",
        permission: "MONEY",
      },
      {
        href: "https://app.shiprocket.in/",
        label: "Shiprocket",
        icon: TruckIcon,
        description: "Shipments, pickups and courier tracking",
        permission: "ORDERS",
      },
      {
        href: "https://dash.cloudflare.com/",
        label: "Cloudflare",
        icon: LayoutIcon,
        description: "Website domain, photo storage (R2) and hosting",
        permission: "OWNER",
      },
    ],
  },
  {
    title: "Shop",
    items: [
      {
        href: "/calendar/",
        label: "Calendar",
        icon: CalendarIcon,
        description: "Your Google Calendar, inside the app",
      },
      { href: "/settings/", label: "Settings", icon: SettingsIcon, description: "Shop, billing, printing, account" },
      { href: "/help/", label: "Help & support", icon: HelpIcon, description: "How things work, getting help" },
    ],
  },
];

/**
 * Whether the signed-in person may see the item. Only hides what the API would refuse anyway; with nobody known
 * (outside the app frame) everything shows.
 */
export function canSee(item: NavItem, staff: Pick<StaffProfile, "role" | "permissions"> | undefined): boolean {
  return !item.permission || hasPermission(staff, item.permission);
}

/** Whether the person may use an area ("OWNER": the owner alone); true with nobody known. */
export function hasPermission(
  staff: Pick<StaffProfile, "role" | "permissions"> | undefined,
  permission: Permission | "OWNER",
): boolean {
  if (!staff || staff.role === "OWNER") return true;
  return permission !== "OWNER" && staff.permissions.includes(permission);
}

/** Every page reachable from search, without duplicates, limited to what the person may see. */
export function searchableNav(staff?: Pick<StaffProfile, "role" | "permissions">): NavItem[] {
  const seen = new Set<string>();
  return [...primaryNav, ...moreSections.flatMap((section) => section.items), ...settingsPages].filter(
    (item) => canSee(item, staff) && (seen.has(item.href) ? false : (seen.add(item.href), true)),
  );
}

/** True when `pathname` is the item's page or one below it (Home matches only itself). */
export function isActive(item: NavItem, pathname: string): boolean {
  const path = pathname.endsWith("/") ? pathname : `${pathname}/`;
  return item.href === "/" ? path === "/" : path.startsWith(item.href);
}

const allPages = (): NavItem[] => [...primaryNav, products, ...moreSections.flatMap((section) => section.items)];

/** Shortcuts on the home page, in this order; each still needs its own permission. */
export const quickActions: NavItem[] = [
  "/stock/",
  "/money/expenses/",
  "/design/",
  "/storefront/",
  "/activity/",
  "/workshop/",
]
  .map((href) => allPages().find((item) => item.href === href))
  .filter((item): item is NavItem => item !== undefined);
