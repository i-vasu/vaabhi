"use client";

import Link from "next/link";
import { useState } from "react";
import { MenuIcon } from "@vaabhi/ui";
import { menuLinks } from "@/lib/site-links";
import { Sheet } from "./sheet";

/** Header menu icon that opens the site menu as a left-hand drawer. */
export function SiteMenu({ className }: Readonly<{ className?: string }>) {
  const [open, setOpen] = useState(false);
  const close = () => setOpen(false);

  return (
    <>
      <button
        type="button"
        aria-label="Open menu"
        aria-expanded={open}
        aria-haspopup="dialog"
        onClick={() => setOpen(true)}
        className={className}
      >
        <MenuIcon size={22} />
      </button>
      <Sheet open={open} onClose={close} title="Hey, how are you doing today?" side="start">
        <nav aria-label="Site">
          <ul>
            {menuLinks.map(([label, href]) => (
              <li key={href} className="border-b border-border-default">
                <Link
                  href={href}
                  onClick={close}
                  className="block px-4 py-4 text-base text-text-primary hover:text-brand-primary"
                >
                  {label}
                </Link>
              </li>
            ))}
          </ul>
        </nav>
      </Sheet>
    </>
  );
}
