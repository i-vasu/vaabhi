/** One resized copy of a photo. `width` is the target rendition (400/800/1600); pixel sizes are actual. */
export type Rendition = { width: number; pixelWidth: number; pixelHeight: number; blob: Blob };

export class UnsupportedBrowserError extends Error {
  constructor() {
    super("This browser cannot create WebP images. Use Chrome, Edge or Firefox to upload photos.");
  }
}

/**
 * Resizes a photo in the browser (never upscaling) and encodes each size as WebP, so only small files are
 * uploaded and the server never touches image bytes. EXIF orientation is applied.
 */
export async function resizeToWebp(file: Blob, widths: readonly number[], quality = 0.82): Promise<Rendition[]> {
  const bitmap = await createImageBitmap(file, { imageOrientation: "from-image" });
  try {
    const renditions: Rendition[] = [];
    for (const width of widths) {
      const scale = Math.min(1, width / bitmap.width);
      const pixelWidth = Math.round(bitmap.width * scale);
      const pixelHeight = Math.round(bitmap.height * scale);
      const canvas = document.createElement("canvas");
      canvas.width = pixelWidth;
      canvas.height = pixelHeight;
      const context = canvas.getContext("2d");
      if (!context) throw new UnsupportedBrowserError();
      context.imageSmoothingQuality = "high";
      context.drawImage(bitmap, 0, 0, pixelWidth, pixelHeight);
      const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, "image/webp", quality));
      // Browsers that cannot encode WebP silently return PNG.
      if (blob?.type !== "image/webp") throw new UnsupportedBrowserError();
      renditions.push({ width, pixelWidth, pixelHeight, blob });
    }
    return renditions;
  } finally {
    bitmap.close();
  }
}
