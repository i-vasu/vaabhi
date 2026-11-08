/*
 * Colour placeholders for the home page, shown where the admin has not uploaded a photo yet (same colours as the
 * approved Figma design). The words and photos themselves come from the API (Storefront banners, categories).
 */

/** Placeholder photo colours per category slug, until the category has a photo. */
export const categoryColors: Record<string, [string, string]> = {
  sarees: ["#8E4450", "#4E1A24"],
  lehengas: ["#C2185B", "#6E1036"],
  "kurta-sets": ["#5E6B3A", "#38401F"],
  "salwar-suits": ["#2E5E6E", "#183842"],
  dupattas: ["#C2A878", "#7D6640"],
};
export const fallbackCategoryColors: [string, string] = ["#C9BFB2", "#8C8178"];

/** Background and text colour for occasion tiles without a photo, in turn. */
export const occasionTones: readonly { colors: [string, string]; text: "light" | "olive" | "dark" }[] = [
  { colors: ["#6E2A35", "#3A1218"], text: "light" },
  { colors: ["#A88B5A", "#5E4A2A"], text: "light" },
  { colors: ["#E8EBDC", "#C9CFAE"], text: "olive" },
  { colors: ["#F2ECE3", "#DCD2C2"], text: "dark" },
];
