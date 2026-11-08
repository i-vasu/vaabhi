"use client";

import { useRef, useState } from "react";
import type { Media } from "@vaabhi/api-client";
import { ChevronRightIcon, cn } from "@vaabhi/ui";
import { useMediaBase } from "@/components/media-base";
import { mediaUrl, videoUrl } from "@/lib/listing";

const SIZES = "(min-width: 900px) 55vw, 100vw";

function sources(key: string, base: string | undefined) {
  const src = mediaUrl(key, 800, base);
  if (!src) return undefined;
  return {
    src,
    srcSet: ([400, 800, 1600] as const).map((w) => `${mediaUrl(key, w, base)} ${w}w`).join(", "),
    thumb: mediaUrl(key, 400, base),
  };
}

type Slide = { key: string; alt: string; src?: ReturnType<typeof sources>; video?: string };

/**
 * Swipeable photos (CSS scroll snap, so touch works without JavaScript) with arrows, a counter and thumbnails.
 * Videos, if any, come after the first photo, with that photo as their poster. Without photos it says they are
 * coming (no made-up artwork).
 */
export function ProductGallery({
  photos,
  videos = [],
  name,
  overlay,
}: Readonly<{ photos: Media[]; videos?: Media[]; name: string; overlay?: React.ReactNode }>) {
  const track = useRef<HTMLDivElement>(null);
  const [index, setIndex] = useState(0);
  const base = useMediaBase();
  const slides: Slide[] = photos.length
    ? photos.map((p) => ({ key: p.key, alt: p.alt, src: sources(p.key, base) }))
    : [{ key: "placeholder", alt: name }];
  const videoSlides = videos.flatMap((v) => {
    const src = videoUrl(v.key, base);
    return src ? [{ key: v.key, alt: v.alt, video: src }] : [];
  });
  slides.splice(1, 0, ...videoSlides);
  const poster = photos[0] ? mediaUrl(photos[0].key, 800, base) : undefined;
  const count = slides.length;

  const go = (i: number) => {
    const next = (i + count) % count;
    setIndex(next);
    const el = track.current;
    el?.scrollTo?.({ left: next * el.clientWidth, behavior: "smooth" });
  };

  return (
    <section
      aria-label="Photos"
      aria-roledescription="carousel"
      className="flex flex-col gap-2.5 md:sticky md:top-20 md:self-start"
    >
      <div className="relative">
        <div
          ref={track}
          className="flex aspect-[3/4] snap-x snap-mandatory overflow-x-auto md:rounded-md [scrollbar-width:none]"
          onScroll={(e) => {
            const el = e.currentTarget;
            if (el.clientWidth) setIndex(Math.round(el.scrollLeft / el.clientWidth));
          }}
        >
          {slides.map((slide, i) => (
            <figure
              key={slide.key}
              aria-roledescription="slide"
              aria-label={`${i + 1} of ${count}`}
              className="m-0 h-full w-full shrink-0 snap-start"
            >
              {slide.video ? (
                <video
                  src={slide.video}
                  poster={poster}
                  controls
                  playsInline
                  muted
                  loop
                  preload="metadata"
                  aria-label={slide.alt}
                  className="h-full w-full bg-bg-surface object-cover"
                />
              ) : slide.src ? (
                // eslint-disable-next-line @next/next/no-img-element -- R2 serves pre-sized WebP renditions
                <img
                  src={slide.src.src}
                  srcSet={slide.src.srcSet}
                  sizes={SIZES}
                  alt={slide.alt}
                  loading={i === 0 ? "eager" : "lazy"}
                  className="h-full w-full object-cover"
                />
              ) : (
                <div className="grid h-full w-full place-items-center bg-bg-muted text-sm text-text-muted">
                  Photos coming soon
                </div>
              )}
            </figure>
          ))}
        </div>
        {overlay}
        {count > 1 && (
          <>
            <button
              type="button"
              aria-label="Previous photo"
              onClick={() => go(index - 1)}
              className="absolute left-3 top-1/2 grid size-9 -translate-y-1/2 rotate-180 place-items-center rounded-full bg-bg-surface/85"
            >
              <ChevronRightIcon size={20} />
            </button>
            <button
              type="button"
              aria-label="Next photo"
              onClick={() => go(index + 1)}
              className="absolute right-3 top-1/2 grid size-9 -translate-y-1/2 place-items-center rounded-full bg-bg-surface/85"
            >
              <ChevronRightIcon size={20} />
            </button>
            <span className="absolute bottom-4 right-4 rounded-full bg-text-primary/55 px-2 py-0.5 text-xs tabular-nums text-text-on-brand">
              {index + 1} / {count}
            </span>
          </>
        )}
      </div>
      {count > 1 && (
        <div className="flex gap-2 overflow-x-auto px-4 md:px-0">
          {slides.map((slide, i) => (
            <button
              key={slide.key}
              type="button"
              aria-label={
                slide.video
                  ? `Play video ${slides.slice(0, i + 1).filter((s) => s.video).length}`
                  : `Show photo ${slides.slice(0, i + 1).filter((s) => !s.video).length}`
              }
              aria-current={i === index}
              onClick={() => go(i)}
              className={cn(
                "h-20 w-[60px] shrink-0 overflow-hidden rounded-sm border-2",
                i === index ? "border-brand-primary" : "border-transparent",
              )}
            >
              <Thumbnail slide={slide} poster={photos[0] && mediaUrl(photos[0].key, 400, base)} />
            </button>
          ))}
        </div>
      )}
    </section>
  );
}

/** A thumbnail: the photo itself, or the first photo with a play mark for a video. */
function Thumbnail({ slide, poster }: Readonly<{ slide: Slide; poster?: string }>) {
  if (slide.video) {
    return (
      <span className="relative grid h-full w-full place-items-center bg-bg-surface">
        {poster && (
          // eslint-disable-next-line @next/next/no-img-element -- thumbnail rendition
          <img src={poster} alt="" className="absolute inset-0 h-full w-full object-cover opacity-70" />
        )}
        <span aria-hidden="true" className="relative text-lg text-text-primary">
          ▶
        </span>
      </span>
    );
  }
  if (!slide.src) return null;
  // eslint-disable-next-line @next/next/no-img-element -- thumbnail rendition
  return <img src={slide.src.thumb} alt="" className="h-full w-full object-cover" />;
}
