import type { ImageLoaderProps } from "next/image";

/**
 * Builds a Cloudflare Images flexible-variant URL.
 * `src` is the Cloudflare image id (e.g. "2cdc28f0-017a-49c4-9ed7-87056c83901"),
 * or an absolute URL, which is passed through unchanged.
 */
export default function cloudflareImageLoader({ src, width, quality }: ImageLoaderProps): string {
  if (/^https?:\/\//.test(src) || src.startsWith("/")) {
    return src;
  }
  const accountHash = process.env.NEXT_PUBLIC_CF_IMAGES_ACCOUNT_HASH;
  return `https://imagedelivery.net/${accountHash}/${src}/w=${width},q=${quality ?? 75},f=auto`;
}
