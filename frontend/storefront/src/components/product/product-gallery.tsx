"use client";

import { useRef, useState } from "react";
import type { Media } from "@vaabhi/api-client";
import { ChevronRightIcon, cn } from "@vaabhi/ui";
import { mediaUrl, shade } from "@/lib/listing";

const SIZES = "(min-width: 900px) 55vw, 100vw";

function sources(key: string) {
  const src = mediaUrl(key, 800);
  if (!src) return undefined;
  return { src, srcSet: ([400, 800, 1600] as const).map((w) => `${mediaUrl(key, w)} ${w}w`).join(", ") };
}

/**
 * Swipeable photos (CSS scroll snap, so touch works without JavaScript) with arrows, a counter and thumbnails.
 * Without photos, the colour stands in, as on listing cards.
 */
export function ProductGallery({
  photos,
  name,
  hex,
  overlay,
}: Readonly<{ photos: Media[]; name: string; hex: string; overlay?: React.ReactNode }>) {
  const track = useRef<HTMLDivElement>(null);
  const [index, setIndex] = useState(0);
  const slides: Array<{ key: string; alt: string; src?: ReturnType<typeof sources> }> = photos.length
    ? photos.map((p) => ({ key: p.key, alt: p.alt, src: sources(p.key) }))
    : [{ key: "placeholder", alt: name }];
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
            <div
              key={slide.key}
              role="group"
              aria-roledescription="slide"
              aria-label={`${i + 1} of ${count}`}
              className="h-full w-full shrink-0 snap-start"
            >
              {slide.src ? (
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
                <div
                  role="img"
                  aria-label={slide.alt}
                  className="h-full w-full"
                  style={{ backgroundImage: `linear-gradient(180deg, ${hex}, ${shade(hex)})` }}
                />
              )}
            </div>
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
              aria-label={`Show photo ${i + 1}`}
              aria-current={i === index}
              onClick={() => go(i)}
              className={cn(
                "h-20 w-[60px] shrink-0 overflow-hidden rounded-sm border-2",
                i === index ? "border-brand-primary" : "border-transparent",
              )}
            >
              {slide.src ? (
                // eslint-disable-next-line @next/next/no-img-element -- thumbnail rendition
                <img src={mediaUrl(slide.key, 400)} alt="" className="h-full w-full object-cover" />
              ) : null}
            </button>
          ))}
        </div>
      )}
    </section>
  );
}
