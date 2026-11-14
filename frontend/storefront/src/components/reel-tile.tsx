"use client";

import { useRef, useState } from "react";

/**
 * One Instagram reel tile. The cover (a JPEG on our media domain) is always shown; on hover the reel's MP4 plays
 * over it, muted and looping. On touch there is no hover, so a tap opens the reel on Instagram instead.
 */
export function ReelTile({ permalink, cover, video }: Readonly<{ permalink: string; cover: string; video?: string }>) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const [playing, setPlaying] = useState(false);

  function play() {
    const el = videoRef.current;
    if (!el) return;
    setPlaying(true);
    el.play().catch(() => {});
  }

  function stop() {
    const el = videoRef.current;
    if (!el) return;
    setPlaying(false);
    el.pause();
    el.currentTime = 0;
  }

  return (
    <a
      href={permalink}
      target="_blank"
      rel="noopener"
      aria-label="Watch this reel on Instagram"
      onPointerEnter={video ? play : undefined}
      onPointerLeave={video ? stop : undefined}
      className="group relative isolate block aspect-[9/16] overflow-hidden rounded-sm bg-bg-muted"
    >
      {/* eslint-disable-next-line @next/next/no-img-element -- a fixed-size JPEG on the media domain */}
      <img
        src={cover}
        alt=""
        loading="lazy"
        decoding="async"
        className="absolute inset-0 -z-10 size-full object-cover"
      />
      {video && (
        <video
          ref={videoRef}
          src={video}
          poster={cover}
          muted
          loop
          playsInline
          preload="none"
          className={`absolute inset-0 -z-10 size-full object-cover transition-opacity ${
            playing ? "opacity-100" : "opacity-0"
          }`}
        />
      )}
      {!playing && (
        <span
          aria-hidden="true"
          className="absolute left-1/2 top-1/2 grid size-11 -translate-x-1/2 -translate-y-1/2 place-items-center rounded-full bg-black/40 text-white backdrop-blur-sm"
        >
          <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
            <path d="M8 5.5v13l11-6.5z" />
          </svg>
        </span>
      )}
    </a>
  );
}
