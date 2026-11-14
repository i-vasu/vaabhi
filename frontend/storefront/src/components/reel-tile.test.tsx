import { fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ReelTile } from "./reel-tile";

const tile = { permalink: "https://instagram.com/reel/1", cover: "instagram/1.jpg", video: "instagram/1.mp4" };

describe("ReelTile", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("plays the video and hides the play icon on hover", () => {
    const play = vi.spyOn(HTMLMediaElement.prototype, "play").mockImplementation(() => Promise.resolve());
    const { container } = render(<ReelTile {...tile} />);

    const link = screen.getByRole("link", { name: "Watch this reel on Instagram" });
    expect(container.querySelector("video")).not.toBeNull();
    expect(container.querySelector("svg")).not.toBeNull();

    fireEvent.pointerEnter(link);

    expect(play).toHaveBeenCalledTimes(1);
    expect(container.querySelector("svg")).toBeNull();
  });

  it("pauses, resets and shows the play icon again on leave", () => {
    vi.spyOn(HTMLMediaElement.prototype, "play").mockImplementation(() => Promise.resolve());
    const pause = vi.spyOn(HTMLMediaElement.prototype, "pause").mockImplementation(() => {});
    const { container } = render(<ReelTile {...tile} />);

    const link = screen.getByRole("link", { name: "Watch this reel on Instagram" });
    fireEvent.pointerEnter(link);
    expect(container.querySelector("svg")).toBeNull();

    const videoEl = container.querySelector("video")!;
    videoEl.currentTime = 3;
    fireEvent.pointerLeave(link);

    expect(pause).toHaveBeenCalledTimes(1);
    expect(videoEl.currentTime).toBe(0);
    expect(container.querySelector("svg")).not.toBeNull();
  });

  it("renders no video and ignores hover when there is no video", () => {
    const play = vi.spyOn(HTMLMediaElement.prototype, "play").mockImplementation(() => Promise.resolve());
    const { container } = render(<ReelTile permalink={tile.permalink} cover={tile.cover} />);

    const link = screen.getByRole("link", { name: "Watch this reel on Instagram" });
    expect(container.querySelector("video")).toBeNull();

    fireEvent.pointerEnter(link);

    expect(play).not.toHaveBeenCalled();
    expect(container.querySelector("svg")).not.toBeNull();
  });

  it("swallows a rejected play()", () => {
    const play = vi.spyOn(HTMLMediaElement.prototype, "play").mockRejectedValue(new Error("nope"));
    const { container } = render(<ReelTile {...tile} />);

    const link = screen.getByRole("link", { name: "Watch this reel on Instagram" });
    expect(() => fireEvent.pointerEnter(link)).not.toThrow();

    expect(play).toHaveBeenCalledTimes(1);
    expect(container.querySelector("svg")).toBeNull();
  });
});
