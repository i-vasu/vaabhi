/*
 * Editorial content for the home page; the gradients stand in for photos (same colours as the approved Figma
 * design). Bestsellers come from the API.
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

export const weaves = ["Banarasi", "Kanjeevaram", "Chanderi", "Paithani", "Chikankari"];
