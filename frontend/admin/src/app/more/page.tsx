"use client";

import { useStaff } from "@/components/app-shell";
import { ListCard, ListRow, PageHeader, SectionLabel } from "@/components/ui";
import { canSee, moreSections } from "@/lib/nav";

/** Everything that is not in the main navigation, grouped; only what the signed-in person may use. */
export default function MorePage() {
  const staff = useStaff();
  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader title="More" />
      {moreSections.map((section) => {
        const items = section.items.filter((item) => canSee(item, staff));
        if (items.length === 0) return null;
        return (
          <div key={section.title}>
            <SectionLabel>{section.title}</SectionLabel>
            <ListCard>
              {items.map((item) => (
                <ListRow
                  key={item.href}
                  icon={item.icon}
                  title={item.label}
                  subtitle={item.description}
                  href={item.href}
                />
              ))}
            </ListCard>
          </div>
        );
      })}
    </section>
  );
}
