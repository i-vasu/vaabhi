"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense } from "react";
import type { StaffMember } from "@vaabhi/api-client";
import { useStaff } from "@/components/app-shell";
import { ErrorNote, Fact, Pill } from "@/components/tailoring";
import { LoadState, PageHeader, SectionLabel, buttonSecondary } from "@/components/ui";
import { api } from "@/lib/api";
import { hasPermission } from "@/lib/nav";
import { rupees } from "@/lib/settings";
import { PAY_TYPES, REQUEST_TONES, WEEKDAYS, hhmm, requestLabel, roleLabel } from "@/lib/staff";
import { formatDate, formatPhone, idParam, useAction, useLoad } from "@/lib/tailoring";

function StaffView({ id }: { id: number }) {
  const me = useStaff();
  const member = useLoad(() => api.admin.staff.get(id), [id]);
  const requests = useLoad(() => api.admin.staff.requests({ staffId: id }), [id]);
  const action = useAction();
  const seesPayroll = hasPermission(me, "PAYROLL");

  if (!member.value) {
    return (
      <section className="mx-auto max-w-3xl">
        <PageHeader title="Staff" backHref="/staff/" />
        <LoadState loading={member.loading} error={member.error} />
      </section>
    );
  }
  const m = member.value;
  const apply = async (call: () => Promise<StaffMember>) => {
    const next = await action.run(call);
    if (next) member.setValue(next);
  };

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader
        title={m.name}
        backHref="/staff/"
        badge={m.active ? roleLabel(m.role) : <Pill tone="bad">Left</Pill>}
        actions={
          <Link href={`/staff/edit/?id=${id}`} className={buttonSecondary}>
            Edit
          </Link>
        }
      />
      <ErrorNote>{action.error}</ErrorNote>
      <dl className="grid grid-cols-2 gap-4 rounded-2xl border border-line bg-panel p-4 sm:grid-cols-3">
        {m.designation && <Fact label="Designation">{m.designation}</Fact>}
        {m.phone && <Fact label="Mobile">{formatPhone(m.phone)}</Fact>}
        <Fact label="Pay">
          {PAY_TYPES.find((p) => p.value === m.payType)?.label}
          {m.payType !== "PIECE" && (
            <span className="block text-sm text-ink-soft">
              {rupees(m.salaryRupees)} {m.payType === "DAILY" ? "a day" : "a month"}
            </span>
          )}
        </Fact>
        <Fact label="Shift">
          {hhmm(m.shiftStart)}–{hhmm(m.shiftEnd)}
          <span className="block text-sm text-ink-soft">{m.lateGraceMinutes} min grace</span>
        </Fact>
        {m.weeklyOff && <Fact label="Weekly off">{WEEKDAYS[m.weeklyOff - 1]}</Fact>}
        {m.hourlyRupees > 0 && <Fact label="Overtime">{rupees(m.hourlyRupees)} an hour</Fact>}
        {m.joinedOn && <Fact label="Joined">{formatDate(m.joinedOn)}</Fact>}
        <Fact label="Dashboard">
          {m.canSignIn ? m.email : "No sign-in"}
          {m.lastLoginAt && (
            <span className="block text-sm text-ink-soft">last {formatDate(m.lastLoginAt.slice(0, 10))}</span>
          )}
        </Fact>
      </dl>

      <div className="mt-4 flex flex-wrap gap-2">
        {m.canSignIn && m.id !== me?.id && (
          <button
            type="button"
            className={buttonSecondary}
            disabled={action.busy}
            onClick={() => void apply(() => api.admin.staff.removeSignIn(id))}
          >
            Remove sign-in
          </button>
        )}
        {m.id !== me?.id && (
          <button
            type="button"
            className={`${buttonSecondary} ${m.active ? "text-bad" : ""}`}
            disabled={action.busy}
            onClick={() => void apply(() => api.admin.staff.setActive(id, !m.active))}
          >
            {m.active ? "Mark as left" : "Bring back"}
          </button>
        )}
      </div>

      <SectionLabel>Requests</SectionLabel>
      {requests.value && requests.value.length === 0 && <p className="text-ink-soft">No requests.</p>}
      {requests.value && requests.value.length > 0 && (
        <ul className="divide-y divide-line rounded-2xl border border-line bg-panel">
          {requests.value.slice(0, 10).map((r) => (
            <li key={r.id} className="flex flex-wrap items-center gap-2 px-4 py-3 text-sm">
              <span className="font-medium">{requestLabel(r.kind)}</span>
              <span className="text-ink-soft">
                {formatDate(r.fromDate)}
                {r.toDate !== r.fromDate && ` – ${formatDate(r.toDate)}`}
                {r.amountRupees && ` · ${rupees(r.amountRupees)}`}
              </span>
              <span className="ml-auto">
                <Pill tone={REQUEST_TONES[r.status]}>{r.status.toLowerCase()}</Pill>
              </span>
            </li>
          ))}
        </ul>
      )}

      {seesPayroll && <Statement staffId={id} />}
    </section>
  );
}

function Statement({ staffId }: { staffId: number }) {
  const statement = useLoad(() => api.admin.payroll.statement(staffId), [staffId]);
  const s = statement.value;
  return (
    <>
      <SectionLabel>Pay statement</SectionLabel>
      <ErrorNote>{statement.error}</ErrorNote>
      {s && (
        <>
          <p className="mb-2">
            {s.balanceRupees === 0
              ? "All settled."
              : s.balanceRupees > 0
                ? `The shop owes ${rupees(s.balanceRupees)}.`
                : `${rupees(-s.balanceRupees)} advance still to earn back.`}
          </p>
          {s.entries.length > 0 && (
            <ul className="divide-y divide-line rounded-2xl border border-line bg-panel text-sm">
              {s.entries.map((e, i) => (
                <li key={i} className="flex gap-3 px-4 py-3">
                  <span className="text-ink-soft">{formatDate(e.date)}</span>
                  <span className="flex-1">{e.description}</span>
                  <span className={`tabular-nums ${e.amountRupees < 0 ? "text-warn" : "text-ok"}`}>
                    {e.amountRupees < 0 ? "− " : "+ "}
                    {rupees(Math.abs(e.amountRupees))}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </>
  );
}

function Page() {
  const id = idParam(useSearchParams().get("id"));
  return id ? <StaffView key={id} id={id} /> : <ErrorNote>No staff member selected.</ErrorNote>;
}

export default function StaffMemberPage() {
  return (
    <Suspense fallback={<p className="text-ink-soft">Loading…</p>}>
      <Page />
    </Suspense>
  );
}
