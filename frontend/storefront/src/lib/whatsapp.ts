/** A wa.me chat link with a starting message; `number` is digits with the country code (from the shop contact). */
export function whatsappLink(number: string, text: string): string {
  return `https://wa.me/${number}?text=${encodeURIComponent(text)}`;
}

/** The shop's social profiles that are set, as [label, url]. */
export function socialLinks(shop: {
  instagram?: string | null;
  facebook?: string | null;
  youtube?: string | null;
}): [label: string, href: string][] {
  const all: [string, string | null | undefined][] = [
    ["Instagram", shop.instagram],
    ["Facebook", shop.facebook],
    ["YouTube", shop.youtube],
  ];
  return all.flatMap(([label, href]) => (href ? [[label, href] as [string, string]] : []));
}
