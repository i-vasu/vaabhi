import type { InstagramReel } from "@vaabhi/api-client";
import { SectionHeader } from "@vaabhi/ui";
import { mediaFileUrl } from "@/lib/listing";
import { ReelCarousel } from "./reel-carousel";

/**
 * "On Instagram": the best reels from the shop's account, as cover images copied to our media domain (small JPEGs,
 * lazy-loaded), in a centred carousel: the reel in the middle plays on its own, its neighbours peek in at the sides.
 */
export function InstagramReels({
  reels,
  profileUrl,
}: Readonly<{ reels: InstagramReel[]; profileUrl?: string | null }>) {
  const shown = reels.slice(0, 6).flatMap((reel) => {
    const cover = mediaFileUrl(reel.imageKey);
    if (!cover) return [];
    const video = mediaFileUrl(reel.videoKey);
    return [{ id: reel.id, permalink: reel.permalink, cover, video }];
  });
  if (shown.length === 0) return null;

  return (
    <section aria-labelledby="instagram-title" className="mx-auto flex w-full max-w-6xl flex-col gap-4 px-4 py-8">
      <SectionHeader
        id="instagram-title"
        title="On Instagram"
        action={profileUrl ? { label: "Follow us", href: profileUrl } : undefined}
      />
      <div className="-mx-4">
        <ReelCarousel reels={shown} />
      </div>
    </section>
  );
}
