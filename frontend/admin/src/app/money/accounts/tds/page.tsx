"use client";

import { useState } from "react";
import type { PaymentMode, TdsSectionView } from "@vaabhi/api-client";
import { ReceiptIcon } from "@/components/icons";
import { ErrorNote, Pill } from "@/components/tailoring";
import {
  EmptyState,
  ListCard,
  ListRow,
  LoadState,
  PageHeader,
  SectionLabel,
  SelectField,
  Sheet,
  TextField,
  Toggle,
  buttonPrimary,
  buttonSecondary,
} from "@/components/ui";
import { fyLabel, fyRange, monthTitle, useIsOwner } from "@/lib/accounts";
import { api } from "@/lib/api";
import { parseRupees, rupees } from "@/lib/settings";
import { PAYMENT_MODES, today, useAction, useLoad } from "@/lib/tailoring";

function fyOptions(): { value: string; label: string }[] {
  const startYear = Number(fyRange(today()).from.slice(0, 4));
  return [0, 1, 2, 3].map((back) => {
    const from = `${startYear - back}-04-01`;
    return { value: from, label: fyLabel(from) };
  });
}

function percent(text: string): number | null {
  const cleaned = text.trim();
  if (cleaned === "" || !/^\d+(\.\d{1,2})?$/.test(cleaned)) return null;
  const value = Number(cleaned);
  return value <= 30 ? value : null;
}

/** A challan paid to the government against TDS deducted in a month. */
function ChallanSheet({
  sections,
  onClose,
  onDone,
}: {
  sections: TdsSectionView[];
  onClose: () => void;
  onDone: () => void;
}) {
  const active = sections.filter((section) => section.active);
  const [sectionId, setSectionId] = useState(active[0] ? String(active[0].id) : "");
  const [periodMonth, setPeriodMonth] = useState(() => today().slice(0, 7));
  const [amount, setAmount] = useState("");
  const [interest, setInterest] = useState("0");
  const [challanNo, setChallanNo] = useState("");
  const [bsrCode, setBsrCode] = useState("");
  const [paidOn, setPaidOn] = useState(today());
  const [mode, setMode] = useState<PaymentMode>("BANK");
  const action = useAction();

  const amountValue = parseRupees(amount);
  const interestValue = parseRupees(interest);
  const valid =
    sectionId !== "" &&
    periodMonth !== "" &&
    amountValue !== null &&
    amountValue > 0 &&
    interestValue !== null &&
    interestValue >= 0 &&
    challanNo.trim() !== "" &&
    /^\d{7}$/.test(bsrCode) &&
    paidOn !== "";

  const sectionOptions = [
    { value: "", label: "Choose a section" },
    ...active.map((section) => ({ value: String(section.id), label: `${section.code} · ${section.name}` })),
  ];
  const modes = PAYMENT_MODES.map((option) => ({ value: option.value, label: option.label }));

  async function save() {
    if (!valid || amountValue === null || interestValue === null) return;
    const created = await action.run(() =>
      api.admin.accounts.tds.createChallan({
        tdsSectionId: Number(sectionId),
        periodMonth,
        amountRupees: amountValue,
        interestRupees: interestValue,
        challanNo: challanNo.trim(),
        bsrCode,
        paidOn,
        mode,
      }),
    );
    if (created) onDone();
  }

  return (
    <Sheet open title="Record challan" onClose={onClose}>
      <div className="space-y-3">
        <SelectField label="Section" options={sectionOptions} value={sectionId} onChange={setSectionId} />
        <TextField
          label="Month deducted"
          type="month"
          value={periodMonth}
          onChange={(e) => setPeriodMonth(e.target.value)}
        />
        <TextField
          label="Amount"
          inputMode="decimal"
          placeholder="0.00"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
        />
        <TextField
          label="Interest"
          inputMode="decimal"
          value={interest}
          onChange={(e) => setInterest(e.target.value)}
        />
        <TextField label="Challan no." value={challanNo} onChange={(e) => setChallanNo(e.target.value)} />
        <TextField
          label="BSR code"
          inputMode="numeric"
          hint="Seven digits."
          value={bsrCode}
          onChange={(e) => setBsrCode(e.target.value)}
        />
        <TextField label="Paid on" type="date" value={paidOn} onChange={(e) => setPaidOn(e.target.value)} />
        <SelectField label="Paid from" options={modes} value={mode} onChange={setMode} />
      </div>
      <ErrorNote>{action.error}</ErrorNote>
      <div className="mt-4 flex gap-3">
        <button type="button" className={buttonPrimary} disabled={action.busy || !valid} onClick={save}>
          {action.busy ? "Recording…" : "Record challan"}
        </button>
        <button type="button" className={buttonSecondary} onClick={onClose}>
          Cancel
        </button>
      </div>
    </Sheet>
  );
}

