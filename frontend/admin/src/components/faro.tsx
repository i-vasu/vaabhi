"use client";

import { useEffect } from "react";
import { getWebInstrumentations, initializeFaro } from "@grafana/faro-web-sdk";

let initialized = false;

/** Grafana Cloud Frontend Observability (RUM). No-op unless NEXT_PUBLIC_FARO_URL is set. */
export function Faro({ appName }: { appName: string }) {
  useEffect(() => {
    const url = process.env.NEXT_PUBLIC_FARO_URL;
    if (!url || initialized) return;
    initialized = true;
    initializeFaro({
      url,
      app: {
        name: appName,
        version: process.env.NEXT_PUBLIC_APP_VERSION ?? "dev",
        environment: process.env.NEXT_PUBLIC_DEPLOY_ENV ?? "local",
      },
      instrumentations: getWebInstrumentations(),
    });
  }, [appName]);

  return null;
}
