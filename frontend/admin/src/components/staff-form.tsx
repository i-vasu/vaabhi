"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import type { FieldError, PayType, StaffForm, StaffMember, StaffRole } from "@vaabhi/api-client";
import { api } from "@/lib/api";
import { describeError, orNull, parseRupees } from "@/lib/settings";
import { PAY_TYPES, ROLES, WEEKDAYS, hhmm } from "@/lib/staff";
import { useStaff } from "./app-shell";
import { SaveBar, SectionLabel, SegmentedControl, SelectField, TextField, ToggleRow } from "./ui";

/** Form fields as typed; money as rupees text. */
export type StaffDraft = {
  name: string;
  phone: string;
  email: string;
  role: StaffRole;
  designation: string;
  joinedOn: string;
  payType: PayType;
  salary: string;
  hourly: string;
  shiftStart: string;
  shiftEnd: string;
  grace: string;
  weeklyOff: string;
  signIn: boolean;
  password: string;
};

export const blankStaff: StaffDraft = {
  name: "",
  phone: "",
  email: "",
  role: "STAFF",
  designation: "",
  joinedOn: "",
  payType: "MONTHLY",
  salary: "",
  hourly: "",
  shiftStart: "10:00",
  shiftEnd: "19:00",
  grace: "10",
  weeklyOff: "",
  signIn: false,
  password: "",
};

export function draftFromMember(member: StaffMember): StaffDraft {
  return {
    name: member.name,
    phone: member.phone ?? "",
    email: member.email ?? "",
    role: member.role,
    designation: member.designation ?? "",
    joinedOn: member.joinedOn ?? "",
    payType: member.payType,
    salary: member.salaryRupees ? String(member.salaryRupees) : "",
    hourly: member.hourlyRupees ? String(member.hourlyRupees) : "",
    shiftStart: hhmm(member.shiftStart),
    shiftEnd: hhmm(member.shiftEnd),
    grace: String(member.lateGraceMinutes),
    weeklyOff: member.weeklyOff ? String(member.weeklyOff) : "",
    signIn: member.canSignIn,
    password: "",
  };
}

/**
 * The API form, or what still needs fixing. A new password is sent only when typed; sign-in needs an email and,
 * for someone who cannot sign in yet, a password.
 */
export function formFromDraft(
  draft: StaffDraft,
  canSignInNow: boolean,
): { form: StaffForm; error: null } | { form: null; error: string } {
  if (!draft.name.trim()) return { form: null, error: "Enter the name." };
  const salaryRupees = draft.salary.trim() === "" ? 0 : parseRupees(draft.salary);
  const hourlyRupees = draft.hourly.trim() === "" ? 0 : parseRupees(draft.hourly);
  if (salaryRupees === null || hourlyRupees === null) return { form: null, error: "Enter pay as numbers." };
  const grace = Number(draft.grace || "0");
  if (!Number.isInteger(grace) || grace < 0 || grace > 180) return { form: null, error: "Grace is 0 to 180 minutes." };
  if (draft.signIn && !draft.email.trim()) return { form: null, error: "Add an email to sign in with." };
  if (draft.signIn && !canSignInNow && draft.password.length < 10) {
    return { form: null, error: "Give them a password of at least 10 characters." };
  }
  return {
    error: null,
    form: {
      name: draft.name.trim(),
      phone: orNull(draft.phone),
      email: orNull(draft.email),
      role: draft.role,
      designation: orNull(draft.designation),
      joinedOn: draft.joinedOn || null,
      payType: draft.payType,
      salaryRupees: draft.payType === "PIECE" ? 0 : salaryRupees,
      hourlyRupees,
      shiftStart: draft.shiftStart,
      shiftEnd: draft.shiftEnd,
      lateGraceMinutes: grace,
      weeklyOff: draft.weeklyOff ? Number(draft.weeklyOff) : null,
      password: draft.signIn && draft.password ? draft.password : null,
    },
  };
}

