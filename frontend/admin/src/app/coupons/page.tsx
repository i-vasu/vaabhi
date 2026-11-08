"use client";

import { useState } from "react";
import type { Coupon, CouponForm } from "@vaabhi/api-client";
import { BadgeIcon, PlusIcon } from "@/components/icons";
import { ErrorNote, Pill } from "@/components/tailoring";
import {
  EmptyState,
  ListCard,
  ListRow,
  LoadState,
  PageHeader,
  SegmentedControl,
  Sheet,
  TextField,
  ToggleRow,
  buttonPrimary,
} from "@/components/ui";
import { api } from "@/lib/api";
import { parseRupees, rupees } from "@/lib/settings";
import { formatDate, parsePercent, useAction, useLoad } from "@/lib/tailoring";

function couponValue(c: Pick<Coupon, "kind" | "value">): string {
  return c.kind === "PERCENT" ? `${c.value}% off` : `${rupees(c.value)} off`;
}

export default function CouponsPage() {
  const coupons = useLoad(() => api.admin.engage.coupons(), []);
  const [editing, setEditing] = useState<{ coupon: Coupon | null } | null>(null);

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader
        title="Coupons"
        actions={
          <button type="button" className={buttonPrimary} onClick={() => setEditing({ coupon: null })}>
            <PlusIcon size={18} /> New coupon
          </button>
        }
      />
      <p className="mb-4 text-ink-soft">Counter staff apply a code on New order; it fills in the discount.</p>
      {!coupons.value ? (
        <LoadState loading={coupons.loading} error={coupons.error} />
      ) : coupons.value.length === 0 ? (
        <EmptyState icon={BadgeIcon} title="No coupons yet" />
      ) : (
        <ListCard>
          {coupons.value.map((c) => (
            <button
              key={c.id}
              type="button"
              className="block w-full text-left hover:bg-panel-2"
              onClick={() => setEditing({ coupon: c })}
            >
              <ListRow
                title={
                  <>
                    {c.code} {!c.active && <Pill tone="bad">Off</Pill>}
                  </>
                }
                subtitle={[
                  couponValue(c),
                  c.minOrderRupees > 0 && `on ${rupees(c.minOrderRupees)}+`,
                  c.validTo && `until ${formatDate(c.validTo)}`,
                  c.description,
                ]
                  .filter(Boolean)
                  .join(" · ")}
                trailing={`${c.uses}${c.maxUses ? ` / ${c.maxUses}` : ""} used`}
              />
            </button>
          ))}
        </ListCard>
      )}
      {editing && (
        <CouponSheet
          coupon={editing.coupon}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            coupons.reload();
          }}
        />
      )}
    </section>
  );
}

function CouponSheet({
  coupon,
  onClose,
  onSaved,
}: {
  coupon: Coupon | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [code, setCode] = useState(coupon?.code ?? "");
  const [kind, setKind] = useState<CouponForm["kind"]>(coupon?.kind ?? "PERCENT");
  const [value, setValue] = useState(coupon ? String(coupon.value) : "");
  const [min, setMin] = useState(coupon?.minOrderRupees ? String(coupon.minOrderRupees) : "");
  const [from, setFrom] = useState(coupon?.validFrom ?? "");
  const [to, setTo] = useState(coupon?.validTo ?? "");
  const [maxUses, setMaxUses] = useState(coupon?.maxUses ? String(coupon.maxUses) : "");
  const [description, setDescription] = useState(coupon?.description ?? "");
  const [active, setActive] = useState(coupon?.active ?? true);
  const action = useAction();

  async function save() {
    const amount = kind === "PERCENT" ? parsePercent(value) : parseRupees(value);
    if (!code.trim()) return action.setError("Enter the code.");
    if (!amount) return action.setError("Enter the discount.");
    const minOrderRupees = min.trim() === "" ? 0 : parseRupees(min);
    if (minOrderRupees === null) return action.setError("Enter the minimum order as a number.");
    const form: CouponForm = {
      code: code.trim(),
      description: description.trim() || null,
      kind,
      value: amount,
      minOrderRupees,
      validFrom: from || null,
      validTo: to || null,
      maxUses: maxUses ? Number(maxUses) : null,
      active,
    };
    const saved = await action.run(() =>
      coupon ? api.admin.engage.updateCoupon(coupon.id, form) : api.admin.engage.createCoupon(form),
    );
    if (saved) onSaved();
  }

  return (
    <Sheet open title={coupon ? `Edit ${coupon.code}` : "New coupon"} onClose={onClose}>
      <div className="flex flex-col gap-4">
        <TextField
          label="Code"
          maxLength={40}
          placeholder="DIWALI10"
          value={code}
          onChange={(e) => setCode(e.target.value)}
        />
        <SegmentedControl
          label="Discount type"
          options={[
            { value: "PERCENT", label: "% off" },
            { value: "AMOUNT", label: "₹ off" },
          ]}
          value={kind}
          onChange={setKind}
        />
        <div className="grid grid-cols-2 gap-3">
          <TextField
            label={kind === "PERCENT" ? "Discount (%)" : "Discount (₹)"}
            inputMode="decimal"
            value={value}
            onChange={(e) => setValue(e.target.value)}
          />
          <TextField
            label="Minimum order (₹)"
            inputMode="decimal"
            value={min}
            onChange={(e) => setMin(e.target.value)}
          />
          <TextField label="From" type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
          <TextField label="Until" type="date" value={to} onChange={(e) => setTo(e.target.value)} />
          <TextField
            label="Uses allowed"
            inputMode="numeric"
            hint="Blank: no limit"
            value={maxUses}
            onChange={(e) => setMaxUses(e.target.value)}
          />
        </div>
        <TextField
          label="Description"
          maxLength={200}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />
        <div className="rounded-2xl border border-line">
          <ToggleRow title="Coupon is on" checked={active} onChange={setActive} />
        </div>
        <ErrorNote>{action.error}</ErrorNote>
        <button type="button" className={buttonPrimary} disabled={action.busy} onClick={() => void save()}>
          {action.busy ? "Saving…" : "Save coupon"}
        </button>
      </div>
    </Sheet>
  );
}
