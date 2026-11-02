"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import type { Client, GarmentTemplate, Measurement } from "@vaabhi/api-client";
import { PlusIcon, RulerIcon } from "@/components/icons";
import { MessageSheet } from "@/components/message-sheet";
import { RewardsSection } from "@/components/rewards-section";
import { ErrorNote, Fact, Pill, StatusPill } from "@/components/tailoring";
import {
  ListCard,
  ListRow,
  LoadState,
  PageHeader,
  SectionLabel,
  SelectField,
  Sheet,
  StatTile,
  TextField,
  buttonPrimary,
  buttonSecondary,
} from "@/components/ui";
import { api } from "@/lib/api";
import { mediaUrl } from "@/lib/photo-upload";
import { rupees } from "@/lib/settings";
import { formatDate, formatPhone, idParam, useAction, useLoad } from "@/lib/tailoring";

function CustomerView({ id }: { id: number }) {
  const client = useLoad(() => api.admin.clients.get(id), [id]);
  const orders = useLoad(() => api.admin.orders.list({ clientId: id, pageSize: 10 }), [id]);
  const alterations = useLoad(() => api.admin.alterations.list({ clientId: id, filter: "ALL", pageSize: 10 }), [id]);
  const [measuring, setMeasuring] = useState(false);
  const [messaging, setMessaging] = useState(false);

  if (!client.value) {
    return (
      <section className="mx-auto max-w-3xl">
        <PageHeader title="Customer" backHref="/customers/" />
        <LoadState loading={client.loading} error={client.error} />
      </section>
    );
  }
  const c = client.value;
  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader
        title={[c.firstName, c.lastName].filter(Boolean).join(" ")}
        backHref="/customers/"
        badge={c.clientType !== "RETAIL" ? c.clientType : undefined}
        actions={
          <Link href={`/customers/edit/?id=${id}`} className={buttonSecondary}>
            Edit
          </Link>
        }
      />
      <div className="mb-6 flex flex-wrap items-center gap-4">
        {c.photoKey && (
          // eslint-disable-next-line @next/next/no-img-element -- static export, R2 serves the resized file
          <img src={mediaUrl(c.photoKey)} alt="" className="size-16 rounded-2xl border border-line object-cover" />
        )}
        <a href={`tel:${c.phone}`} className="text-lg font-medium text-accent-ink underline">
          {formatPhone(c.phone)}
        </a>
        <div className="ml-auto flex gap-2">
          <Link href={`/orders/new/?clientId=${id}`} className={buttonPrimary}>
            <PlusIcon size={18} /> New order
          </Link>
          <Link href={`/alterations/new/?clientId=${id}`} className={buttonSecondary}>
            Alteration
          </Link>
          <button type="button" className={buttonSecondary} onClick={() => setMessaging(true)}>
            Message
          </button>
        </div>
      </div>

      <div className="grid grid-cols-3 gap-3">
        <StatTile label="Orders" value={c.orderCount} />
        <StatTile label="Spent" value={rupees(c.spentPaise)} />
        <StatTile label="Due" value={rupees(c.outstandingPaise)} tone={c.outstandingPaise > 0 ? "warn" : "default"} />
      </div>

      <Details client={c} />

      <SectionLabel
        action={
          <button type="button" className="text-sm text-accent-ink underline" onClick={() => setMeasuring(true)}>
            + Add measurement
          </button>
        }
      >
        Measurements
      </SectionLabel>
      <Measurements measurements={c.measurements} />

      <RewardsSection clientId={id} />

      <SectionLabel>Orders</SectionLabel>
      <ErrorNote>{orders.error}</ErrorNote>
      {orders.value && orders.value.items.length === 0 && <p className="text-ink-soft">No orders yet.</p>}
      {orders.value && orders.value.items.length > 0 && (
        <ListCard>
          {orders.value.items.map((order) => (
            <ListRow
              key={order.id}
              href={`/orders/view/?id=${order.id}`}
              title={
                <>
                  {order.number} <StatusPill status={order.status} overdue={order.overdue} />
                </>
              }
              subtitle={`${order.itemsLabel} · due ${formatDate(order.deliveryDate)}`}
              trailing={order.balancePaise > 0 ? `${rupees(order.balancePaise)} due` : rupees(order.totalPaise)}
            />
          ))}
        </ListCard>
      )}
      {orders.value && orders.value.total > orders.value.items.length && (
        <Link href={`/orders/?clientId=${id}`} className="mt-2 inline-block text-sm text-accent-ink underline">
          All {orders.value.total} orders
        </Link>
      )}

      <SectionLabel>Alterations</SectionLabel>
      <ErrorNote>{alterations.error}</ErrorNote>
      {alterations.value && alterations.value.items.length === 0 && <p className="text-ink-soft">No alterations.</p>}
      {alterations.value && alterations.value.items.length > 0 && (
        <ListCard>
          {alterations.value.items.map((alteration) => (
            <ListRow
              key={alteration.id}
              href={`/alterations/view/?id=${alteration.id}`}
              title={
                <>
                  {alteration.number} <StatusPill status={alteration.status} overdue={alteration.overdue} />
                </>
              }
              subtitle={`${alteration.itemsLabel} · ready by ${formatDate(alteration.readyBy)}`}
              trailing={alteration.paid ? rupees(alteration.totalPaise) : "Free"}
            />
          ))}
        </ListCard>
      )}

      {messaging && <MessageSheet clientId={id} onClose={() => setMessaging(false)} />}
      {measuring && (
        <MeasurementSheet
          clientId={id}
          latest={c.measurements}
          onClose={() => setMeasuring(false)}
          onSaved={() => {
            setMeasuring(false);
            client.reload();
          }}
        />
      )}
    </section>
  );
}

