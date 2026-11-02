"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useId, useRef, useState, useSyncExternalStore } from "react";
import { UserIcon } from "@vaabhi/ui";
import { displayName, sessionStore, signOut, syncSession } from "@/lib/session";

const item = "block w-full px-4 py-2.5 text-left text-sm text-text-primary hover:bg-bg-muted hover:text-brand-primary";

/**
 * Profile icon that opens a menu: profile, orders and wishlist, then log out (signed in) or sign in (guest).
 * Checks the session once per page load, which also keeps the customer signed in (the session is renewed).
 */
export function AccountMenu({ className }: Readonly<{ className?: string }>) {
  const session = useSyncExternalStore(sessionStore.subscribe, sessionStore.read, sessionStore.serverValue);
  const [open, setOpen] = useState(false);
  const root = useRef<HTMLDivElement>(null);
  const menuId = useId();
  const pathname = usePathname();

  useEffect(() => {
    void syncSession();
  }, []);

  useEffect(() => {
    if (!open) return;
    const onPointer = (e: PointerEvent) => {
      if (!root.current?.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setOpen(false);
    };
    document.addEventListener("pointerdown", onPointer);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("pointerdown", onPointer);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  const close = () => setOpen(false);

  return (
    <div ref={root} className="relative flex">
      <button
        type="button"
        aria-label="Account"
        aria-expanded={open}
        aria-controls={menuId}
        onClick={() => setOpen((o) => !o)}
        className={className}
      >
        <UserIcon size={22} />
      </button>
      {open && (
        <nav
          id={menuId}
          aria-label="Account"
          className="absolute right-0 top-full z-20 mt-3 w-52 rounded-sm border border-border-default bg-bg-surface py-1 shadow-lg"
        >
          {session && (
            <p className="truncate border-b border-border-default px-4 py-2.5 text-xs text-text-muted">
              Hi, {displayName(session)}
            </p>
          )}
          <ul>
            <li>
              <Link href="/account" className={item} onClick={close}>
                Profile
              </Link>
            </li>
            <li>
              <Link href="/account/orders" className={item} onClick={close}>
                Orders
              </Link>
            </li>
            <li>
              <Link href="/wishlist" className={item} onClick={close}>
                Wishlist
              </Link>
            </li>
            <li className="mt-1 border-t border-border-default pt-1">
              {session ? (
                <button
                  type="button"
                  className={item}
                  onClick={() => {
                    close();
                    void signOut();
                  }}
                >
                  Log out
                </button>
              ) : (
                <Link
                  href={pathname && pathname !== "/signup" ? `/signup?next=${encodeURIComponent(pathname)}` : "/signup"}
                  className={`${item} font-medium text-brand-primary`}
                  onClick={close}
                >
                  Sign in / Sign up
                </Link>
              )}
            </li>
          </ul>
        </nav>
      )}
    </div>
  );
}
