"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import type { Order, PaymentMode, ProductionStage } from "@vaabhi/api-client";
import { ErrorNote, Fact, Pill, StatusPill } from "@/components/tailoring";
import {
  ListCard,
  LoadState,
  PageHeader,
  SectionLabel,
  SegmentedControl,
  SelectField,
  Sheet,
  TextAreaField,
  TextField,
  buttonPrimary,
  buttonSecondary,
} from "@/components/ui";
import { api } from "@/lib/api";
import { mediaUrl } from "@/lib/photo-upload";
import { parseRupees, rupees } from "@/lib/settings";
import { PAYMENT_MODES, formatDate, formatPhone, idParam, isOpen, useAction, useLoad } from "@/lib/tailoring";

const dateTime = new Intl.DateTimeFormat("en-IN", {
  day: "numeric",
  month: "short",
  hour: "numeric",
  minute: "2-digit",
});

function OrderView({ id }: { id: number }) {
  const order = useLoad(() => api.admin.orders.get(id), [id]);
  const stages = useLoad(() => api.admin.settings.stages(), []);
  const action = useAction();
  const [sheet, setSheet] = useState<"payment" | "schedule" | "cancel" | null>(null);

  if (!order.value) {
    return (
      <section className="mx-auto max-w-3xl">
        <PageHeader title="Order" backHref="/orders/" />
        <LoadState loading={order.loading} error={order.error} />
      </section>
    );
  }
  const o = order.value;
  const open = isOpen(o.status);
  const apply = async (call: () => Promise<Order>) => {
    const next = await action.run(call);
    if (next) {
      order.setValue(next);
      setSheet(null);
    }
  };

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader title={o.number} backHref="/orders/" badge={<StatusPill status={o.status} />} />

      <div className="mb-6 flex flex-wrap items-center gap-3">
        <Link href={`/customers/view/?id=${o.client.id}`} className="text-lg font-medium text-accent-ink underline">
          {o.client.name}
        </Link>
        <a href={`tel:${o.client.phone}`} className="text-ink-soft">
          {formatPhone(o.client.phone)}
        </a>
        <div className="ml-auto flex flex-wrap gap-2">
          {o.status !== "CANCELLED" && (
            <button type="button" className={buttonPrimary} onClick={() => setSheet("payment")}>
              {o.balancePaise > 0 ? "Take payment" : "Payment / refund"}
            </button>
          )}
          {open && (
            <button
              type="button"
              className={buttonSecondary}
              disabled={action.busy}
              onClick={() => void apply(() => api.admin.orders.deliver(id))}
            >
              Mark delivered
            </button>
          )}
        </div>
      </div>
      <ErrorNote>{action.error}</ErrorNote>

      <dl className="mt-4 grid grid-cols-2 gap-4 rounded-2xl border border-line bg-panel p-4 sm:grid-cols-4">
        <Fact label="Booked">{formatDate(o.orderDate)}</Fact>
        <Fact label="Delivery">
          {formatDate(o.deliveryDate)}
          {o.dueTime && ` · ${o.dueTime.slice(0, 5)}`}
        </Fact>
        {o.trialDate && <Fact label="Trial">{formatDate(o.trialDate)}</Fact>}
        {o.createdBy && <Fact label="Booked by">{o.createdBy}</Fact>}
        {o.deliveredAt && <Fact label="Delivered">{dateTime.format(new Date(o.deliveredAt))}</Fact>}
      </dl>
      {o.notes && <p className="mt-3 whitespace-pre-line text-ink-soft">{o.notes}</p>}

      <SectionLabel
        action={
          open && stages.value ? (
            <StageSelect
              label="Move all items to"
              stages={stages.value}
              value={null}
              disabled={action.busy}
              onChange={(stageId) => void apply(() => api.admin.orders.moveItems(id, { stageId, itemIds: [] }))}
            />
          ) : undefined
        }
      >
        Items
      </SectionLabel>
      <ListCard>
        {o.items.map((item) => (
          <div key={item.id} className="flex flex-wrap items-start gap-4 px-4 py-4">
            {item.clothPhotoKey && (
              // eslint-disable-next-line @next/next/no-img-element -- static export, R2 serves the resized file
              <img
                src={mediaUrl(item.clothPhotoKey)}
                alt={`Cloth for ${item.description}`}
                className="size-14 rounded-xl border border-line object-cover"
              />
            )}
            <div className="min-w-0 flex-1">
              <p className="font-medium">
                {item.quantity > 1 && `${item.quantity} × `}
                {item.description}
                {item.colour && <span className="text-ink-soft"> · {item.colour}</span>}
              </p>
              <p className="text-sm text-ink-soft">
                {rupees(item.pricePaise * item.quantity)}
                {item.note && ` · ${item.note}`}
              </p>
              {item.measurements && (
                <p className="mt-1 text-sm text-ink-faint">
                  {Object.entries(item.measurements)
                    .map(([key, value]) => `${key.replace(/_/g, " ")} ${value}`)
                    .join(" · ")}
                </p>
              )}
            </div>
            {open && stages.value ? (
              <StageSelect
                label={`Stage of ${item.description}`}
                stages={stages.value}
                value={item.stage?.id ?? null}
                disabled={action.busy}
                onChange={(stageId) =>
                  void apply(() => api.admin.orders.moveItems(id, { stageId, itemIds: [item.id] }))
                }
              />
            ) : (
              item.stage && <Pill>{item.stage.name}</Pill>
            )}
          </div>
        ))}
      </ListCard>

      <SectionLabel>Money</SectionLabel>
      <dl className="rounded-2xl border border-line bg-panel p-4 text-sm">
        <Line label="Items" value={rupees(o.itemsPaise)} />
        {o.deliveryChargePaise > 0 && <Line label="Delivery" value={rupees(o.deliveryChargePaise)} />}
        {o.discountPaise > 0 && (
          <Line
            label={o.discountKind === "PERCENT" ? `Discount (${o.discountValue / 100}%)` : "Discount"}
            value={`− ${rupees(o.discountPaise)}`}
          />
        )}
        {o.couponCode && <Line label="Coupon" value={o.couponCode} />}
        <Line label="Total" value={rupees(o.totalPaise)} strong />
        <Line label="Paid" value={rupees(o.paidPaise)} />
        <Line label={o.balancePaise < 0 ? "To refund" : "Balance"} value={rupees(Math.abs(o.balancePaise))} strong />
      </dl>
      {o.payments.length > 0 && (
        <ul className="mt-3 divide-y divide-line rounded-2xl border border-line bg-panel text-sm">
          {o.payments.map((payment) => (
            <li key={payment.id} className="flex flex-wrap gap-x-3 px-4 py-3">
              <span className={payment.amountPaise < 0 ? "text-bad" : "text-ok"}>
                {payment.amountPaise < 0 ? "− " : ""}
                {rupees(Math.abs(payment.amountPaise))}
              </span>
              <span>{payment.kind === "ADVANCE" ? "Advance" : payment.kind === "REFUND" ? "Refund" : "Payment"}</span>
              <span className="text-ink-soft">{PAYMENT_MODES.find((m) => m.value === payment.mode)?.label}</span>
              <span className="ml-auto text-ink-faint">
                {dateTime.format(new Date(payment.receivedAt))}
                {payment.receivedBy && ` · ${payment.receivedBy}`}
              </span>
              {payment.note && <span className="basis-full text-ink-soft">{payment.note}</span>}
            </li>
          ))}
        </ul>
      )}

      <div className="mt-6 flex flex-wrap gap-2">
        {open && (
          <button type="button" className={buttonSecondary} onClick={() => setSheet("schedule")}>
            Change dates
          </button>
        )}
        <Link href={`/alterations/new/?clientId=${o.client.id}&orderId=${o.id}`} className={buttonSecondary}>
          Book alteration
        </Link>
        {open && (
          <button type="button" className={`${buttonSecondary} text-bad`} onClick={() => setSheet("cancel")}>
            Cancel order
          </button>
        )}
      </div>

      {sheet === "payment" && (
        <PaymentSheet
          order={o}
          busy={action.busy}
          error={action.error}
          onClose={() => setSheet(null)}
          onSubmit={(payment) => void apply(() => api.admin.orders.addPayment(id, payment))}
        />
      )}
      {sheet === "schedule" && (
        <ScheduleSheet
          order={o}
          busy={action.busy}
          error={action.error}
          onClose={() => setSheet(null)}
          onSubmit={(schedule) => void apply(() => api.admin.orders.reschedule(id, schedule))}
        />
      )}
      {sheet === "cancel" && (
        <Sheet open title={`Cancel ${o.number}?`} onClose={() => setSheet(null)}>
          <p className="text-ink-soft">
            The order stays on the customer&apos;s record as cancelled.
            {o.paidPaise > 0 && ` ${rupees(o.paidPaise)} was paid; record a refund afterwards if you give it back.`}
          </p>
          <ErrorNote>{action.error}</ErrorNote>
          <div className="mt-4 flex gap-2">
            <button
              type="button"
              className={`${buttonPrimary} bg-bad hover:bg-bad`}
              disabled={action.busy}
              onClick={() => void apply(() => api.admin.orders.cancel(id))}
            >
              Cancel order
            </button>
            <button type="button" className={buttonSecondary} onClick={() => setSheet(null)}>
              Keep it
            </button>
          </div>
        </Sheet>
      )}
    </section>
  );
}

