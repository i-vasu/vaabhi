"use client";

import { useEffect, useRef } from "react";

type GoogleIdentity = {
  accounts: {
    id: {
      initialize(options: {
        client_id: string;
        callback: (response: { credential: string }) => void;
        ux_mode?: "popup" | "redirect";
        auto_select?: boolean;
        context?: "signin" | "signup" | "use";
      }): void;
      renderButton(parent: HTMLElement, options: Record<string, string | number>): void;
    };
  };
};

declare global {
  interface Window {
    google?: GoogleIdentity;
  }
}

const SCRIPT = "https://accounts.google.com/gsi/client";
let loading: Promise<void> | undefined;

function loadGoogle(): Promise<void> {
  if (window.google?.accounts) return Promise.resolve();
  loading ??= new Promise<void>((resolve, reject) => {
    const script = document.createElement("script");
    script.src = SCRIPT;
    script.async = true;
    script.onload = () => resolve();
    script.onerror = () => {
      loading = undefined;
      reject(new Error("Google sign-in did not load"));
    };
    document.head.appendChild(script);
  });
  return loading;
}

/**
 * Google's own "Continue with Google" button (Google Identity Services). Google returns a signed ID token
 * ("credential"), which our backend verifies; no Google password or access to the account is involved.
 */
export function GoogleButton({
  clientId,
  onCredential,
  onError,
}: Readonly<{ clientId: string; onCredential: (credential: string) => void; onError: (message: string) => void }>) {
  const container = useRef<HTMLDivElement>(null);
  const handlers = useRef({ onCredential, onError });

  useEffect(() => {
    handlers.current = { onCredential, onError };
  }, [onCredential, onError]);

  useEffect(() => {
    let cancelled = false;
    loadGoogle()
      .then(() => {
        const parent = container.current;
        if (cancelled || !parent || !window.google) return;
        window.google.accounts.id.initialize({
          client_id: clientId,
          callback: (response) => handlers.current.onCredential(response.credential),
          ux_mode: "popup",
          auto_select: false,
          context: "signin",
        });
        window.google.accounts.id.renderButton(parent, {
          type: "standard",
          theme: "outline",
          size: "large",
          text: "continue_with",
          shape: "rectangular",
          logo_alignment: "center",
          width: Math.min(parent.offsetWidth || 360, 400),
        });
      })
      .catch(() => {
        if (!cancelled) {
          handlers.current.onError("Google sign-in could not load. Check your connection or use your mobile number.");
        }
      });
    return () => {
      cancelled = true;
    };
  }, [clientId]);

  return <div ref={container} data-testid="google-button" className="flex min-h-11 w-full justify-center" />;
}
