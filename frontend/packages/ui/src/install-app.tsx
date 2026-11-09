"use client";

import { useEffect } from "react";

/**
 * Registers the app's service worker (public/sw.js) so phones and desktops offer "Install app". Production
 * only: in development it would get in the way of hot reload.
 */
export function InstallableApp() {
  useEffect(() => {
    if (process.env.NODE_ENV !== "production" || !("serviceWorker" in navigator)) return;
    navigator.serviceWorker.register("/sw.js").catch(() => {
      // Not installable in this browser; the site works the same.
    });
  }, []);
  return null;
}
