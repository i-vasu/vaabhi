"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useMemo, useState, type ReactNode } from "react";
import { ApiError, type StaffProfile, type StaffRole } from "@vaabhi/api-client";
import { cn } from "@vaabhi/ui";
import { appVersion } from "@vaabhi/observability";
import { api } from "@/lib/api";
import { clearSession, getStaff, getToken } from "@/lib/auth";
import { isActive, moreNav, primaryNav, searchableNav } from "@/lib/nav";
import { BellIcon, LogoutIcon, SearchIcon } from "./icons";
import { Sheet, inputClasses } from "./ui";

const SHOP_NAME = "vaabhi";

const roleLabel: Record<StaffRole, string> = { OWNER: "Owner", MANAGER: "Manager", STAFF: "Staff" };

export function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  const letters = parts.length > 1 ? parts[0]![0]! + parts.at(-1)![0]! : (parts[0] ?? "?").slice(0, 2);
  return letters.toUpperCase();
}

/**
 * Frame of every signed-in page: sidebar (wide screens), header and bottom bar (phones). Pages under /login
 * render bare. Anyone without a valid sign-in is sent to /login and comes back here afterwards.
 */
export function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const { replace } = useRouter();
  const isLogin = pathname.startsWith("/login");
  const [staff, setStaff] = useState<StaffProfile | undefined>(undefined);

  useEffect(() => {
    if (isLogin) return;
    const signOutTo = () => {
      clearSession();
      replace(`/login/?next=${encodeURIComponent(pathname)}`);
    };
    if (!getToken()) {
      signOutTo();
      return;
    }
    let cancelled = false;
    api.admin
      .me()
      .then((profile) => !cancelled && setStaff(profile))
      .catch((error: unknown) => {
        if (cancelled) return;
        if (error instanceof ApiError && (error.status === 401 || error.status === 403)) signOutTo();
        // API unreachable: keep working with the profile saved at sign-in.
        else setStaff(getStaff());
      });
    return () => {
      cancelled = true;
    };
  }, [isLogin, pathname, replace]);

  if (isLogin) return <>{children}</>;
  // Nothing protected is shown until we know who is signed in.
  if (!staff) return <div className="min-h-screen bg-app" aria-busy="true" />;

  return (
    <div className="flex min-h-screen">
      <Sidebar pathname={pathname} />
      <div className="flex min-w-0 flex-1 flex-col">
        <Header
          staff={staff}
          onSignOut={() => {
            clearSession();
            replace("/login/");
          }}
        />
        <main className="flex-1 px-4 pb-28 pt-6 md:px-8 md:pb-10">{children}</main>
      </div>
      <BottomBar pathname={pathname} />
    </div>
  );
}

function Sidebar({ pathname }: { pathname: string }) {
  return (
    <aside className="sticky top-0 hidden h-screen w-60 shrink-0 flex-col border-r border-line bg-panel p-4 md:flex">
      <Link href="/" className="mb-8 px-2 text-lg font-semibold tracking-[0.2em]">
        VAABHI
      </Link>
      <nav aria-label="Main" className="flex flex-col gap-1">
        {[...primaryNav, moreNav].map((item) => (
          <Link
            key={item.href}
            href={item.href}
            aria-current={isActive(item, pathname) ? "page" : undefined}
            className={cn(
              "flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm",
              isActive(item, pathname) ? "bg-accent-soft text-accent-ink" : "text-ink-soft hover:bg-panel-2",
            )}
          >
            <item.icon />
            {item.label}
          </Link>
        ))}
      </nav>
      <p className="mt-auto px-2 pt-6 text-xs text-ink-faint" data-testid="app-version">
        {appVersion().label}
      </p>
    </aside>
  );
}

