import Link from "next/link";
import { BagIcon, HeartIcon, MenuIcon, SearchIcon } from "@vaabhi/ui";

const iconButton = "rounded-sm p-0.5 text-text-primary hover:text-brand-primary";

export function SiteHeader() {
  return (
    <>
      <p className="bg-bg-muted px-4 py-2 text-center text-xs tracking-[0.02em] text-text-secondary">
        Free shipping above ₹1,999 · COD · 7-day easy returns
      </p>
      <header className="sticky top-0 z-10 border-b border-border-default bg-bg-surface">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3">
          <div className="flex items-center gap-4">
            <button type="button" aria-label="Open menu" className={iconButton}>
              <MenuIcon size={22} />
            </button>
            <Link href="/search" aria-label="Search" className={iconButton}>
              <SearchIcon size={22} />
            </Link>
          </div>
          <Link href="/" className="font-serif text-[26px] font-semibold tracking-[0.14em] text-brand-primary">
            VAABHI
          </Link>
          <div className="flex items-center gap-4">
            <Link href="/wishlist" aria-label="Wishlist" className={iconButton}>
              <HeartIcon size={22} />
            </Link>
            <Link href="/bag" aria-label="Shopping bag" className={iconButton}>
              <BagIcon size={22} />
            </Link>
          </div>
        </div>
      </header>
    </>
  );
}
