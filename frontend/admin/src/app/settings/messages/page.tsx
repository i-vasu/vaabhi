"use client";

import type { MessageTemplates } from "@vaabhi/api-client";
import { LoadState, PageHeader, SaveBar, SectionLabel, SegmentedControl, TextAreaField } from "@/components/ui";
import { useSection } from "@/lib/settings";

type TemplateKey = Exclude<keyof MessageTemplates, "channel">;

const templates: { key: TemplateKey; title: string }[] = [
  { key: "orderPlaced", title: "Order placed" },
  { key: "orderReady", title: "Order ready" },
  { key: "orderDelivered", title: "Order delivered" },
  { key: "paymentReceived", title: "Payment received" },
  { key: "reviewRequest", title: "Review request" },
  { key: "reward", title: "Reward coupon" },
];

const sample: Record<string, string> = {
  customer: "Priya",
  shop: "vaabhi",
  order: "ORD-2042",
  amount: "₹ 2,400",
  balance: "₹ 1,400",
  date: "12 Nov",
  link: "https://g.page/r/…",
  code: "RWD-7F3K",
};

/** Same rule as MessageTemplates.fill on the server: known placeholders are filled, others stay as typed. */
export function fillTemplate(template: string, values: Record<string, string> = sample): string {
  return template.replace(/\{([a-z]+)}/g, (match, name: string) => values[name] ?? match);
}

export default function MessageTemplatesPage() {
  const section = useSection("messages");
  const m = section.value;

  if (!m) {
    return (
      <section className="mx-auto max-w-3xl">
        <PageHeader title="Message templates" backHref="/settings/printing/" />
        <LoadState loading={section.loading} error={section.loadError} />
      </section>
    );
  }

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader title="Message templates" backHref="/settings/printing/" />
      <SegmentedControl
        label="Send by"
        options={[
          { value: "WHATSAPP", label: "WhatsApp" },
          { value: "SMS", label: "SMS" },
        ]}
        value={m.channel}
        onChange={(channel) => section.update({ channel })}
      />
      <p className="mt-3 text-sm text-ink-faint">
        Placeholders: {"{customer} {shop} {order} {amount} {balance} {date} {link} {code}"}
      </p>
      {templates.map((template) => (
        <div key={template.key}>
          <SectionLabel>{template.title}</SectionLabel>
          <TextAreaField
            label={`${template.title} message`}
            maxLength={1000}
            value={m[template.key]}
            onChange={(event) => section.update({ [template.key]: event.target.value })}
            error={section.fieldError(template.key)}
            hint={<>Preview: {fillTemplate(m[template.key])}</>}
          />
        </div>
      ))}
      <SaveBar saving={section.saving} saved={section.saved} error={section.error} onSave={() => void section.save()} />
    </section>
  );
}
