import type { InstagramReel } from "@vaabhi/api-client";
import { SectionHeader } from "@vaabhi/ui";
import { mediaFileUrl } from "@/lib/listing";

/**
 * "On Instagram": the latest reels from the shop's account, as cover images copied to our media domain (small
 * JPEGs, lazy-loaded), so the section costs no Instagram scripts or video until a customer taps one. A tap opens
 * the reel on Instagram (the app on phones). Phones scroll the row sideways; computers show four across.
 */
export function InstagramReels({
  reels,
  profileUrl,
}: Readonly<{ reels: InstagramReel[]; profileUrl?: string | null }>) {
  const shown = reels.flatMap((reel) => {
    const src = mediaFileUrl(reel.imageKey);
    return src ? [{ ...reel, src }] : [];
  });
  if (shown.length === 0) return null;

  return (
    <section aria-labelledby="instagram-title" className="mx-auto flex w-full max-w-6xl flex-col gap-4 px-4 py-8">
      <SectionHeader
        id="instagram-title"
        title="On Instagram"
        action={profileUrl ? { label: "Follow us", href: profileUrl } : undefined}
      />
      <ul className="-mx-4 flex snap-x snap-mandatory gap-3 overflow-x-auto px-4 pb-1 md:mx-0 md:grid md:grid-cols-4 md:overflow-visible md:px-0">
        {shown.map((reel) => (
          <li key={reel.id} className="w-[42%] shrink-0 snap-start sm:w-[30%] md:w-auto">
            <a
              href={reel.permalink}
              target="_blank"
              rel="noopener"
              aria-label={reel.caption ? `Watch on Instagram: ${reel.caption}` : "Watch this reel on Instagram"}
              className="group relative isolate block aspect-[9/16] overflow-hidden rounded-sm bg-bg-muted"
            >
              {/* eslint-disable-next-line @next/next/no-img-element -- a fixed-size JPEG on the media domain */}
              <img
                src={reel.src}
                alt=""
                loading="lazy"
                decoding="async"
                className="absolute inset-0 -z-10 size-full object-cover transition-transform duration-300 group-hover:scale-105"
              />
              <span
                aria-hidden="true"
                className="absolute inset-0 -z-10 bg-gradient-to-t from-black/60 via-transparent to-transparent"
              />
              <span
                aria-hidden="true"
                className="absolute left-1/2 top-1/2 grid size-11 -translate-x-1/2 -translate-y-1/2 place-items-center rounded-full bg-black/40 text-white backdrop-blur-sm"
              >
                <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
                  <path d="M8 5.5v13l11-6.5z" />
                </svg>
              </span>
              {reel.caption && (
                <span className="absolute inset-x-0 bottom-0 line-clamp-2 p-3 text-xs leading-4 text-text-on-brand">
                  {reel.caption}
                </span>
              )}
            </a>
          </li>
        ))}
      </ul>
    </section>
  );
}
