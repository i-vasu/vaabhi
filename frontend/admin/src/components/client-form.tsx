"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import type { Client, ClientForm, FieldError, Gender } from "@vaabhi/api-client";
import { api } from "@/lib/api";
import { describeError, orNull } from "@/lib/settings";
import { CLIENT_TYPES } from "@/lib/tailoring";
import { ErrorNote, PhotoInput } from "./tailoring";
import { SaveBar, SectionLabel, SegmentedControl, SelectField, TextAreaField, TextField } from "./ui";

const GENDERS: { value: Gender; label: string }[] = [
  { value: "FEMALE", label: "Female" },
  { value: "MALE", label: "Male" },
  { value: "OTHER", label: "Other" },
];

export const emptyClientForm: ClientForm = {
  phone: "",
  firstName: "",
  lastName: null,
  gender: "FEMALE",
  photoKey: null,
  email: null,
  altPhone: null,
  whatsapp: null,
  address: null,
  city: null,
  state: null,
  pinCode: null,
  clientType: "RETAIL",
  gstin: null,
  preferredStyle: null,
  birthDate: null,
  occasionName: null,
  occasionDate: null,
};

/** The editable part of a saved customer. */
export function formFromClient(client: Client): ClientForm {
  const { phone, firstName, lastName, gender, photoKey, email, altPhone, whatsapp, address, city, state } = client;
  const { pinCode, clientType, gstin, preferredStyle, birthDate, occasionName, occasionDate } = client;
  return {
    phone: phone.replace(/^\+91/, ""),
    firstName,
    lastName,
    gender,
    photoKey,
    email,
    altPhone,
    whatsapp,
    address,
    city,
    state,
    pinCode,
    clientType,
    gstin,
    preferredStyle,
    birthDate,
    occasionName,
    occasionDate,
  };
}

/** Add or edit a customer. Only the mobile number and first name are needed; the rest can follow later. */
export function ClientEditor({ id, initial }: { id?: number; initial: ClientForm }) {
  const router = useRouter();
  const [form, setForm] = useState(initial);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fields, setFields] = useState<FieldError[]>([]);

  const update = (patch: Partial<ClientForm>) => setForm((current) => ({ ...current, ...patch }));
  const fieldError = (field: string) => fields.find((f) => f.field === field)?.message;
  /** Text input bound to an optional field: empty clears it. */
  const text = (field: keyof ClientForm) => ({
    value: (form[field] as string | null) ?? "",
    onChange: (event: { target: { value: string } }) => update({ [field]: event.target.value } as Partial<ClientForm>),
    error: fieldError(field),
  });

  async function save() {
    if (!form.phone.trim() || !form.firstName.trim()) {
      setError("Enter the mobile number and first name.");
      return;
    }
    setSaving(true);
    setError(null);
    setFields([]);
    const cleaned: ClientForm = {
      ...form,
      phone: form.phone.trim(),
      firstName: form.firstName.trim(),
      ...Object.fromEntries(
        (
          [
            "lastName",
            "email",
            "altPhone",
            "whatsapp",
            "address",
            "city",
            "state",
            "pinCode",
            "gstin",
            "preferredStyle",
            "occasionName",
          ] as const
        ).map((key) => [key, orNull(form[key] ?? "")]),
      ),
      birthDate: form.birthDate || null,
      occasionDate: form.occasionDate || null,
    };
    try {
      const saved = id ? await api.admin.clients.update(id, cleaned) : await api.admin.clients.create(cleaned);
      router.replace(`/customers/view/?id=${saved.id}`);
    } catch (e) {
      const described = describeError(e);
      setError(described.message);
      setFields(described.fields);
      setSaving(false);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <SectionLabel>Basic</SectionLabel>
      <PhotoInput
        label="Add photo"
        purpose="CLIENT"
        value={form.photoKey}
        onChange={(photoKey) => update({ photoKey })}
      />
      <TextField
        label="Mobile number"
        type="tel"
        inputMode="tel"
        autoComplete="off"
        required
        {...text("phone")}
        placeholder="10-digit mobile"
      />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="First name" required maxLength={60} {...text("firstName")} />
        <TextField label="Last name" maxLength={60} {...text("lastName")} />
      </div>
      <SegmentedControl
        label="Gender"
        options={GENDERS}
        value={form.gender}
        onChange={(gender) => update({ gender })}
      />

      <SectionLabel>Contact</SectionLabel>
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="WhatsApp number" type="tel" hint="If different from mobile" {...text("whatsapp")} />
        <TextField label="Alternate number" type="tel" {...text("altPhone")} />
      </div>
      <TextField label="Email" type="email" {...text("email")} />
      <TextAreaField label="Address" rows={2} maxLength={300} {...text("address")} />
      <div className="grid gap-4 sm:grid-cols-3">
        <TextField label="City" maxLength={80} {...text("city")} />
        <TextField label="State" maxLength={80} {...text("state")} />
        <TextField label="PIN code" inputMode="numeric" maxLength={6} {...text("pinCode")} />
      </div>

      <SectionLabel>Business</SectionLabel>
      <div className="grid gap-4 sm:grid-cols-2">
        <SelectField
          label="Customer type"
          options={CLIENT_TYPES}
          value={form.clientType}
          onChange={(clientType) => update({ clientType })}
        />
        <TextField label="GSTIN" maxLength={15} hint="For B2B invoices" {...text("gstin")} />
      </div>
      <TextAreaField
        label="Preferred style"
        maxLength={1000}
        placeholder="Likes boat necks, no padding"
        {...text("preferredStyle")}
      />

      <SectionLabel>Dates to remember</SectionLabel>
      <TextField
        label="Birthday"
        type="date"
        value={form.birthDate ?? ""}
        onChange={(event) => update({ birthDate: event.target.value || null })}
        error={fieldError("birthDate")}
      />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="Occasion" maxLength={60} placeholder="Wedding" {...text("occasionName")} />
        <TextField
          label="Occasion date"
          type="date"
          value={form.occasionDate ?? ""}
          onChange={(event) => update({ occasionDate: event.target.value || null })}
          error={fieldError("occasionDate")}
        />
      </div>

      {error && fields.length > 0 && <ErrorNote>Check the highlighted fields.</ErrorNote>}
      <SaveBar
        saving={saving}
        saved={false}
        error={error}
        onSave={() => void save()}
        label={id ? "Save customer" : "Add customer"}
      />
    </div>
  );
}
