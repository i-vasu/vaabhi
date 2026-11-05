import { ApiError, type AdminMedia } from "@vaabhi/api-client";
import { api } from "./api";
import { resizeToWebp, type Rendition } from "./image-resize";

export const RENDITIONS = [400, 800, 1600] as const;

/** Same rule as the API (VideoUploadRequest): what phones record and every browser can upload. */
export const VIDEO_TYPES = ["video/mp4", "video/quicktime", "video/webm"] as const;
export const MAX_VIDEO_BYTES = 200 * 1024 * 1024;

function mediaBase(): string {
  return (process.env.NEXT_PUBLIC_MEDIA_URL || "https://media.vaabhi.com").replace(/\/$/, "");
}

/** R2 serves photos from the public media domain; keys never change, so they can be cached for a year. */
export function mediaUrl(key: string, width: (typeof RENDITIONS)[number] = 400): string {
  return `${mediaBase()}/${key}/${width}.webp`;
}

/** A video is one file under its key, served with the type it was uploaded with. */
export function videoUrl(key: string): string {
  return `${mediaBase()}/${key}/video`;
}

export type PhotoDetails = { alt: string; colourName: string | null };
type Resize = (file: Blob, widths: readonly number[]) => Promise<Rendition[]>;

/**
 * Resize in the browser → ask the API for presigned URLs → PUT each size straight to R2 → tell the API it is
 * complete (the API checks the files exist). Returns the product's photos after the upload.
 */
export async function uploadPhoto(
  productId: number,
  file: Blob,
  details: PhotoDetails,
  resize: Resize = resizeToWebp,
): Promise<AdminMedia[]> {
  const renditions = await resize(file, RENDITIONS);
  const largest = renditions.at(-1)!;
  const upload = await api.admin.startMediaUpload(productId, {
    alt: details.alt,
    colourName: details.colourName,
    width: largest.pixelWidth,
    height: largest.pixelHeight,
  });
  for (const target of upload.renditions) {
    const rendition = renditions.find((r) => r.width === target.width);
    if (!rendition) throw new Error(`No ${target.width} px version was made`);
    const response = await fetch(target.uploadUrl, {
      method: "PUT",
      body: rendition.blob,
      headers: { "Content-Type": "image/webp", "Cache-Control": "public, max-age=31536000, immutable" },
    });
    if (!response.ok) throw new ApiError(response.status, `Uploading the ${target.width} px version failed`);
  }
  return api.admin.completeMediaUpload(productId, upload.media.id);
}

/**
 * Uploads a video as is (no transcoding): presigned URL → one PUT straight to R2 → complete. Returns the
 * product's media after the upload.
 */
export async function uploadVideo(productId: number, file: File, details: PhotoDetails): Promise<AdminMedia[]> {
  const contentType = file.type as (typeof VIDEO_TYPES)[number];
  if (!VIDEO_TYPES.includes(contentType)) throw new Error("Use an MP4, MOV or WebM video");
  const upload = await api.admin.startVideoUpload(productId, {
    alt: details.alt,
    colourName: details.colourName,
    contentType,
  });
  const response = await fetch(upload.uploadUrl, {
    method: "PUT",
    body: file,
    headers: { "Content-Type": contentType, "Cache-Control": "public, max-age=31536000, immutable" },
  });
  if (!response.ok) throw new ApiError(response.status, "Uploading the video failed");
  return api.admin.completeMediaUpload(productId, upload.media.id);
}
