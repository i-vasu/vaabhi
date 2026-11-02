"use client";

import Link from "next/link";
import { useState } from "react";
import type { PaymentMode, PayrollMonth, Payslip } from "@vaabhi/api-client";
import { AmountLine, MonthPicker } from "@/components/money";
import { ErrorNote, Pill } from "@/components/tailoring";
import { LoadState, PageHeader, SelectField, Sheet, StatTile, buttonPrimary, buttonSecondary } from "@/components/ui";
import { api } from "@/lib/api";
import { monthOf } from "@/lib/money";
import { rupees } from "@/lib/settings";
import { minutes } from "@/lib/staff";
import { PAYMENT_MODES, today, useAction, useLoad } from "@/lib/tailoring";

/**
 * A month's payroll: a preview from attendance, finished work and advances until it is finalised; then each
 * payslip can be paid (which records a salary expense).
 */
export default function PayrollPage() {
  const [month, setMonth] = useState(() => monthOf(today()));
  const key = month.from.slice(0, 7);
  const payroll = useLoad(() => api.admin.payroll.month(key), [key]);
  const action = useAction();
  const [paying, setPaying] = useState<Payslip | null>(null);
  const [open, setOpen] = useState<number | null>(null);
  const p = payroll.value;

  const apply = async (call: () => Promise<PayrollMonth>) => {
    const next = await action.run(call);
    if (next) payroll.setValue(next);
  };

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader
        title="Payroll"
        backHref="/staff/"
        actions={
          <Link href="/payroll/production/" className={buttonSecondary}>
            Production
          </Link>
        }
      />
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <MonthPicker value={month} onChange={setMonth} />
        {p && (p.finalized ? <Pill tone="ok">Finalised</Pill> : <Pill tone="warn">Preview</Pill>)}
      </div>
      <ErrorNote>{payroll.error ?? action.error}</ErrorNote>
      {!p ? (
        <LoadState loading={payroll.loading} error={null} />
      ) : (
        <>
          <div className="mb-4 grid grid-cols-3 gap-3">
            <StatTile label="Net pay" value={rupees(p.totalNetPaise)} />
            <StatTile label="Paid" value={rupees(p.paidPaise)} tone="ok" />
            <StatTile label="To pay" value={rupees(p.unpaidPaise)} tone={p.unpaidPaise > 0 ? "warn" : "default"} />
          </div>
          {!p.finalized ? (
            <p className="mb-4 text-sm text-ink-soft">
              Figures change as attendance, finished work and advances change. Finalise when the month is done to freeze
              the payslips and take back the advances.
            </p>
          ) : null}
          <ul className="flex flex-col gap-3">
            {p.slips.map((slip) => (
              <li key={slip.staffId} className="rounded-2xl border border-line bg-panel p-4">
                <button
                  type="button"
                  aria-expanded={open === slip.staffId}
                  className="flex w-full items-center gap-3 text-left"
                  onClick={() => setOpen(open === slip.staffId ? null : slip.staffId)}
                >
                  <span className="min-w-0 flex-1">
                    <span className="block font-medium">{slip.name}</span>
                    <span className="block text-sm text-ink-soft">
                      {slip.payType === "PIECE"
                        ? `${slip.pieces} pieces`
                        : `${slip.daysPaid} days paid${Number(slip.daysUnpaid) > 0 ? ` · ${slip.daysUnpaid} unpaid` : ""}`}
                    </span>
                  </span>
                  <span className={`font-semibold tabular-nums ${slip.netPaise < 0 ? "text-bad" : ""}`}>
                    {rupees(slip.netPaise)}
                  </span>
                  {slip.paidAt && <Pill tone="ok">Paid</Pill>}
                </button>
                {open === slip.staffId && <SlipDetails slip={slip} />}
                {p.finalized && !slip.paidAt && slip.id && slip.netPaise > 0 && (
                  <button type="button" className={`${buttonSecondary} mt-3`} onClick={() => setPaying(slip)}>
                    Pay {slip.name}
                  </button>
                )}
              </li>
            ))}
          </ul>
          <div className="mt-6 flex gap-2">
            {!p.finalized ? (
              <button
                type="button"
                className={buttonPrimary}
                disabled={action.busy || p.slips.length === 0}
                onClick={() => void apply(() => api.admin.payroll.finalize(key))}
              >
                Finalise month
              </button>
            ) : (
              p.paidPaise === 0 && (
                <button
                  type="button"
                  className={buttonSecondary}
                  disabled={action.busy}
                  onClick={() => void apply(() => api.admin.payroll.reopen(key))}
                >
                  Reopen month
                </button>
              )
            )}
          </div>
        </>
      )}
      {paying && (
        <PaySheet
          slip={paying}
          onClose={() => setPaying(null)}
          onPay={async (mode) => {
            const paid = await action.run(() => api.admin.payroll.pay(paying.id!, mode));
            if (paid) {
              setPaying(null);
              payroll.reload();
            }
          }}
          busy={action.busy}
          error={action.error}
        />
      )}
    </section>
  );
}

function SlipDetails({ slip }: { slip: Payslip }) {
  return (
    <dl aria-label={`Payslip of ${slip.name}`} className="mt-3 border-t border-line pt-3 text-sm">
      {slip.payType !== "PIECE" && (
        <AmountLine
          label={slip.payType === "DAILY" ? `${slip.daysPaid} days × ${rupees(slip.ratePaise)}` : "Salary"}
          value={rupees(slip.basePaise)}
        />
      )}
      {slip.payType === "PIECE" && <AmountLine label={`${slip.pieces} pieces`} value={rupees(slip.piecePaise)} />}
      {slip.overtimePaise > 0 && (
        <AmountLine label={`Overtime ${minutes(slip.overtimeMinutes)}`} value={`+ ${rupees(slip.overtimePaise)}`} />
      )}
      {slip.latePaise > 0 && (
        <AmountLine label={`Late ${minutes(slip.lateMinutes)}`} value={`− ${rupees(slip.latePaise)}`} />
      )}
      {slip.advancePaise > 0 && <AmountLine label="Advance taken back" value={`− ${rupees(slip.advancePaise)}`} />}
      <AmountLine label="Net" value={rupees(slip.netPaise)} strong />
    </dl>
  );
}

function PaySheet({
  slip,
  onClose,
  onPay,
  busy,
  error,
}: {
  slip: Payslip;
  onClose: () => void;
  onPay: (mode: PaymentMode) => Promise<void>;
  busy: boolean;
  error: string | null;
}) {
  const [mode, setMode] = useState<PaymentMode>("CASH");
  return (
    <Sheet open title={`Pay ${slip.name}`} onClose={onClose}>
      <div className="flex flex-col gap-4">
        <p>{rupees(slip.netPaise)} for the month. It is recorded as a salary expense in the ledger.</p>
        <SelectField label="Paid by" options={PAYMENT_MODES} value={mode} onChange={setMode} />
        <ErrorNote>{error}</ErrorNote>
        <button type="button" className={buttonPrimary} disabled={busy} onClick={() => void onPay(mode)}>
          {busy ? "Saving…" : "Mark paid"}
        </button>
      </div>
    </Sheet>
  );
}
