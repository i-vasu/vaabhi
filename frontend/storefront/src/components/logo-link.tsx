"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

/**
 * The VAABHI wordmark. It always lands at the very top of the home page (announcement bar and banner in view):
 * on the home page it scrolls up; elsewhere it jumps to the top and then navigates, so Next does not scroll the
 * page content in under the sticky header.
 */
export function LogoLink({ className }: Readonly<{ className?: string }>) {
  const pathname = usePathname();
  return (
    <Link
      href="/"
      scroll={false}
      className={className}
      onClick={(e) => {
        if (pathname === "/") {
          e.preventDefault();
          const reduce = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;
          window.scrollTo({ top: 0, behavior: reduce ? "auto" : "smooth" });
        } else {
          window.scrollTo({ top: 0 });
        }
      }}
    >
      VAABHI
    </Link>
  );
}
