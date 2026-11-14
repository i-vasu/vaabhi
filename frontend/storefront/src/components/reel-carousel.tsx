"use client";

import { useCallback, useEffect, useRef, useState } from "react";

export type Reel = { id: string; permalink: string; cover: string; video?: string };

/**
 * A centred reel carousel: the reel in the middle is large and plays on its own (muted, looping) while the row is on
 * screen; its neighbours peek in smaller at the sides. Swiping snaps the next reel to the centre. Tapping a side reel
 * brings it to the centre; tapping the centre reel opens it on Instagram. A speaker button turns the sound on.
 */
export function ReelCarousel({ reels }: Readonly<{ reels: Reel[] }>) {
  const track = useRef<HTMLUListElement>(null);
  const videos = useRef<(HTMLVideoElement | null)[]>([]);
  const [active, setActive] = useState(0);
  const [visible, setVisible] = useState(false);
  const [muted, setMuted] = useState(true);

  // The reel whose centre is closest to the track's centre is the active one.
  const onScroll = useCallback(() => {
    const el = track.current;
    if (!el) return;
    const middle = el.scrollLeft + el.clientWidth / 2;
    let best = 0;
    let bestDistance = Infinity;
    [...el.children].forEach((child, i) => {
      const item = child as HTMLElement;
      const distance = Math.abs(item.offsetLeft + item.offsetWidth / 2 - middle);
      if (distance < bestDistance) {
        bestDistance = distance;
        best = i;
      }
    });
    setActive(best);
  }, []);

  // Only play while the row is actually on screen.
  useEffect(() => {
    const el = track.current;
    if (!el || typeof IntersectionObserver === "undefined") {
      setVisible(true);
      return;
    }
    const observer = new IntersectionObserver(([entry]) => setVisible(entry?.isIntersecting ?? false), {
      threshold: 0.4,
    });
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    videos.current.forEach((video, i) => {
      if (!video) return;
      if (i === active && visible) {
        video.muted = muted;
        // play() returns undefined in some browsers (and jsdom); autoplay can also be refused. Either is fine.
        Promise.resolve(video.play()).catch(() => {});
      } else {
        video.pause();
      }
    });
  }, [active, visible, muted]);

  function centre(i: number) {
    const el = track.current;
    const item = el?.children[i] as HTMLElement | undefined;
    if (!el || !item) return;
    el.scrollTo?.({ left: item.offsetLeft + item.offsetWidth / 2 - el.clientWidth / 2, behavior: "smooth" });
    setActive(i);
  }

  return (
    <ul
      ref={track}
      onScroll={onScroll}
      aria-label="Instagram reels"
      className="flex snap-x snap-mandatory items-center gap-3 overflow-x-auto px-[14%] py-2 [scrollbar-width:none] sm:px-[30%] md:px-[36%] lg:px-[38%]"
    >
      {reels.map((reel, i) => {
        const isActive = i === active;
        return (
          <li
            key={reel.id}
            className={`relative w-[72%] shrink-0 snap-center transition-[transform,opacity] duration-300 sm:w-[40%] md:w-[28%] lg:w-[24%] ${
              isActive ? "scale-100" : "scale-[0.82] opacity-70"
            }`}
          >
            <a
              href={reel.permalink}
              target="_blank"
              rel="noopener"
              aria-label="Watch this reel on Instagram"
              onClick={(e) => {
                if (!isActive) {
                  e.preventDefault();
                  centre(i);
                }
              }}
              className="relative isolate block aspect-[9/16] overflow-hidden rounded-xl bg-bg-muted"
            >
              {/* eslint-disable-next-line @next/next/no-img-element -- a fixed-size JPEG on the media domain */}
              <img
                src={reel.cover}
                alt=""
                loading={i < 2 ? "eager" : "lazy"}
                decoding="async"
                className="absolute inset-0 -z-10 size-full object-cover"
              />
              {reel.video && (
                <video
                  ref={(el) => {
                    videos.current[i] = el;
                  }}
                  src={reel.video}
                  poster={reel.cover}
                  muted
                  loop
                  playsInline
                  preload={isActive ? "auto" : "none"}
                  className={`absolute inset-0 -z-10 size-full object-cover transition-opacity duration-300 ${
                    isActive ? "opacity-100" : "opacity-0"
                  }`}
                />
              )}
            </a>
            {isActive && reel.video && (
              <button
                type="button"
                onClick={() => setMuted((m) => !m)}
                aria-label={muted ? "Turn sound on" : "Turn sound off"}
                aria-pressed={!muted}
                className="absolute bottom-3 right-3 grid size-10 place-items-center rounded-lg bg-black/45 text-white backdrop-blur-sm"
              >
                <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.8">
                  <path d="M4 9.5h3.5L12 5.5v13l-4.5-4H4z" strokeLinejoin="round" />
                  {muted ? (
                    <path d="m16 9.5 5 5m0-5-5 5" strokeLinecap="round" />
                  ) : (
                    <path d="M16 9a4 4 0 0 1 0 6m2.5-8.5a7.5 7.5 0 0 1 0 11" strokeLinecap="round" />
                  )}
                </svg>
              </button>
            )}
          </li>
        );
      })}
    </ul>
  );
}