export function StaffEditor({ member }: { member?: StaffMember }) {
  const router = useRouter();
  const me = useStaff();
  const [draft, setDraft] = useState<StaffDraft>(member ? draftFromMember(member) : blankStaff);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fields, setFields] = useState<FieldError[]>([]);
  const update = (patch: Partial<StaffDraft>) => setDraft((d) => ({ ...d, ...patch }));
  const fieldError = (field: string) => fields.find((f) => f.field === field)?.message;
  const owner = me?.role === "OWNER" || me === undefined;
  const pay = PAY_TYPES.find((p) => p.value === draft.payType)!;

  async function save() {
    const result = formFromDraft(draft, member?.canSignIn ?? false);
    if (result.error !== null) {
      setError(result.error);
      return;
    }
    setSaving(true);
    setError(null);
    setFields([]);
    try {
      const saved = member
        ? await api.admin.staff.update(member.id, result.form)
        : await api.admin.staff.create(result.form);
      router.replace(`/staff/view/?id=${saved.id}`);
    } catch (e) {
      const described = describeError(e);
      setError(described.message);
      setFields(described.fields);
      setSaving(false);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <SectionLabel>Person</SectionLabel>
      <TextField label="Name" maxLength={120} value={draft.name} onChange={(e) => update({ name: e.target.value })} />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField
          label="Mobile"
          type="tel"
          maxLength={20}
          value={draft.phone}
          onChange={(e) => update({ phone: e.target.value })}
        />
        <TextField
          label="Designation"
          maxLength={60}
          placeholder="Tailor, cutter, embroidery"
          value={draft.designation}
          onChange={(e) => update({ designation: e.target.value })}
        />
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <SelectField
          label="Role"
          options={ROLES.filter((r) => owner || r.value === "STAFF")}
          value={draft.role}
          onChange={(role) => update({ role })}
          hint={owner ? undefined : "Only the owner can add managers"}
        />
        <TextField
          label="Joined on"
          type="date"
          value={draft.joinedOn}
          onChange={(e) => update({ joinedOn: e.target.value })}
        />
      </div>

      <SectionLabel>Pay</SectionLabel>
      <SegmentedControl
        label="Paid by"
        options={PAY_TYPES.map((p) => ({ value: p.value, label: p.label }))}
        value={draft.payType}
        onChange={(payType) => update({ payType })}
      />
      {draft.payType === "PIECE" && (
        <p className="text-sm text-ink-soft">
          Paid the price list&apos;s piece rate for each work stage they finish on the work board.
        </p>
      )}
      <div className="grid gap-4 sm:grid-cols-2">
        {draft.payType !== "PIECE" && (
          <TextField
            label={pay.rate}
            inputMode="decimal"
            value={draft.salary}
            error={fieldError("salaryRupees")}
            onChange={(e) => update({ salary: e.target.value })}
          />
        )}
        <TextField
          label="Overtime per hour (₹)"
          inputMode="decimal"
          hint="Also what an hour late costs"
          value={draft.hourly}
          onChange={(e) => update({ hourly: e.target.value })}
        />
      </div>

      <SectionLabel>Shift</SectionLabel>
      <div className="grid gap-4 sm:grid-cols-4">
        <TextField
          label="Starts"
          type="time"
          value={draft.shiftStart}
          onChange={(e) => update({ shiftStart: e.target.value })}
        />
        <TextField
          label="Ends"
          type="time"
          value={draft.shiftEnd}
          onChange={(e) => update({ shiftEnd: e.target.value })}
        />
        <TextField
          label="Grace (min)"
          inputMode="numeric"
          value={draft.grace}
          onChange={(e) => update({ grace: e.target.value })}
        />
        <SelectField
          label="Weekly off"
          options={[{ value: "", label: "None" }, ...WEEKDAYS.map((d, i) => ({ value: String(i + 1), label: d }))]}
          value={draft.weeklyOff}
          onChange={(weeklyOff) => update({ weeklyOff })}
        />
      </div>

      <SectionLabel>Dashboard sign-in</SectionLabel>
      <div className="rounded-2xl border border-line bg-panel">
        <ToggleRow
          title="Can sign in to the dashboard"
          subtitle="Tailors who only work on garments do not need this"
          checked={draft.signIn}
          disabled={member?.canSignIn}
          onChange={(signIn) => update({ signIn })}
        />
      </div>
      {draft.signIn && (
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField
            label="Email"
            type="email"
            maxLength={254}
            value={draft.email}
            error={fieldError("email")}
            onChange={(e) => update({ email: e.target.value })}
          />
          <TextField
            label={member?.canSignIn ? "New password" : "Password"}
            type="password"
            autoComplete="new-password"
            hint={member?.canSignIn ? "Leave blank to keep it" : "At least 10 characters"}
            value={draft.password}
            error={fieldError("password")}
            onChange={(e) => update({ password: e.target.value })}
          />
        </div>
      )}
      <SaveBar
        saving={saving}
        saved={false}
        error={error}
        onSave={() => void save()}
        label={member ? "Save" : "Add staff"}
      />
    </div>
  );
}