/** A TDS section's rates and thresholds; the CA keeps these right. */
function SectionSheet({
  section,
  onClose,
  onDone,
}: {
  section: TdsSectionView;
  onClose: () => void;
  onDone: () => void;
}) {
  const [code, setCode] = useState(section.code);
  const [name, setName] = useState(section.name);
  const [rate, setRate] = useState(String(section.ratePercent));
  const [noPanRate, setNoPanRate] = useState(String(section.noPanRatePercent));
  const [yearThreshold, setYearThreshold] = useState(section.yearThresholdRupees?.toString() ?? "");
  const [singleThreshold, setSingleThreshold] = useState(section.singleThresholdRupees?.toString() ?? "");
  const [active, setActive] = useState(section.active);
  const action = useAction();

  const rateValue = percent(rate);
  const noPanValue = percent(noPanRate);
  const yearValue = parseRupees(yearThreshold);
  const singleValue = parseRupees(singleThreshold);
  const valid =
    code.trim() !== "" &&
    name.trim() !== "" &&
    rateValue !== null &&
    noPanValue !== null &&
    (yearThreshold.trim() === "" || yearValue !== null) &&
    (singleThreshold.trim() === "" || singleValue !== null);

  async function save() {
    if (!valid || rateValue === null || noPanValue === null) return;
    const updated = await action.run(() =>
      api.admin.accounts.tds.updateSection(section.id, {
        code: code.trim(),
        name: name.trim(),
        ratePercent: rateValue,
        noPanRatePercent: noPanValue,
        yearThresholdRupees: yearValue,
        singleThresholdRupees: singleValue,
        active,
      }),
    );
    if (updated) onDone();
  }

  return (
    <Sheet open title={`Edit ${section.code}`} onClose={onClose}>
      <div className="space-y-3">
        <TextField label="Code" value={code} onChange={(e) => setCode(e.target.value)} />
        <TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} />
        <TextField label="Rate %" inputMode="decimal" value={rate} onChange={(e) => setRate(e.target.value)} />
        <TextField
          label="Rate without PAN %"
          inputMode="decimal"
          value={noPanRate}
          onChange={(e) => setNoPanRate(e.target.value)}
        />
        <TextField
          label="Year threshold"
          inputMode="decimal"
          hint="Leave blank for no threshold."
          value={yearThreshold}
          onChange={(e) => setYearThreshold(e.target.value)}
        />
        <TextField
          label="Single-bill threshold"
          inputMode="decimal"
          value={singleThreshold}
          onChange={(e) => setSingleThreshold(e.target.value)}
        />
        <div className="flex items-center justify-between gap-3">
          <span className="text-sm font-medium text-ink-soft">Active</span>
          <Toggle checked={active} onChange={setActive} label="Active" />
        </div>
      </div>
      <ErrorNote>{action.error}</ErrorNote>
      <div className="mt-4 flex gap-3">
        <button type="button" className={buttonPrimary} disabled={action.busy || !valid} onClick={save}>
          {action.busy ? "Saving…" : "Save section"}
        </button>
        <button type="button" className={buttonSecondary} onClick={onClose}>
          Cancel
        </button>
      </div>
    </Sheet>
  );
}

