import {
  ApiError,
  type Banner,
  type BannerForm,
  type BannerSlot,
  type ContentPageForm,
  type PageSection,
} from "@vaabhi/api-client";
import { api } from "./api";
import { resizeToWebp, type Rendition } from "./image-resize";

type Resize = (file: Blob, widths: readonly number[]) => Promise<Rendition[]>;

/** Where a page is listed: a website footer column, or the blog. */
export const PAGE_SECTIONS: { section: PageSection; title: string }[] = [
  { section: "HELP", title: "Help" },
  { section: "POLICY", title: "Policies" },
  { section: "ABOUT", title: "About" },
  { section: "BLOG", title: "Blog" },
];

/** Where the page lives on the website (same rule as the storefront): policies and blog posts have their own paths. */
export function pagePath(page: Pick<ContentPageForm, "slug" | "section">): string {
  if (page.section === "POLICY") return `/policies/${page.slug}`;
  if (page.section === "BLOG") return `/blog/${page.slug}`;
  return `/${page.slug}`;
}

/** "Returns & exchanges" → "returns-exchanges"; `typing` keeps a trailing dash so the next word can follow. */
export function slugify(text: string, typing = false): string {
  const slug = text
    .toLowerCase()
    .normalize("NFKD")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-/, "")
    .slice(0, 60);
  // Runs are already one dash, so at most one trails.
  return typing ? slug : slug.replace(/-$/, "");
}

/** Pages shoppers look for before they buy; offered until each one exists. Names only — the shop writes the text. */
export const STARTER_PAGES: Pick<ContentPageForm, "slug" | "section" | "title">[] = [
  { slug: "shipping", section: "POLICY", title: "Shipping" },
  { slug: "returns", section: "POLICY", title: "Returns & exchanges" },
  { slug: "cancellation", section: "POLICY", title: "Cancellations" },
  { slug: "privacy", section: "POLICY", title: "Privacy" },
  { slug: "terms", section: "POLICY", title: "Terms of use" },
  { slug: "size-guide", section: "HELP", title: "Size guide" },
  { slug: "care-guide", section: "HELP", title: "Garment & saree care" },
  { slug: "faq", section: "HELP", title: "FAQs" },
  // Shown on the website's Customise page (menu); the slug must stay "customise".
  { slug: "customise", section: "HELP", title: "Customise" },
  { slug: "about", section: "ABOUT", title: "Our story" },
  { slug: "weavers", section: "ABOUT", title: "Meet the weavers" },
];

/** What each home-page slot is for, as the storefront shows it. */
export const SLOTS: { slot: BannerSlot; title: string; hint: string }[] = [
  {
    slot: "HERO",
    title: "Hero slides",
    hint: "The big photo at the top of the home page. The first live slide shows; wide photos work best.",
  },
  {
    slot: "OCCASION",
    title: "Shop by occasion",
    hint: "One tile per occasion (Wedding, Festive…). The title is the tile's name; the link opens its products.",
  },
  {
    slot: "CRAFT",
    title: "The craft",
    hint: "The photo and words of the craft section. Only the first live one shows.",
  },
];

/** "Live", "Off", or when it starts/ended, for the row's status line. */
export function bannerStatus(banner: Pick<Banner, "live" | "active" | "startsOn" | "endsOn">, today: string): string {
  if (banner.live) return banner.endsOn ? `Live until ${banner.endsOn}` : "Live";
  if (!banner.active) return "Off";
  if (banner.startsOn && banner.startsOn > today) return `Starts ${banner.startsOn}`;
  if (banner.endsOn && banner.endsOn < today) return `Ended ${banner.endsOn}`;
  return "Off";
}

export function emptyBannerForm(slot: BannerSlot): BannerForm {
  return {
    slot,
    eyebrow: null,
    title: "",
    subtitle: null,
    ctaLabel: null,
    link: null,
    imageKey: "",
    altText: null,
    active: true,
    startsOn: null,
    endsOn: null,
  };
}

export function formOf(banner: Banner): BannerForm {
  const { slot, eyebrow, title, subtitle, ctaLabel, link, imageKey, altText, active, startsOn, endsOn } = banner;
  return { slot, eyebrow, title, subtitle, ctaLabel, link, imageKey, altText, active, startsOn, endsOn };
}

/** Moves the banner one place up (-1) or down (+1) and returns the slot's new id order. */
export function moved(ids: number[], id: number, step: -1 | 1): number[] {
  const from = ids.indexOf(id);
  const to = from + step;
  if (from < 0 || to < 0 || to >= ids.length) return ids;
  const next = [...ids];
  [next[from], next[to]] = [next[to]!, next[from]!];
  return next;
}

/**
 * Resizes a storefront photo to 400/800/1600 px WebPs in the browser, PUTs each straight to R2 and returns the key
 * to save on the banner or category (the API checks every width arrived).
 */
export async function uploadSiteImage(file: Blob, resize: Resize = resizeToWebp): Promise<string> {
  const upload = await api.admin.storefront.startUpload();
  const renditions = await resize(
    file,
    upload.renditions.map((r) => r.width),
  );
  for (const target of upload.renditions) {
    const rendition = renditions.find((r) => r.width === target.width);
    if (!rendition) throw new Error(`No ${target.width} px version was made`);
    const response = await fetch(target.uploadUrl, {
      method: "PUT",
      body: rendition.blob,
      headers: { "Content-Type": "image/webp", "Cache-Control": "public, max-age=31536000, immutable" },
    });
    if (!response.ok) throw new ApiError(response.status, `Uploading the ${target.width} px version failed`);
  }
  return upload.key;
}
