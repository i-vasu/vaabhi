import type { InstagramReel } from "@vaabhi/api-client";
import { SectionHeader } from "@vaabhi/ui";
import { mediaFileUrl } from "@/lib/listing";
import { ReelTile } from "./reel-tile";

/**
 * "On Instagram": the best reels from the shop's account, as cover images copied to our media domain (small JPEGs,
 * lazy-loaded). The row scrolls right-to-left — the best reel sits at the right and scrolling left reveals the
 * rest. On hover a reel's MP4 plays over its cover; a tap opens the reel on Instagram (the app on phones).
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
      <ul dir="rtl" className="-mx-4 flex snap-x snap-mandatory gap-3 overflow-x-auto px-4 pb-1">
        {shown.map((reel) => (
          <li key={reel.id} dir="ltr" className="w-[42%] shrink-0 snap-start sm:w-[28%] md:w-[22%] lg:w-[18%]">
            <ReelTile permalink={reel.permalink} cover={reel.cover} video={reel.video} />
          </li>
        ))}
      </ul>
    </section>
  );
}
