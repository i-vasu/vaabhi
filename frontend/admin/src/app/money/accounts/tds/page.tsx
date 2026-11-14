"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
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
  SegmentedControl,
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
import { PAYMENT_MODES, formatDate, today, useAction, useLoad } from "@/lib/tailoring";

type Tab = "deducted" | "returns";

const TABS: { value: Tab; label: string }[] = [
  { value: "deducted", label: "Deducted and paid" },
  { value: "returns", label: "Returns" },
];

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
function DeductedView({ tab, onTab }: { tab: Tab; onTab: (tab: Tab) => void }) {
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
      <div className="mb-6">
        <SegmentedControl label="TDS view" options={TABS} value={tab} onChange={onTab} />
      </div>
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

/** The API's label for the financial year starting in `startYear`: "2026-27". */
function fyLabelOf(startYear: number): string {
  return `${startYear}-${String((startYear + 1) % 100).padStart(2, "0")}`;
}

/** "2026-27" → "2026-04-01". */
function fyStart(label: string): string {
  return `${label.slice(0, 4)}-04-01`;
}

/** The last day of a quarter (1–4) of the financial year starting in `startYear`. */
function quarterEnd(startYear: number, quarter: number): string {
  return new Date(Date.UTC(startYear, 3 + quarter * 3, 0)).toISOString().slice(0, 10);
}

function returnsFyOptions(): { value: string; label: string }[] {
  const startYear = Number(fyRange(today()).from.slice(0, 4));
  return [0, 1, 2, 3, 4].map((back) => {
    const label = fyLabelOf(startYear - back);
    return { value: label, label: fyLabel(fyStart(label)) };
  });
}

const QUARTERS: { value: string; label: string }[] = [
  { value: "1", label: "Q1 Apr–Jun" },
  { value: "2", label: "Q2 Jul–Sep" },
  { value: "3", label: "Q3 Oct–Dec" },
  { value: "4", label: "Q4 Jan–Mar" },
];

/** The latest quarter that has already ended: FY 2026-27 Q2 on 14 Nov 2026, FY 2025-26 Q4 on 10 May 2026. */
function latestQuarter(now: string): { fy: string; quarter: string } {
  const startYear = Number(fyRange(now).from.slice(0, 4));
  for (let back = 0; back <= 4; back++) {
    for (let quarter = 4; quarter >= 1; quarter--) {
      if (quarterEnd(startYear - back, quarter) <= now) {
        return { fy: fyLabelOf(startYear - back), quarter: String(quarter) };
      }
    }
  }
  return { fy: fyLabelOf(startYear - 1), quarter: "4" };
}

/** The 26Q deductees, challans and per-section totals of one quarter, with a CSV for each part. */
function ReturnsView({ tab, onTab }: { tab: Tab; onTab: (tab: Tab) => void }) {
  const [defaults] = useState(() => latestQuarter(today()));
  const [fy, setFy] = useState(defaults.fy);
  const [quarter, setQuarter] = useState(defaults.quarter);
  const data = useLoad(() => api.admin.accounts.tds.returns(fy, Number(quarter)), [fy, quarter]);
  const download = useAction();
  const q = data.value;

  async function downloadCsv(part: "deductees" | "challans") {
    await download.run(async () => {
      const blob = await api.admin.accounts.tds.returnsCsv(fy, Number(quarter), part);
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `26q-${fy}-q${quarter}-${part}.csv`;
      link.click();
      setTimeout(() => URL.revokeObjectURL(url), 0);
    });
  }

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader title="TDS" backHref="/money/accounts/" />
      <div className="mb-6">
        <SegmentedControl label="TDS view" options={TABS} value={tab} onChange={onTab} />
      </div>
      <div className="mb-4 flex flex-wrap items-end gap-3">
        <SelectField className="w-44" label="Financial year" options={returnsFyOptions()} value={fy} onChange={setFy} />
        <SelectField className="w-40" label="Quarter" options={QUARTERS} value={quarter} onChange={setQuarter} />
        <button
          type="button"
          className={buttonSecondary}
          disabled={download.busy}
          onClick={() => downloadCsv("deductees")}
        >
          Deductees CSV
        </button>
        <button
          type="button"
          className={buttonSecondary}
          disabled={download.busy}
          onClick={() => downloadCsv("challans")}
        >
          Challans CSV
        </button>
      </div>
      <ErrorNote>{data.error ?? download.error}</ErrorNote>
      {!q ? (
        <LoadState loading={data.loading} error={null} />
      ) : q.deductees.length === 0 && q.challans.length === 0 ? (
        <EmptyState icon={ReceiptIcon} title="No TDS deducted this quarter" />
      ) : (
        <>
          {q.deductees.length > 0 && (
            <>
              <SectionLabel>Deductees</SectionLabel>
              <div className="overflow-x-auto rounded-2xl border border-line bg-panel">
                <table className="w-full min-w-[46rem] text-left text-sm">
                  <thead className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
                    <tr>
                      <th className="px-4 py-2 font-medium">Section</th>
                      <th className="px-4 py-2 font-medium">Party</th>
                      <th className="px-4 py-2 font-medium">PAN</th>
                      <th className="px-4 py-2 font-medium">Bill no</th>
                      <th className="px-4 py-2 font-medium">Credited on</th>
                      <th className="px-4 py-2 text-right font-medium">Amount</th>
                      <th className="px-4 py-2 text-right font-medium">Rate %</th>
                      <th className="px-4 py-2 text-right font-medium">TDS</th>
                      <th className="px-4 py-2 font-medium">Challan no</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-line">
                    {q.deductees.map((row) => (
                      <tr key={row.billId}>
                        <td className="whitespace-nowrap px-4 py-3 font-medium">{row.sectionCode}</td>
                        <td className="px-4 py-3">{row.partyName}</td>
                        <td className="whitespace-nowrap px-4 py-3">
                          {row.pan ?? <Pill tone="warn">PAN missing</Pill>}
                        </td>
                        <td className="whitespace-nowrap px-4 py-3">{row.billNo}</td>
                        <td className="whitespace-nowrap px-4 py-3">{formatDate(row.creditedOn)}</td>
                        <td className="whitespace-nowrap px-4 py-3 text-right tabular-nums">
                          {rupees(row.amountRupees)}
                        </td>
                        <td className="whitespace-nowrap px-4 py-3 text-right tabular-nums">{row.ratePercent}%</td>
                        <td className="whitespace-nowrap px-4 py-3 text-right tabular-nums">{rupees(row.tdsRupees)}</td>
                        <td className="whitespace-nowrap px-4 py-3">{row.challanNo ?? "—"}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          )}

          {q.challans.length > 0 && (
            <>
              <SectionLabel>Challans</SectionLabel>
              <div className="overflow-x-auto rounded-2xl border border-line bg-panel">
                <table className="w-full min-w-[40rem] text-left text-sm">
                  <thead className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
                    <tr>
                      <th className="px-4 py-2 font-medium">Section</th>
                      <th className="px-4 py-2 font-medium">Month</th>
                      <th className="px-4 py-2 text-right font-medium">Amount</th>
                      <th className="px-4 py-2 text-right font-medium">Interest</th>
                      <th className="px-4 py-2 font-medium">Challan no</th>
                      <th className="px-4 py-2 font-medium">BSR code</th>
                      <th className="px-4 py-2 font-medium">Paid on</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-line">
                    {q.challans.map((challan) => (
                      <tr key={challan.id}>
                        <td className="whitespace-nowrap px-4 py-3 font-medium">{challan.sectionCode}</td>
                        <td className="whitespace-nowrap px-4 py-3">{monthTitle(challan.periodMonth)}</td>
                        <td className="whitespace-nowrap px-4 py-3 text-right tabular-nums">
                          {rupees(challan.amountRupees)}
                        </td>
                        <td className="whitespace-nowrap px-4 py-3 text-right tabular-nums">
                          {rupees(challan.interestRupees)}
                        </td>
                        <td className="whitespace-nowrap px-4 py-3">{challan.challanNo}</td>
                        <td className="whitespace-nowrap px-4 py-3">{challan.bsrCode}</td>
                        <td className="whitespace-nowrap px-4 py-3">{formatDate(challan.paidOn)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          )}

          {q.totals.length > 0 && (
            <>
              <SectionLabel>Section totals</SectionLabel>
              <div className="overflow-x-auto rounded-2xl border border-line bg-panel">
                <table className="w-full text-left text-sm">
                  <thead className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
                    <tr>
                      <th className="px-4 py-2 font-medium">Section</th>
                      <th className="px-4 py-2 text-right font-medium">Deducted</th>
                      <th className="px-4 py-2 text-right font-medium">Deposited</th>
                      <th className="px-4 py-2 text-right font-medium">Difference</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-line">
                    {q.totals.map((total) => (
                      <tr key={total.sectionCode}>
                        <td className="whitespace-nowrap px-4 py-3 font-medium">{total.sectionCode}</td>
                        <td className="whitespace-nowrap px-4 py-3 text-right tabular-nums">
                          {rupees(total.deductedRupees)}
                        </td>
                        <td className="whitespace-nowrap px-4 py-3 text-right tabular-nums">
                          {rupees(total.depositedRupees)}
                        </td>
                        <td
                          className={`whitespace-nowrap px-4 py-3 text-right tabular-nums ${
                            total.differenceRupees !== 0 ? "text-bad" : ""
                          }`}
                        >
                          {rupees(total.differenceRupees)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          )}
        </>
      )}
    </section>
  );
}

function Route() {
  const router = useRouter();
  const tab: Tab = useSearchParams().get("tab") === "returns" ? "returns" : "deducted";
  const selectTab = (next: Tab) => {
    router.replace(next === "returns" ? "/money/accounts/tds/?tab=returns" : "/money/accounts/tds/");
  };
  return tab === "returns" ? <ReturnsView tab={tab} onTab={selectTab} /> : <DeductedView tab={tab} onTab={selectTab} />;
}

/** TDS deducted and paid, and the quarterly 26Q returns. */
export default function TdsPage() {
  return (
    <Suspense fallback={<p className="text-ink-soft">Loading…</p>}>
      <Route />
    </Suspense>
  );
}
