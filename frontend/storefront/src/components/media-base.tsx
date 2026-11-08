"use client";

import { createContext, useContext } from "react";

/**
 * The R2 media domain, read on the server at request time (MEDIA_URL, set in docker-compose) and handed to
 * client components, which cannot read server environment variables. Undefined without photos (local
 * development without a bucket): photos then fall back to colour placeholders.
 */
const MediaBase = createContext<string | undefined>(undefined);

export function MediaBaseProvider({ base, children }: Readonly<{ base?: string; children: React.ReactNode }>) {
  return <MediaBase.Provider value={base}>{children}</MediaBase.Provider>;
}

/** The media domain in client components; falls back to NEXT_PUBLIC_MEDIA_URL when no provider is above. */
export function useMediaBase(): string | undefined {
  return useContext(MediaBase) ?? process.env.NEXT_PUBLIC_MEDIA_URL;
}
