"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import type { Alteration, OrderStatus, PaymentMode } from "@vaabhi/api-client";
import { ErrorNote, Fact, Pill, StatusPill } from "@/components/tailoring";
import {
  ListCard,
  LoadState,
  PageHeader,
  SectionLabel,
  SelectField,
  Sheet,
  TextField,
  buttonPrimary,
  buttonSecondary,
} from "@/components/ui";
import { api } from "@/lib/api";
import { mediaUrl } from "@/lib/photo-upload";
import { parseRupees, rupees } from "@/lib/settings";
import {
  ALTERATION_KINDS,
  PAYMENT_MODES,
  STATUS_LABELS,
  formatDate,
  formatPhone,
  idParam,
  isOpen,
  useAction,
  useLoad,
} from "@/lib/tailoring";

/** The next step from each open status. */
const NEXT: Partial<Record<OrderStatus, OrderStatus>> = {
  BOOKED: "IN_PROGRESS",
  IN_PROGRESS: "READY",
  READY: "DELIVERED",
};

const ACTION_LABELS: Partial<Record<OrderStatus, string>> = {
  IN_PROGRESS: "Start work",
  READY: "Mark ready",
  DELIVERED: "Hand over",
};

function AlterationView({ id }: { id: number }) {
  const alteration = useLoad(() => api.admin.alterations.get(id), [id]);
  const action = useAction();
  const [sheet, setSheet] = useState<"deliver" | "cancel" | null>(null);

  if (!alteration.value) {
    return (
      <section className="mx-auto max-w-3xl">
        <PageHeader title="Alteration" backHref="/alterations/" />
        <LoadState loading={alteration.loading} error={alteration.error} />
      </section>
    );
  }
  const a = alteration.value;
  const next = NEXT[a.status];
  const move = async (
    status: OrderStatus,
    collectRupees = 0,
    mode: PaymentMode | null = null,
    settlement: Settlement | null = null,
  ) => {
    const updated = await action.run(() =>
      api.admin.alterations.progress(id, { status, collectRupees, mode, settlement }),
    );
    if (updated) {
      alteration.setValue(updated);
      setSheet(null);
    }
  };

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader title={a.number} backHref="/alterations/" badge={<StatusPill status={a.status} />} />
      <div className="mb-6 flex flex-wrap items-center gap-3">
        <Link href={`/customers/view/?id=${a.client.id}`} className="text-lg font-medium text-accent-ink underline">
          {a.client.name}
        </Link>
        <a href={`tel:${a.client.phone}`} className="text-ink-soft">
          {formatPhone(a.client.phone)}
        </a>
        {next && (
          <button
            type="button"
            className={`${buttonPrimary} ml-auto`}
            disabled={action.busy}
            onClick={() => (next === "DELIVERED" && a.balanceRupees > 0 ? setSheet("deliver") : void move(next))}
          >
            {ACTION_LABELS[next]}
          </button>
        )}
      </div>
      <ErrorNote>{action.error}</ErrorNote>

      <dl className="mt-4 grid grid-cols-2 gap-4 rounded-2xl border border-line bg-panel p-4 sm:grid-cols-4">
        <Fact label="Ready by">{formatDate(a.readyBy)}</Fact>
        <Fact label="Charge">{a.paid ? rupees(a.totalRupees) : "Free"}</Fact>
        {a.paid && <Fact label="Due">{rupees(a.balanceRupees)}</Fact>}
        {a.orderId && a.orderNumber && (
          <Fact label="Order">
            <Link href={`/orders/view/?id=${a.orderId}`} className="text-accent-ink underline">
              {a.orderNumber}
            </Link>
          </Fact>
        )}
        {a.createdBy && <Fact label="Booked by">{a.createdBy}</Fact>}
      </dl>
      {a.notes && <p className="mt-3 whitespace-pre-line text-ink-soft">{a.notes}</p>}

      <SectionLabel>Garments</SectionLabel>
      <ListCard>
        {a.items.map((item) => (
          <div key={item.id} className="flex items-start gap-4 px-4 py-4">
            {item.photoKey && (
              // eslint-disable-next-line @next/next/no-img-element -- static export, R2 serves the resized file
              <img
                src={mediaUrl(item.photoKey)}
                alt={item.garment}
                className="size-14 rounded-xl border border-line object-cover"
              />
            )}
            <div className="min-w-0 flex-1">
              <p className="font-medium">
                {item.garment} <Pill>{ALTERATION_KINDS.find((k) => k.value === item.kind)?.label}</Pill>
              </p>
              {item.instructions && <p className="text-sm text-ink-soft">{item.instructions}</p>}
            </div>
            {a.paid && <span className="text-sm tabular-nums">{rupees(item.costRupees)}</span>}
          </div>
        ))}
      </ListCard>

      <SectionLabel>Terms accepted</SectionLabel>
      <p className="rounded-2xl border border-line bg-panel p-4 text-sm text-ink-soft">{a.undertaking}</p>

      <div className="mt-6 flex flex-wrap gap-2">
        <Link href={`/print/alteration/?id=${a.id}`} className={buttonSecondary}>
          Print slip
        </Link>
        {isOpen(a.status) && (
          <button type="button" className={`${buttonSecondary} text-bad`} onClick={() => setSheet("cancel")}>
            Cancel alteration
          </button>
        )}
      </div>

      {sheet === "deliver" && (
        <DeliverSheet
          alteration={a}
          busy={action.busy}
          error={action.error}
          onClose={() => setSheet(null)}
          onDeliver={(amount, mode) => void move("DELIVERED", amount, mode)}
        />
      )}
      {sheet === "cancel" && (
        <CancelSheet
          alteration={a}
          busy={action.busy}
          error={action.error}
          onClose={() => setSheet(null)}
          onCancel={(settlement, mode) => void move("CANCELLED", 0, mode, settlement)}
        />
      )}
    </section>
  );
}

