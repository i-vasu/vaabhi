import type { BadgeTone } from "@vaabhi/ui";

/*
 * Editorial content for the home page. Products are placeholders until the catalogue module
 * exists; the gradients stand in for photos (same colours as the approved Figma design).
 */

/** Placeholder photo colours per category slug, until category images exist. */
export const categoryColors: Record<string, [string, string]> = {
  sarees: ["#8E4450", "#4E1A24"],
  lehengas: ["#C2185B", "#6E1036"],
  "kurta-sets": ["#5E6B3A", "#38401F"],
  "salwar-suits": ["#2E5E6E", "#183842"],
  dupattas: ["#C2A878", "#7D6640"],
};
export const fallbackCategoryColors: [string, string] = ["#C9BFB2", "#8C8178"];

export const occasions = [
  { label: "Wedding", href: "/occasion/wedding", colors: ["#6E2A35", "#3A1218"], tone: "light" },
  { label: "Festive", href: "/occasion/festive", colors: ["#A88B5A", "#5E4A2A"], tone: "light" },
  { label: "Haldi & Mehendi", href: "/occasion/haldi-mehendi", colors: ["#E8EBDC", "#C9CFAE"], tone: "olive" },
  { label: "Everyday & office", href: "/occasion/everyday", colors: ["#F2ECE3", "#DCD2C2"], tone: "dark" },
] as const;

export type FeaturedProduct = {
  slug: string;
  name: string;
  detail: string;
  price: number;
  mrp?: number;
  badge?: { label: string; tone: BadgeTone };
  colors: [string, string];
};

export const bestsellers: FeaturedProduct[] = [
  {
    slug: "emerald-kanjeevaram-silk-saree",
    name: "Emerald Kanjeevaram silk saree",
    detail: "Pure silk · Zari border",
    price: 12499,
    mrp: 15999,
    badge: { label: "Handloom", tone: "gold" },
    colors: ["#1E6B52", "#0E3B2E"],
  },
  {
    slug: "rani-pink-bridal-lehenga",
    name: "Rani pink bridal lehenga",
    detail: "Raw silk · Hand embroidery",
    price: 18900,
    mrp: 24500,
    badge: { label: "New", tone: "festive" },
    colors: ["#C2185B", "#6E1036"],
  },
  {
    slug: "mehendi-chanderi-kurta-set",
    name: "Mehendi Chanderi kurta set",
    detail: "Chanderi · Gota patti",
    price: 3299,
    mrp: 3999,
    badge: { label: "Handloom", tone: "olive" },
    colors: ["#7A8450", "#4A522A"],
  },
  {
    slug: "ivory-chikankari-anarkali",
    name: "Ivory chikankari anarkali",
    detail: "Georgette · Lucknowi",
    price: 5499,
    mrp: 6999,
    badge: { label: "Bestseller", tone: "gold" },
    colors: ["#EDE6D8", "#C9BBA2"],
  },
];

export const weaves = ["Banarasi", "Kanjeevaram", "Chanderi", "Paithani", "Chikankari"];
