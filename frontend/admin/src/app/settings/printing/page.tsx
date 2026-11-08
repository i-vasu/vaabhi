"use client";

import { ChatIcon, ImageIcon, LayoutIcon, PrinterIcon } from "@/components/icons";
import { ListCard, ListRow, PageHeader } from "@/components/ui";

export default function PrintingPage() {
  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader title="Printing & messages" backHref="/settings/" />
      <ListCard>
        <ListRow
          icon={PrinterIcon}
          title="Print formats"
          subtitle="What the Print button offers"
          href="/settings/print/"
        />
        <ListRow
          icon={LayoutIcon}
          title="Form layouts"
          subtitle="Which sections each form shows"
          href="/settings/print/#layout"
        />
        <ListRow
          icon={ChatIcon}
          title="Message templates"
          subtitle="WhatsApp & SMS wording"
          href="/settings/messages/"
        />
        <ListRow
          icon={ImageIcon}
          title="Gallery watermark"
          subtitle="Brand your design photos"
          href="/settings/watermark/"
        />
      </ListCard>
    </section>
  );
}
