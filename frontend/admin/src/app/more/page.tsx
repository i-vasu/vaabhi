"use client";

import { PageHeader, ListCard, ListRow, SectionLabel } from "@/components/ui";
import { moreSections } from "@/lib/nav";

/** Everything that is not in the main navigation, grouped. */
export default function MorePage() {
  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader title="More" />
      {moreSections.map((section) => (
        <div key={section.title}>
          <SectionLabel>{section.title}</SectionLabel>
          <ListCard>
            {section.items.map((item) => (
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
      ))}
    </section>
  );
}
