"use client";

import type { RewardSettings } from "@vaabhi/api-client";
import { ErrorNote } from "@/components/tailoring";
import { LoadState, PageHeader, TextField, ToggleRow, buttonPrimary } from "@/components/ui";
import { api } from "@/lib/api";
import { rupees, useSaveable } from "@/lib/settings";

/** How loyalty points work. Customers' balances are on their pages. */
export default function RewardsPage() {
  const s = useSaveable<RewardSettings>(
    () => api.admin.engage.rewardSettings(),
    (value) => api.admin.engage.saveRewardSettings(value),
  );
  const v = s.value;
  return (
    <section className="mx-auto max-w-2xl">
      <PageHeader title="Rewards" backHref="/more/" />
      {!v ? (
        <LoadState loading={s.loading} error={s.loadError} />
      ) : (
        <div className="flex flex-col gap-4">
          <div className="rounded-2xl border border-line bg-panel">
            <ToggleRow
              title="Customers earn points"
              subtitle="Points are added when an order is delivered"
              checked={v.enabled}
              onChange={(enabled) => s.update({ enabled })}
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <TextField
              label="Points per ₹100"
              inputMode="numeric"
              value={String(v.pointsPer100)}
              onChange={(e) => s.update({ pointsPer100: Number(e.target.value) || 0 })}
            />
            <TextField
              label="One point is worth (₹)"
              inputMode="numeric"
              value={String(v.rupeesPerPoint)}
              onChange={(e) => s.update({ rupeesPerPoint: Number(e.target.value) || 0 })}
            />
          </div>
          <p className="text-sm text-ink-soft">
            A ₹1,000 order earns {10 * v.pointsPer100} points, worth {rupees(10 * v.pointsPer100 * v.rupeesPerPoint)}{" "}
            when redeemed.
          </p>
          <ErrorNote>{s.error}</ErrorNote>
          <button type="button" className={buttonPrimary} disabled={s.saving} onClick={() => void s.save()}>
            {s.saving ? "Saving…" : s.saved ? "Saved" : "Save"}
          </button>
        </div>
      )}
    </section>
  );
}