function Line({ label, value, strong = false }: { label: string; value: string; strong?: boolean }) {
  return (
    <div
      className={strong ? "mt-1 flex justify-between text-base font-semibold" : "flex justify-between text-ink-soft"}
    >
      <dt>{label}</dt>
      <dd className="tabular-nums">{value}</dd>
    </div>
  );
}

function StageSelect({
  label,
  stages,
  value,
  disabled,
  onChange,
}: {
  label: string;
  stages: ProductionStage[];
  value: number | null;
  disabled: boolean;
  onChange: (stageId: number) => void;
}) {
  return (
    <select
      aria-label={label}
      disabled={disabled}
      value={value === null ? "" : String(value)}
      onChange={(event) => event.target.value && onChange(Number(event.target.value))}
      className="rounded-xl border border-line-strong bg-field px-3 py-2 text-sm"
    >
      {value === null && <option value="">{label}…</option>}
      {stages.map((stage) => (
        <option key={stage.id} value={String(stage.id)}>
          {stage.name}
        </option>
      ))}
    </select>
  );
}

function PaymentSheet({
  order,
  busy,
  error,
  onClose,
  onSubmit,
}: {
  order: Order;
  busy: boolean;
  error: string | null;
  onClose: () => void;
  onSubmit: (payment: { amountPaise: number; mode: PaymentMode; refund: boolean; note: string | null }) => void;
}) {
  const [refund, setRefund] = useState(order.balancePaise < 0);
  const [amount, setAmount] = useState(order.balancePaise !== 0 ? String(Math.abs(order.balancePaise) / 100) : "");
  const [mode, setMode] = useState<PaymentMode>("CASH");
  const [note, setNote] = useState("");
  const [invalid, setInvalid] = useState<string | null>(null);

  return (
    <Sheet open title={refund ? "Record refund" : "Take payment"} onClose={onClose}>
      <div className="flex flex-col gap-4">
        <SegmentedControl
          label="Payment or refund"
          options={[
            { value: "pay", label: "Payment" },
            { value: "refund", label: "Refund" },
          ]}
          value={refund ? "refund" : "pay"}
          onChange={(value) => setRefund(value === "refund")}
        />
        <TextField
          label="Amount (₹)"
          inputMode="decimal"
          value={amount}
          error={invalid ?? undefined}
          hint={order.balancePaise > 0 ? `${rupees(order.balancePaise)} due` : undefined}
          onChange={(event) => setAmount(event.target.value)}
        />
        <SelectField label="Paid by" options={PAYMENT_MODES} value={mode} onChange={setMode} />
        <TextField label="Note" maxLength={200} value={note} onChange={(event) => setNote(event.target.value)} />
        <ErrorNote>{error}</ErrorNote>
        <button
          type="button"
          className={buttonPrimary}
          disabled={busy}
          onClick={() => {
            const amountPaise = parseRupees(amount);
            if (!amountPaise) {
              setInvalid("Enter the amount");
              return;
            }
            setInvalid(null);
            onSubmit({ amountPaise, mode, refund, note: note.trim() || null });
          }}
        >
          {busy ? "Saving…" : refund ? "Record refund" : "Record payment"}
        </button>
      </div>
    </Sheet>
  );
}

