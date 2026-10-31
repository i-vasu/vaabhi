import Link from "next/link";
import { appVersion } from "@vaabhi/observability";

const columns = [
  {
    title: "Shop",
    links: [
      ["Sarees", "/c/sarees"],
      ["Lehengas", "/c/lehengas"],
      ["Kurta sets", "/c/kurta-sets"],
      ["New arrivals", "/new"],
    ],
  },
  {
    title: "Help",
    links: [
      ["Track order", "/track"],
      ["Returns", "/returns"],
      ["Size guide", "/size-guide"],
      ["Contact us", "/contact"],
    ],
  },
] as const;

export function SiteFooter() {
  return (
    <footer className="bg-bg-muted">
      <div className="mx-auto flex max-w-6xl flex-col gap-4 px-4 py-8">
        <p className="font-serif text-[22px] font-semibold tracking-[0.14em] text-brand-primary">VAABHI</p>
        <div className="flex gap-12">
          {columns.map((column) => (
            <nav key={column.title} aria-label={column.title}>
              <ul className="flex flex-col gap-2">
                {column.links.map(([label, href]) => (
                  <li key={href}>
                    <Link href={href} className="text-sm text-text-secondary hover:text-brand-primary">
                      {label}
                    </Link>
                  </li>
                ))}
              </ul>
            </nav>
          ))}
        </div>
        <hr className="border-border-default" />
        <p className="text-xs text-text-muted">
          © 2026 Vaabhi · Made in India · UPI, cards and COD accepted
          <span className="sr-only"> · {appVersion().label}</span>
        </p>
      </div>
    </footer>
  );
}
