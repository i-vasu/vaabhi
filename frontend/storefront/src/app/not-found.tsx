import Link from "next/link";
import { buttonClasses } from "@vaabhi/ui";

export default function NotFound() {
  return (
    <section className="mx-auto flex w-full max-w-xl flex-col items-center gap-4 px-4 py-20 text-center">
      <p className="text-xs tracking-[0.16em] text-text-muted">PAGE NOT FOUND</p>
      <h1 className="font-serif text-[32px] font-semibold leading-10 text-text-primary">We couldn’t find that page</h1>
      <p className="text-sm text-text-muted">It may have moved, or the link may be mistyped.</p>
      <div className="flex flex-wrap justify-center gap-3 pt-2">
        <Link href="/" className={buttonClasses("primary")}>
          Back to the shop
        </Link>
        <Link href="/search" className={buttonClasses("secondary")}>
          Search
        </Link>
      </div>
    </section>
  );
}