function ScheduleSheet({
  order,
  busy,
  error,
  onClose,
  onSubmit,
}: {
  order: Order;
  busy: boolean;
  error: string | null;
  onClose: () => void;
  onSubmit: (schedule: {
    deliveryDate: string;
    dueTime: string | null;
    trialDate: string | null;
    notes: string | null;
  }) => void;
}) {
  const [deliveryDate, setDeliveryDate] = useState(order.deliveryDate);
  const [dueTime, setDueTime] = useState(order.dueTime?.slice(0, 5) ?? "");
  const [trialDate, setTrialDate] = useState(order.trialDate ?? "");
  const [notes, setNotes] = useState(order.notes ?? "");
  return (
    <Sheet open title="Change dates" onClose={onClose}>
      <div className="flex flex-col gap-4">
        <div className="grid grid-cols-2 gap-3">
          <TextField
            label="Delivery date"
            type="date"
            min={order.orderDate}
            value={deliveryDate}
            onChange={(event) => setDeliveryDate(event.target.value)}
          />
          <TextField
            label="Due time"
            type="time"
            value={dueTime}
            onChange={(event) => setDueTime(event.target.value)}
          />
        </div>
        <TextField
          label="Trial date"
          type="date"
          value={trialDate}
          onChange={(event) => setTrialDate(event.target.value)}
        />
        <TextAreaField
          label="Notes"
          maxLength={2000}
          value={notes}
          onChange={(event) => setNotes(event.target.value)}
        />
        <ErrorNote>{error}</ErrorNote>
        <button
          type="button"
          className={buttonPrimary}
          disabled={busy || !deliveryDate}
          onClick={() =>
            onSubmit({
              deliveryDate,
              dueTime: dueTime || null,
              trialDate: trialDate || null,
              notes: notes.trim() || null,
            })
          }
        >
          {busy ? "Saving…" : "Save dates"}
        </button>
      </div>
    </Sheet>
  );
}

function Page() {
  const id = idParam(useSearchParams().get("id"));
  return id ? <OrderView key={id} id={id} /> : <ErrorNote>No order selected.</ErrorNote>;
}

export default function OrderPage() {
  return (
    <Suspense fallback={<p className="text-ink-soft">Loading…</p>}>
      <Page />
    </Suspense>
  );
}