/** TDS deducted by section and month, against the challans paid, with the sections' settings. */
export default function TdsPage() {
  const owner = useIsOwner();
  const [fyFrom, setFyFrom] = useState(() => fyRange(today()).from);
  const range = fyRange(fyFrom);
  const summary = useLoad(() => api.admin.accounts.tds.summary(range.from, range.to), [range.from, range.to]);
  const sections = useLoad(() => api.admin.accounts.tds.sections(), []);

  const [recording, setRecording] = useState(false);
  const [editing, setEditing] = useState<TdsSectionView | null>(null);

  const rows = summary.value;
  const sectionRows = sections.value;
  const totalDeducted = (rows ?? []).reduce((sum, row) => sum + row.deductedRupees, 0);
  const totalPaid = (rows ?? []).reduce((sum, row) => sum + row.paidRupees, 0);
  const totalBalance = (rows ?? []).reduce((sum, row) => sum + row.balanceRupees, 0);

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader
        title="TDS"
        backHref="/money/accounts/"
        actions={
          owner && (
            <button type="button" className={buttonPrimary} onClick={() => setRecording(true)}>
              Record challan
            </button>
          )
        }
      />
      <div className="mb-4">
        <SelectField label="Financial year" options={fyOptions()} value={fyFrom} onChange={setFyFrom} />
      </div>
      <ErrorNote>{summary.error ?? sections.error}</ErrorNote>

      {!rows ? (
        <LoadState loading={summary.loading} error={null} />
      ) : rows.length === 0 ? (
        <EmptyState icon={ReceiptIcon} title="No TDS this year">
          TDS shows here once supplier bills in this year carry it.
        </EmptyState>
      ) : (
        <div className="overflow-x-auto rounded-2xl border border-line bg-panel">
          <table className="w-full min-w-[34rem] text-left text-sm">
            <thead className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
              <tr>
                <th className="px-4 py-2 font-medium">Month</th>
                <th className="px-4 py-2 font-medium">Section</th>
                <th className="px-4 py-2 text-right font-medium">Deducted</th>
                <th className="px-4 py-2 text-right font-medium">Paid</th>
                <th className="px-4 py-2 text-right font-medium">Balance</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {rows.map((row) => (
                <tr key={`${row.month}-${row.sectionCode}`}>
                  <td className="whitespace-nowrap px-4 py-3">{monthTitle(row.month)}</td>
                  <td className="px-4 py-3">
                    <span className="font-medium">{row.sectionCode}</span>
                    <span className="ml-2 text-ink-soft">{row.sectionName}</span>
                  </td>
                  <td className="whitespace-nowrap px-4 py-3 text-right tabular-nums">{rupees(row.deductedRupees)}</td>
                  <td className="whitespace-nowrap px-4 py-3 text-right tabular-nums">{rupees(row.paidRupees)}</td>
                  <td className="whitespace-nowrap px-4 py-3 text-right tabular-nums">{rupees(row.balanceRupees)}</td>
                </tr>
              ))}
            </tbody>
            <tfoot className="border-t border-line font-medium">
              <tr>
                <td className="px-4 py-3" colSpan={2}>
                  Total
                </td>
                <td className="px-4 py-3 text-right tabular-nums">{rupees(totalDeducted)}</td>
                <td className="px-4 py-3 text-right tabular-nums">{rupees(totalPaid)}</td>
                <td className="px-4 py-3 text-right tabular-nums">{rupees(totalBalance)}</td>
              </tr>
            </tfoot>
          </table>
        </div>
      )}

      <SectionLabel>Sections</SectionLabel>
      {!sectionRows ? (
        <LoadState loading={sections.loading} error={null} />
      ) : sectionRows.length === 0 ? (
        <p className="text-sm text-ink-soft">No TDS sections yet.</p>
      ) : (
        <ListCard>
          {sectionRows.map((section) => (
            <ListRow
              key={section.id}
              title={
                <>
                  {section.code} · {section.name} {!section.active && <Pill tone="default">Inactive</Pill>}
                </>
              }
              subtitle={`${section.ratePercent}% (${section.noPanRatePercent}% without PAN)${
                section.yearThresholdRupees !== null ? ` · ${rupees(section.yearThresholdRupees)} a year` : ""
              }${section.singleThresholdRupees !== null ? ` · ${rupees(section.singleThresholdRupees)} a bill` : ""}`}
              trailing={
                owner ? (
                  <button type="button" className={buttonSecondary} onClick={() => setEditing(section)}>
                    Edit
                  </button>
                ) : undefined
              }
            />
          ))}
        </ListCard>
      )}

      {recording && sectionRows && (
        <ChallanSheet
          sections={sectionRows}
          onClose={() => setRecording(false)}
          onDone={() => {
            setRecording(false);
            summary.reload();
          }}
        />
      )}
      {editing && (
        <SectionSheet
          section={editing}
          onClose={() => setEditing(null)}
          onDone={() => {
            setEditing(null);
            sections.reload();
          }}
        />
      )}
    </section>
  );
}