function BottomBar({ pathname }: { pathname: string }) {
  return (
    <nav
      aria-label="Main"
      className="fixed inset-x-0 bottom-0 z-40 flex border-t border-line bg-panel pb-[env(safe-area-inset-bottom)] md:hidden"
    >
      {[...primaryNav, moreNav].map((item) => {
        const active = isActive(item, pathname);
        return (
          <Link
            key={item.href}
            href={item.href}
            aria-current={active ? "page" : undefined}
            className={cn(
              "flex flex-1 flex-col items-center gap-1 py-2.5 text-xs",
              active ? "text-ink" : "text-ink-faint",
            )}
          >
            <item.icon size={22} />
            {item.label}
            <span className={cn("h-0.5 w-6 rounded-full", active ? "bg-ink" : "bg-transparent")} />
          </Link>
        );
      })}
    </nav>
  );
}

function Header({ staff, onSignOut }: { staff: StaffProfile; onSignOut: () => void }) {
  const [searching, setSearching] = useState(false);
  const [account, setAccount] = useState(false);
  const iconButton =
    "grid size-11 place-items-center rounded-xl border border-line text-ink-soft hover:bg-panel-2 hover:text-ink";

  return (
    <header className="sticky top-0 z-30 flex items-center gap-3 border-b border-line bg-panel/95 px-4 py-3 backdrop-blur md:px-8">
      <button
        type="button"
        onClick={() => setAccount(true)}
        className="flex min-w-0 items-center gap-3 text-left"
        aria-label="Account"
      >
        <span className="grid size-11 shrink-0 place-items-center rounded-full bg-accent font-semibold text-white">
          {initials(staff.name)}
        </span>
        <span className="min-w-0">
          <span className="block truncate font-semibold">{staff.name}</span>
          <span className="block truncate text-sm text-ink-soft">
            {roleLabel[staff.role]} · {SHOP_NAME}
          </span>
        </span>
      </button>
      <div className="ml-auto flex gap-2">
        <button type="button" className={iconButton} aria-label="Search" onClick={() => setSearching(true)}>
          <SearchIcon />
        </button>
        <Link href="/notifications/" className={iconButton} aria-label="Notifications">
          <BellIcon />
        </Link>
      </div>

      <GoToSheet open={searching} onClose={() => setSearching(false)} />
      <Sheet open={account} title="Account" onClose={() => setAccount(false)}>
        <p className="font-medium">{staff.name}</p>
        <p className="text-sm text-ink-soft">{staff.email}</p>
        <button
          type="button"
          onClick={onSignOut}
          className="mt-6 flex w-full items-center justify-center gap-2 rounded-xl border border-line-strong py-3 text-bad hover:bg-bad-soft"
        >
          <LogoutIcon /> Sign out
        </button>
      </Sheet>
    </header>
  );
}

/** Jump to any page by name. Search across orders and customers arrives with those modules. */
function GoToSheet({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [query, setQuery] = useState("");
  const results = useMemo(() => {
    const q = query.trim().toLowerCase();
    return searchableNav().filter(
      (item) => !q || item.label.toLowerCase().includes(q) || item.description?.toLowerCase().includes(q),
    );
  }, [query]);

  return (
    <Sheet open={open} title="Go to" onClose={onClose}>
      <input
        aria-label="Search pages"
        placeholder="Search pages"
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        className={inputClasses}
      />
      <ul className="mt-4 flex flex-col gap-1">
        {results.map((item) => (
          <li key={item.href}>
            <Link
              href={item.href}
              onClick={onClose}
              className="flex items-center gap-3 rounded-xl px-3 py-2.5 hover:bg-panel-2"
            >
              <item.icon className="text-accent-ink" />
              <span>
                <span className="block">{item.label}</span>
                {item.description && <span className="block text-sm text-ink-soft">{item.description}</span>}
              </span>
            </Link>
          </li>
        ))}
        {results.length === 0 && <li className="px-3 py-2 text-ink-soft">No page matches “{query}”.</li>}
      </ul>
    </Sheet>
  );
}
