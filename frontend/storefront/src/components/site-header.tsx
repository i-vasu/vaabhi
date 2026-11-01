import Link from "next/link";
import { SearchIcon } from "@vaabhi/ui";
import { AccountMenu } from "./account-menu";
import { BagLink } from "./bag-link";
import { LogoLink } from "./logo-link";
import { SiteMenu } from "./site-menu";

const iconButton = "rounded-sm p-0.5 text-text-primary hover:text-brand-primary";

export function SiteHeader() {
  return (
    <>
      <p className="bg-bg-muted px-4 py-2 text-center text-xs tracking-[0.02em] text-text-secondary">
        Free shipping above ₹1,999 · COD · 7-day easy returns
      </p>
      <header className="sticky top-0 z-10 border-b border-border-default bg-bg-surface">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3">
          <div className="flex flex-1 items-center">
            <SiteMenu className={iconButton} />
          </div>
          <LogoLink className="font-serif text-[26px] font-semibold tracking-[0.14em] text-brand-primary" />
          <div className="flex flex-1 items-center justify-end gap-4">
            <Link href="/search" aria-label="Search" className={iconButton}>
              <SearchIcon size={22} />
            </Link>
            <BagLink className={iconButton} />
            <AccountMenu className={iconButton} />
          </div>
        </div>
      </header>
    </>
  );
}
