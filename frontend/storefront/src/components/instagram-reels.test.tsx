import { fireEvent, render, screen } from "@testing-library/react";
import type { InstagramReel } from "@vaabhi/api-client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { mediaFileUrl } from "@/lib/listing";
import { InstagramReels } from "./instagram-reels";

type ReelWithVideo = InstagramReel & { videoKey: string };

const reels: ReelWithVideo[] = [
  {
    id: "3",
    permalink: "https://instagram.com/reel/3",
    caption: "Festive edit",
    imageKey: "instagram/3.jpg",
    videoKey: "instagram/3.mp4",
    postedAt: "2026-11-03T10:00:00Z",
  },
  {
    id: "2",
    permalink: "https://instagram.com/reel/2",
    caption: null,
    imageKey: "instagram/2.jpg",
    videoKey: "instagram/2.mp4",
    postedAt: "2026-11-02T10:00:00Z",
  },
];

describe("InstagramReels", () => {
  beforeEach(() => {
    vi.stubEnv("NEXT_PUBLIC_MEDIA_URL", "");
    vi.stubEnv("MEDIA_URL", "https://media.vaabhi.com/");
  });
  afterEach(() => {
    vi.unstubAllEnvs();
    vi.restoreAllMocks();
  });

  it("links each cover to its reel and layers the reel video over it, with no caption", () => {
    const { container } = render(<InstagramReels reels={reels} profileUrl="https://instagram.com/vaabhi" />);

    expect(screen.getByRole("heading", { name: "On Instagram" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Follow us" })).toHaveAttribute("href", "https://instagram.com/vaabhi");

    const tiles = screen.getAllByRole("link", { name: "Watch this reel on Instagram" });
    expect(tiles).toHaveLength(2);
    expect(tiles[0]).toHaveAttribute("href", "https://instagram.com/reel/3");
    expect(tiles[0]).toHaveAttribute("target", "_blank");
    expect(tiles[1]).toHaveAttribute("href", "https://instagram.com/reel/2");

    const covers = [...container.querySelectorAll("img")].map((img) => img.getAttribute("src"));
    expect(covers).toEqual(["https://media.vaabhi.com/instagram/3.jpg", "https://media.vaabhi.com/instagram/2.jpg"]);
    const videos = [...container.querySelectorAll("video")].map((video) => video.getAttribute("src"));
    expect(videos).toEqual(["https://media.vaabhi.com/instagram/3.mp4", "https://media.vaabhi.com/instagram/2.mp4"]);
    expect(screen.queryByText("Festive edit")).not.toBeInTheDocument();
  });

  it("autoplays only the centre reel, muted, with a sound toggle", () => {
    const play = vi.spyOn(HTMLMediaElement.prototype, "play").mockImplementation(() => Promise.resolve());
    vi.spyOn(HTMLMediaElement.prototype, "pause").mockImplementation(() => {});
    const { container } = render(<InstagramReels reels={reels} />);

    expect(play).toHaveBeenCalled();
    expect(play.mock.contexts.every((video) => video === container.querySelector("video"))).toBe(true);

    const sound = screen.getByRole("button", { name: "Turn sound on" });
    fireEvent.click(sound);
    expect(screen.getByRole("button", { name: "Turn sound off" })).toHaveAttribute("aria-pressed", "true");
  });

  it("brings a side reel to the centre instead of leaving the page", () => {
    vi.spyOn(HTMLMediaElement.prototype, "play").mockImplementation(() => Promise.resolve());
    vi.spyOn(HTMLMediaElement.prototype, "pause").mockImplementation(() => {});
    render(<InstagramReels reels={reels} />);

    const side = screen.getAllByRole("link", { name: "Watch this reel on Instagram" })[1]!;
    expect(fireEvent.click(side)).toBe(false);
  });

  it("shows at most six reels", () => {
    const eight: ReelWithVideo[] = Array.from({ length: 8 }, (_, i) => ({
      id: String(i + 1),
      permalink: `https://instagram.com/reel/${i + 1}`,
      caption: null,
      imageKey: `instagram/${i + 1}.jpg`,
      videoKey: `instagram/${i + 1}.mp4`,
      postedAt: `2026-11-${String(i + 1).padStart(2, "0")}T10:00:00Z`,
    }));

    render(<InstagramReels reels={eight} />);

    expect(screen.getAllByRole("link", { name: "Watch this reel on Instagram" })).toHaveLength(6);
  });

  it("leaves out the follow link without a profile", () => {
    render(<InstagramReels reels={reels} />);
    expect(screen.queryByRole("link", { name: "Follow us" })).not.toBeInTheDocument();
  });

  it("shows nothing without reels or a media domain", () => {
    expect(render(<InstagramReels reels={[]} />).container).toBeEmptyDOMElement();

    vi.stubEnv("MEDIA_URL", "");
    expect(render(<InstagramReels reels={reels} />).container).toBeEmptyDOMElement();
  });
});

describe("mediaFileUrl", () => {
  it("joins the media domain and the key", () => {
    expect(mediaFileUrl("instagram/1.jpg", "https://media.vaabhi.com/")).toBe(
      "https://media.vaabhi.com/instagram/1.jpg",
    );
    expect(mediaFileUrl("instagram/1.jpg", "https://cdn.example")).toBe("https://cdn.example/instagram/1.jpg");
  });

  it("has no URL without a media domain", () => {
    vi.stubEnv("MEDIA_URL", "");
    vi.stubEnv("NEXT_PUBLIC_MEDIA_URL", "");
    expect(mediaFileUrl("instagram/1.jpg")).toBeUndefined();
    vi.unstubAllEnvs();
  });
});