type Settlement = "REFUND" | "KEEP";

const SETTLEMENTS: { value: Settlement; label: string }[] = [
  { value: "REFUND", label: "Give it back" },
  { value: "KEEP", label: "Keep it (work already done)" },
];

/** Cancels; when money was collected, the shop says whether it goes back and how. */
function CancelSheet({
  alteration,
  busy,
  error,
  onClose,
  onCancel,
}: {
  alteration: Alteration;
  busy: boolean;
  error: string | null;
  onClose: () => void;
  onCancel: (settlement: Settlement | null, mode: PaymentMode | null) => void;
}) {
  const collected = alteration.collectedRupees;
  const [settlement, setSettlement] = useState<Settlement>("REFUND");
  const [mode, setMode] = useState<PaymentMode>("CASH");
  return (
    <Sheet open title={`Cancel ${alteration.number}?`} onClose={onClose}>
      <div className="flex flex-col gap-4">
        <p className="text-ink-soft">
          It stays on the customer&apos;s record as {STATUS_LABELS.CANCELLED.toLowerCase()}.
        </p>
        {collected > 0 && (
          <>
            <SelectField
              label={`${rupees(collected)} was collected`}
              options={SETTLEMENTS}
              value={settlement}
              onChange={setSettlement}
            />
            {settlement === "REFUND" && (
              <SelectField label="Given back by" options={PAYMENT_MODES} value={mode} onChange={setMode} />
            )}
          </>
        )}
        <ErrorNote>{error}</ErrorNote>
        <div className="flex gap-2">
          <button
            type="button"
            className={`${buttonPrimary} bg-bad hover:bg-bad`}
            disabled={busy}
            onClick={() =>
              collected > 0 ? onCancel(settlement, settlement === "REFUND" ? mode : null) : onCancel(null, null)
            }
          >
            Cancel alteration
          </button>
          <button type="button" className={buttonSecondary} onClick={onClose}>
            Keep it
          </button>
        </div>
      </div>
    </Sheet>
  );
}

function DeliverSheet({
  alteration,
  busy,
  error,
  onClose,
  onDeliver,
}: {
  alteration: Alteration;
  busy: boolean;
  error: string | null;
  onClose: () => void;
  onDeliver: (collectRupees: number, mode: PaymentMode) => void;
}) {
  const [amount, setAmount] = useState(String(alteration.balanceRupees));
  const [mode, setMode] = useState<PaymentMode>("CASH");
  const collect = amount.trim() === "" ? 0 : parseRupees(amount);
  return (
    <Sheet open title="Hand over" onClose={onClose}>
      <div className="flex flex-col gap-4">
        <TextField
          label="Collected now (₹)"
          inputMode="decimal"
          value={amount}
          hint={`${rupees(alteration.balanceRupees)} due`}
          error={collect === null ? "Enter the amount as a number" : undefined}
          onChange={(event) => setAmount(event.target.value)}
        />
        <SelectField label="Paid by" options={PAYMENT_MODES} value={mode} onChange={setMode} />
        <ErrorNote>{error}</ErrorNote>
        <button
          type="button"
          className={buttonPrimary}
          disabled={busy || collect === null}
          onClick={() => collect !== null && onDeliver(collect, mode)}
        >
          {busy ? "Saving…" : "Hand over"}
        </button>
      </div>
    </Sheet>
  );
}

function Page() {
  const id = idParam(useSearchParams().get("id"));
  return id ? <AlterationView key={id} id={id} /> : <ErrorNote>No alteration selected.</ErrorNote>;
}

export default function AlterationPage() {
  return (
    <Suspense fallback={<p className="text-ink-soft">Loading…</p>}>
      <Page />
    </Suspense>
  );
}
