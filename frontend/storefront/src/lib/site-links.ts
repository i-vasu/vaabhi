/**
 * Site navigation content: the header drawer menu and the footer columns. Edit here; a CMS can take over later.
 * Plain [label, href] lists, so this file is excluded from Sonar's copy-paste detection (see sonar-project.properties).
 */
export type NavLink = readonly [label: string, href: string];

export const menuLinks: readonly NavLink[] = [
  ["Best sellers", "/bestsellers"],
  ["New arrivals", "/new"],
  ["Pre Order", "/pre-order"],
  ["Collections", "/collections"],
  ["Support", "/support"],
  ["Blogs", "/blog"],
];

export const footerColumns: readonly { title: string; links: readonly NavLink[] }[] = [
  {
    title: "Shop",
    links: [
      ["Sarees", "/c/sarees"],
      ["Lehengas", "/c/lehengas"],
      ["Kurta sets", "/c/kurta-sets"],
      ["New arrivals", "/new"],
      ["Best sellers", "/bestsellers"],
    ],
  },
  {
    title: "Help",
    links: [
      ["Track order", "/track"],
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
      ["Returns & exchanges", "/returns"],
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
