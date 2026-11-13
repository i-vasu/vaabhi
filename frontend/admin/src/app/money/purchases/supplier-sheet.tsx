"use client";

import { useState } from "react";
import type { SupplierForm, SupplierStatement } from "@vaabhi/api-client";
import { ErrorNote } from "@/components/tailoring";
import { SelectField, Sheet, TextAreaField, TextField, buttonPrimary } from "@/components/ui";
import { api } from "@/lib/api";
import { orNull } from "@/lib/settings";
import { useAction, useLoad } from "@/lib/tailoring";

/** Add or change a supplier: who they are, how to reach them, their state and the TDS section their bills use. */
export function SupplierSheet({
  supplier,
  onClose,
  onSaved,
}: {
  supplier: SupplierStatement | null;
  onClose: () => void;
  onSaved: (id: number) => void;
}) {
  const balance = supplier?.balance;
  const [name, setName] = useState(balance?.name ?? "");
  const [phone, setPhone] = useState(balance?.phone ?? "");
  const [gstin, setGstin] = useState(balance?.gstin ?? "");
  const [pan, setPan] = useState(supplier?.pan ?? "");
  const [address, setAddress] = useState(supplier?.address ?? "");
  const [state, setState] = useState(balance?.state ?? "");
  const [tdsSectionId, setTdsSectionId] = useState(supplier ? String(supplier.tdsSectionId ?? "") : "");
  const states = useLoad(() => api.admin.settings.states(), []);
  const sections = useLoad(() => api.admin.accounts.tds.sections(), []);
  const action = useAction();

  const stateOptions = [
    { value: "", label: "—" },
    ...(states.value ?? []).map((s) => ({ value: s.name, label: `${s.name} (${s.code})` })),
  ];
  if (state && !stateOptions.some((option) => option.value === state)) {
    stateOptions.push({ value: state, label: state });
  }

  const sectionOptions = [
    { value: "", label: "None" },
    ...(sections.value ?? [])
      .filter((section) => section.active)
      .map((section) => ({ value: String(section.id), label: `${section.code} · ${section.name}` })),
  ];
  if (tdsSectionId && !sectionOptions.some((option) => option.value === tdsSectionId)) {
    const saved = sections.value?.find((section) => String(section.id) === tdsSectionId);
    sectionOptions.push({ value: tdsSectionId, label: saved ? `${saved.code} · ${saved.name}` : "Saved section" });
  }

  async function save() {
    if (!name.trim()) return action.setError("Enter the supplier's name.");
    const form: SupplierForm = {
      name: name.trim(),
      phone: orNull(phone),
      email: supplier?.email ?? null,
      gstin: orNull(gstin),
      pan: orNull(pan),
      address: orNull(address),
      city: supplier?.city ?? null,
      state: orNull(state),
      pinCode: supplier?.pinCode ?? null,
      tdsSectionId: tdsSectionId ? Number(tdsSectionId) : null,
    };
    if (supplier) {
      const saved = await action.run(() => api.admin.purchases.updateSupplier(supplier.balance.partyId, form));
      if (saved) onSaved(supplier.balance.partyId);
      return;
    }
    const created = await action.run(() => api.admin.purchases.createSupplier(form));
    if (created) onSaved(created.id);
  }

  return (
    <Sheet open title={supplier ? "Edit supplier" : "Add supplier"} onClose={onClose}>
      <div className="flex flex-col gap-4">
        <TextField label="Name" maxLength={120} value={name} onChange={(e) => setName(e.target.value)} />
        <div className="grid grid-cols-2 gap-3">
          <TextField
            label="Phone"
            type="tel"
            inputMode="tel"
            maxLength={20}
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
          />
          <TextField label="GSTIN" maxLength={15} value={gstin} onChange={(e) => setGstin(e.target.value)} />
          <TextField label="PAN" maxLength={10} value={pan} onChange={(e) => setPan(e.target.value)} />
          <SelectField
            label="State"
            options={stateOptions}
            value={state}
            onChange={setState}
            hint="Decides CGST/SGST or IGST on their bills"
          />
        </div>
        <TextAreaField label="Address" maxLength={300} value={address} onChange={(e) => setAddress(e.target.value)} />
        <SelectField
          label="TDS section"
          options={sectionOptions}
          value={tdsSectionId}
          onChange={setTdsSectionId}
          hint="Suggests TDS when their bills cross the threshold"
        />
        <ErrorNote>{action.error ?? states.error ?? sections.error}</ErrorNote>
        <button type="button" className={buttonPrimary} disabled={action.busy} onClick={() => void save()}>
          {action.busy ? "Saving…" : supplier ? "Save supplier" : "Add supplier"}
        </button>
      </div>
    </Sheet>
  );
}
