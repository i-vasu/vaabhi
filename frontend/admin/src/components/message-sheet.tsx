"use client";

import { useState } from "react";
import type { Message, MessagePurpose } from "@vaabhi/api-client";
import { api } from "@/lib/api";
import { PURPOSES } from "@/lib/extras";
import { useAction } from "@/lib/tailoring";
import { ErrorNote } from "./tailoring";
import { SegmentedControl, SelectField, Sheet, TextAreaField, TextField, buttonPrimary, buttonSecondary } from "./ui";

/**
 * Prepare a WhatsApp / SMS for a customer from the shop's templates. Without a messaging provider it opens the
 * message in WhatsApp (or the SMS app) ready to send.
 */
export function MessageSheet({
  orderId,
  clientId,
  onClose,
  open: openLink = (url) => window.open(url, "_blank", "noopener"),
}: {
  orderId?: number;
  clientId: number;
  onClose: () => void;
  open?: (url: string) => void;
}) {
  const purposes = PURPOSES.filter((p) => orderId || (!p.value.startsWith("ORDER") && p.value !== "PAYMENT"));
  const [purpose, setPurpose] = useState<MessagePurpose>(purposes[0]!.value);
  const [channel, setChannel] = useState<"WHATSAPP" | "SMS">("WHATSAPP");
  const [code, setCode] = useState("");
  const [body, setBody] = useState("");
  const [sent, setSent] = useState<Message | null>(null);
  const action = useAction();

  return (
    <Sheet open title="Message the customer" onClose={onClose}>
      {sent ? (
        <div className="flex flex-col gap-4">
          <p className="whitespace-pre-line rounded-xl bg-panel-2 p-3 text-sm">{sent.body}</p>
          {sent.link ? (
            <a href={sent.link} target="_blank" rel="noopener noreferrer" className={buttonPrimary}>
              Open in {sent.channel === "WHATSAPP" ? "WhatsApp" : "Messages"}
            </a>
          ) : (
            <p className={sent.status === "FAILED" ? "text-bad" : "text-ok"}>
              {sent.status === "FAILED" ? `Not sent: ${sent.error}` : "Sent"}
            </p>
          )}
          <button type="button" className={buttonSecondary} onClick={onClose}>
            Done
          </button>
        </div>
      ) : (
        <div className="flex flex-col gap-4">
          <SelectField label="Message" options={purposes} value={purpose} onChange={setPurpose} />
          <SegmentedControl
            label="Send by"
            options={[
              { value: "WHATSAPP", label: "WhatsApp" },
              { value: "SMS", label: "SMS" },
            ]}
            value={channel}
            onChange={setChannel}
          />
          {purpose === "REWARD" && (
            <TextField label="Coupon code" maxLength={40} value={code} onChange={(e) => setCode(e.target.value)} />
          )}
          {purpose === "CUSTOM" && (
            <TextAreaField
              label="Your message"
              maxLength={2000}
              value={body}
              onChange={(e) => setBody(e.target.value)}
            />
          )}
          <ErrorNote>{action.error}</ErrorNote>
          <button
            type="button"
            className={buttonPrimary}
            disabled={action.busy}
            onClick={async () => {
              const message = await action.run(() =>
                api.admin.engage.prepareMessage({
                  purpose,
                  orderId: orderId ?? null,
                  clientId,
                  code: code.trim() || null,
                  body: body.trim() || null,
                  channel,
                }),
              );
              if (message) {
                setSent(message);
                if (message.link) openLink(message.link);
              }
            }}
          >
            {action.busy ? "Preparing…" : "Prepare message"}
          </button>
        </div>
      )}
    </Sheet>
  );
}
