import { ApiError, type AdminMedia } from "@vaabhi/api-client";
import { api } from "./api";
import { resizeToWebp, type Rendition } from "./image-resize";

export const RENDITIONS = [400, 800, 1600] as const;

/** R2 serves photos from the public media domain; keys never change, so they can be cached for a year. */
export function mediaUrl(key: string, width: (typeof RENDITIONS)[number] = 400): string {
  const base = process.env.NEXT_PUBLIC_MEDIA_URL || "https://media.vaabhi.com";
  return `${base.replace(/\/$/, "")}/${key}/${width}.webp`;
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