function Details({ client: c }: { client: Client }) {
  const address = [c.address, c.city, c.state, c.pinCode].filter(Boolean).join(", ");
  const facts: [string, string | null][] = [
    ["WhatsApp", c.whatsapp && formatPhone(c.whatsapp)],
    ["Alternate", c.altPhone && formatPhone(c.altPhone)],
    ["Email", c.email],
    ["Address", address || null],
    ["GSTIN", c.gstin],
    ["Birthday", c.birthDate && formatDate(c.birthDate)],
    [
      "Occasion",
      c.occasionName && [c.occasionName, c.occasionDate && formatDate(c.occasionDate)].filter(Boolean).join(" · "),
    ],
    ["Preferred style", c.preferredStyle],
  ];
  const shown = facts.filter(([, value]) => value);
  if (shown.length === 0) return null;
  return (
    <dl className="mt-6 grid gap-4 rounded-2xl border border-line bg-panel p-4 sm:grid-cols-2">
      {shown.map(([label, value]) => (
        <Fact key={label} label={label}>
          {value}
        </Fact>
      ))}
    </dl>
  );
}

/** Newest measurement per garment first; older sets are listed under it. */
function Measurements({ measurements }: { measurements: Measurement[] }) {
  if (measurements.length === 0) {
    return <p className="text-ink-soft">No measurements yet. They are copied onto each new order.</p>;
  }
  return (
    <div className="flex flex-col gap-3">
      {measurements.map((m, index) => {
        const older = measurements.slice(0, index).some((other) => other.templateId === m.templateId);
        return (
          <div key={m.id} className="rounded-2xl border border-line bg-panel p-4">
            <div className="mb-2 flex flex-wrap items-center gap-2">
              <RulerIcon size={18} className="text-accent-ink" />
              <span className="font-medium">{m.garment}</span>
              {older && <Pill>Older</Pill>}
              <span className="ml-auto text-sm text-ink-soft">
                {formatDate(m.takenAt)}
                {m.takenBy && ` · ${m.takenBy}`}
              </span>
            </div>
            <dl className="grid grid-cols-2 gap-x-4 gap-y-1 text-sm sm:grid-cols-4">
              {Object.entries(m.values).map(([key, value]) => (
                <div key={key} className="flex justify-between gap-2">
                  <dt className="text-ink-soft">{key.replace(/_/g, " ")}</dt>
                  <dd className="font-medium tabular-nums">{value}</dd>
                </div>
              ))}
            </dl>
            {m.notes && <p className="mt-2 text-sm text-ink-soft">{m.notes}</p>}
          </div>
        );
      })}
    </div>
  );
}

function MeasurementSheet({
  clientId,
  latest,
  onClose,
  onSaved,
}: {
  clientId: number;
  latest: Measurement[];
  onClose: () => void;
  onSaved: () => void;
}) {
  const templates = useLoad(() => api.admin.settings.garmentTemplates(), []);
  const [templateId, setTemplateId] = useState<number | null>(null);
  const [values, setValues] = useState<Record<string, string>>({});
  const [notes, setNotes] = useState("");
  const action = useAction();

  const list: GarmentTemplate[] = templates.value ?? [];
  const template = list.find((t) => t.id === (templateId ?? list[0]?.id));
  // Start from the last measurements for the garment, so only what changed needs typing.
  const lastFor = (id: number | null | undefined) => latest.find((m) => m.templateId === id)?.values ?? {};
  const shown = templateId === null ? lastFor(template?.id) : values;

  function pick(id: number) {
    setTemplateId(id);
    setValues(lastFor(id));
  }

  function setValue(key: string, value: string) {
    setTemplateId(template!.id!);
    setValues({ ...shown, [key]: value });
  }

  return (
    <Sheet open title="Add measurement" onClose={onClose}>
      {!template ? (
        <LoadState loading={templates.loading} error={templates.error} />
      ) : (
        <div className="flex flex-col gap-4">
          <SelectField
            label="Garment"
            options={list.map((t) => ({ value: String(t.id), label: t.name }))}
            value={String(template.id)}
            onChange={(value) => pick(Number(value))}
          />
          <div className="grid grid-cols-2 gap-3">
            {template.fields.map((field) => (
              <TextField
                key={field.key}
                label={field.unit ? `${field.label} (${field.unit})` : field.label}
                inputMode={field.unit ? "decimal" : "text"}
                maxLength={40}
                value={shown[field.key] ?? ""}
                onChange={(event) => setValue(field.key, event.target.value)}
              />
            ))}
          </div>
          <TextField label="Notes" maxLength={500} value={notes} onChange={(event) => setNotes(event.target.value)} />
          <ErrorNote>{action.error}</ErrorNote>
          <button
            type="button"
            className={buttonPrimary}
            disabled={action.busy}
            onClick={async () => {
              const saved = await action.run(() =>
                api.admin.clients.addMeasurement(clientId, {
                  templateId: template.id!,
                  values: Object.fromEntries(
                    Object.entries(shown).filter(([key]) => template.fields.some((f) => f.key === key)),
                  ),
                  notes: notes.trim() || null,
                }),
              );
              if (saved) onSaved();
            }}
          >
            {action.busy ? "Saving…" : "Save measurement"}
          </button>
        </div>
      )}
    </Sheet>
  );
}

function Page() {
  const id = idParam(useSearchParams().get("id"));
  return id ? <CustomerView key={id} id={id} /> : <ErrorNote>No customer selected.</ErrorNote>;
}

export default function CustomerPage() {
  return (
    <Suspense fallback={<p className="text-ink-soft">Loading…</p>}>
      <Page />
    </Suspense>
  );
}
