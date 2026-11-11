"use client";

import Link from "next/link";
import { ChatIcon } from "@/components/icons";
import { Pill } from "@/components/tailoring";
import { EmptyState, LoadState, PageHeader } from "@/components/ui";
import { api } from "@/lib/api";
import { purposeLabel } from "@/lib/extras";
import { formatPhone, useLoad } from "@/lib/tailoring";

const when = new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", hour: "numeric", minute: "2-digit" });

/** Every WhatsApp prepared or sent to customers, newest first. */
export default function MessagesPage() {
  const messages = useLoad(() => api.admin.engage.messages(), []);
  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader title="Messages" backHref="/more/" />
      <p className="mb-4 text-sm text-ink-soft">
        Send one from an order or a customer&apos;s page. Without a WhatsApp provider set up, each message opens in
        WhatsApp on the shop&apos;s phone ready to send.
      </p>
      {!messages.value ? (
        <LoadState loading={messages.loading} error={messages.error} />
      ) : messages.value.length === 0 ? (
        <EmptyState icon={ChatIcon} title="No messages yet" />
      ) : (
        <ul className="divide-y divide-line rounded-2xl border border-line bg-panel">
          {messages.value.map((m) => (
            <li key={m.id} className="px-4 py-3">
              <div className="flex flex-wrap items-center gap-2 text-sm">
                <span className="font-medium">{m.clientName ?? formatPhone(m.toPhone)}</span>
                <Pill>{purposeLabel(m.purpose)}</Pill>
                {m.orderId && (
                  <Link href={`/orders/view/?id=${m.orderId}`} className="text-accent-ink underline">
                    {m.orderNumber}
                  </Link>
                )}
                <span className="ml-auto text-ink-faint">
                  {when.format(new Date(m.createdAt))}
                  {m.createdBy && ` · ${m.createdBy}`}
                </span>
              </div>
              <p className="mt-1 text-sm text-ink-soft">{m.body}</p>
              {m.link && (
                <a
                  href={m.link}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-sm text-accent-ink underline"
                >
                  Send again
                </a>
              )}
              {m.status === "FAILED" && <p className="text-sm text-bad">Not sent: {m.error}</p>}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
