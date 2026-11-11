import { render, screen } from "@testing-library/react";
import type { InstagramReel } from "@vaabhi/api-client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { mediaFileUrl } from "@/lib/listing";
import { InstagramReels } from "./instagram-reels";

const reels: InstagramReel[] = [
  {
    id: "3",
    permalink: "https://instagram.com/reel/3",
    caption: "Festive edit",
    imageKey: "instagram/3.jpg",
    postedAt: "2026-11-03T10:00:00Z",
  },
  {
    id: "2",
    permalink: "https://instagram.com/reel/2",
    caption: null,
    imageKey: "instagram/2.jpg",
    postedAt: "2026-11-02T10:00:00Z",
  },
];

describe("InstagramReels", () => {
  beforeEach(() => {
    vi.stubEnv("NEXT_PUBLIC_MEDIA_URL", "");
    vi.stubEnv("MEDIA_URL", "https://media.vaabhi.com/");
  });
  afterEach(() => vi.unstubAllEnvs());

  it("links each cover to its reel on Instagram", () => {
    const { container } = render(<InstagramReels reels={reels} profileUrl="https://instagram.com/vaabhi" />);

    expect(screen.getByRole("heading", { name: "On Instagram" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Follow us" })).toHaveAttribute("href", "https://instagram.com/vaabhi");
    const captioned = screen.getByRole("link", { name: "Watch on Instagram: Festive edit" });
    expect(captioned).toHaveAttribute("href", "https://instagram.com/reel/3");
    expect(captioned).toHaveAttribute("target", "_blank");
    expect(screen.getByRole("link", { name: "Watch this reel on Instagram" })).toHaveAttribute(
      "href",
      "https://instagram.com/reel/2",
    );
    const covers = [...container.querySelectorAll("img")].map((img) => img.getAttribute("src"));
    expect(covers).toEqual(["https://media.vaabhi.com/instagram/3.jpg", "https://media.vaabhi.com/instagram/2.jpg"]);
    expect(screen.getByText("Festive edit")).toBeInTheDocument();
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
