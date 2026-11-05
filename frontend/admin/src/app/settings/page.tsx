"use client";

import { useState } from "react";
import { SearchIcon } from "@/components/icons";
import { ListCard, ListRow, PageHeader, inputClasses } from "@/components/ui";
import { useStaff } from "@/components/app-shell";
import { canSee, settingsPages } from "@/lib/nav";

/** Settings hub: one row per area, searchable by what the area contains. */
export default function SettingsPage() {
  const [query, setQuery] = useState("");
  const staff = useStaff();
  const q = query.trim().toLowerCase();
  const pages = settingsPages.filter(
    (page) =>
      canSee(page, staff) &&
      (!q || page.label.toLowerCase().includes(q) || page.description?.toLowerCase().includes(q)),
  );

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader title="Settings" backHref="/more/" />
      <div className="relative mb-6">
        <SearchIcon className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-ink-faint" />
        <input
          aria-label="Search settings"
          placeholder="Search settings"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          className={`${inputClasses} pl-12`}
        />
      </div>
      {pages.length > 0 ? (
        <ListCard>
          {pages.map((page) => (
            <ListRow key={page.href} icon={page.icon} title={page.label} subtitle={page.description} href={page.href} />
          ))}
        </ListCard>
      ) : (
        <p className="text-ink-soft">No setting matches “{query}”.</p>
      )}
    </section>
  );
}
