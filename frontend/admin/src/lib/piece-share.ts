import type { WorkPiece } from "@vaabhi/api-client";
import { qty, unitLabel } from "./extras";
import { mediaUrl } from "./photo-upload";

const DIMENSION_UNITS: Record<string, string> = { IN: "in", CM: "cm", M: "m" };

/** The WhatsApp message for a piece; lines with nothing to say are left out. */
export function pieceMessage(piece: WorkPiece): string {
  const fabric = piece.fabric;
  const fabricParts = [
    [fabric.material, fabric.colour].filter(Boolean).join(", "),
    `${qty(piece.quantity)} ${unitLabel(piece.unit)}`,
    fabric.widthIn ? `${qty(fabric.widthIn)}" wide` : "",
    fabric.gsm ? `${fabric.gsm} GSM` : "",
  ].filter(Boolean);
  const measurements = piece.dimensions
    .map((d) => `${d.label} ${qty(d.value)} ${DIMENSION_UNITS[d.unit] ?? d.unit.toLowerCase()}`)
    .join(" · ");
  return [
    `🧵 Vaabhi – ${piece.stage.name}`,
    piece.productName,
    `Fabric: ${fabricParts.join(" · ")}`,
    measurements && `Measurements: ${measurements}`,
    piece.note && `Note: ${piece.note}`,
  ]
    .filter(Boolean)
    .join("\n");
}

/** Design photos first, then the fabric's first photo. */
export function pieceSharePhotos(piece: WorkPiece): string[] {
  return [...piece.photos.map((p) => mediaUrl(p.key)), ...(piece.photoKey ? [mediaUrl(piece.photoKey)] : [])];
}

type ShareNavigator = {
  canShare?: (data: ShareData) => boolean;
  share?: (data: ShareData) => Promise<void>;
};

export type ShareDeps = {
  nav: ShareNavigator;
  fetch: (url: string) => Promise<Response>;
  /** Opens the link; false when the browser blocked the new window. */
  open: (url: string) => boolean;
};

const defaultDeps = (): ShareDeps => ({
  nav: navigator,
  fetch: (url) => fetch(url),
  open: (url) => {
    // No "noopener" feature: with it window.open always returns null and a blocked popup cannot be told apart.
    const opened = window.open(url, "_blank");
    if (opened) opened.opener = null;
    return opened !== null;
  },
});

/** Thrown when the browser blocked the wa.me window. */
export class ShareBlockedError extends Error {
  constructor() {
    super("The browser blocked WhatsApp from opening. Allow pop-ups for this site and try again.");
  }
}

/** wa.me wants the number with country code and digits only; a bare 10-digit Indian mobile gets 91. */
export function waNumber(phone: string | null | undefined): string {
  const digits = (phone ?? "").replace(/\D/g, "");
  return digits.length === 10 ? `91${digits}` : digits;
}

export type ShareFile = { url: string; file: File | null };

/**
 * Downloads the share photos as files ahead of the tap (the share sheet must open right after it). A photo that
 * cannot be fetched (offline, media CORS not allowing GET) comes back with `file: null` and is shared as a link.
 */
export function fetchShareFiles(
  piece: WorkPiece,
  deps: Pick<ShareDeps, "fetch"> = defaultDeps(),
): Promise<ShareFile[]> {
  return Promise.all(
    pieceSharePhotos(piece).map(async (url, index) => {
      try {
        const response = await deps.fetch(url);
        if (!response.ok) return { url, file: null };
        const blob = await response.blob();
        return { url, file: new File([blob], `vaabhi-${piece.pieceId}-${index + 1}.webp`, { type: "image/webp" }) };
      } catch {
        return { url, file: null };
      }
    }),
  );
}

/**
 * Shares the piece with a tailor. On phones the share sheet gets the photos as files plus the message (pick
 * WhatsApp, then the chat). Where files cannot be shared (desktop), wa.me opens with the message and photo links.
 * Pass `prepared` from {@link fetchShareFiles}: the share sheet only opens straight from a tap, so nothing here waits
 * on the network first. Without it (photos still downloading) the photos go as links.
 */
export async function sharePiece(
  piece: WorkPiece,
  prepared?: ShareFile[],
  deps: ShareDeps = defaultDeps(),
): Promise<"shared" | "link" | "cancelled"> {
  const text = pieceMessage(piece);
  const urls = pieceSharePhotos(piece);
  if (deps.nav.share && deps.nav.canShare) {
    const fetched = prepared ?? urls.map((url) => ({ url, file: null }));
    const files = fetched.flatMap((f) => (f.file ? [f.file] : []));
    const missing = fetched.flatMap((f) => (f.file ? [] : [f.url]));
    const withLinks = missing.length ? `${text}\n${missing.join("\n")}` : text;
    const data: ShareData = files.length ? { files, text: withLinks } : { text: withLinks };
    if (deps.nav.canShare(data)) {
      try {
        await deps.nav.share(data);
        return "shared";
      } catch (error) {
        if (error instanceof DOMException && error.name === "AbortError") return "cancelled";
        // Any other failure: fall through to the wa.me link.
      }
    }
  }
  const message = urls.length ? `${text}\n${urls.join("\n")}` : text;
  if (!deps.open(`https://wa.me/${waNumber(piece.assignedPhone)}?text=${encodeURIComponent(message)}`)) {
    throw new ShareBlockedError();
  }
  return "link";
}
