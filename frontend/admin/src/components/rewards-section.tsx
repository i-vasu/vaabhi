"use client";

import { useState } from "react";
import type { RewardChange } from "@vaabhi/api-client";
import { api } from "@/lib/api";
import { rupees } from "@/lib/settings";
import { formatDate, useAction, useLoad } from "@/lib/tailoring";
import { ErrorNote } from "./tailoring";
import { SectionLabel, SegmentedControl, Sheet, TextField, buttonPrimary } from "./ui";

/** A customer's loyalty points: balance, history, and redeeming or correcting them. Hidden when there are none. */
export function RewardsSection({ clientId }: { clientId: number }) {
  const account = useLoad(() => api.admin.engage.rewards(clientId), [clientId]);
  const [changing, setChanging] = useState(false);
  const a = account.value;
  if (!a || (a.balance === 0 && a.entries.length === 0)) return null;

  return (
    <>
      <SectionLabel
        action={
          <button type="button" className="text-sm text-accent-ink underline" onClick={() => setChanging(true)}>
            Redeem / adjust
          </button>
        }
      >
        Reward points
      </SectionLabel>
      <div className="rounded-2xl border border-line bg-panel p-4">
        <p className="text-lg font-semibold">
          {a.balance} points <span className="text-sm font-normal text-ink-soft">worth {rupees(a.valueRupees)}</span>
        </p>
        <ul className="mt-2 text-sm text-ink-soft">
          {a.entries.slice(0, 5).map((e) => (
            <li key={e.id} className="flex gap-2">
              <span>{formatDate(e.createdAt.slice(0, 10))}</span>
              <span className="flex-1">
                {e.kind === "EARNED" ? "Earned" : e.kind === "REDEEMED" ? "Redeemed" : "Adjusted"}
                {e.orderNumber && ` · ${e.orderNumber}`}
                {e.note && !e.orderNumber && ` · ${e.note}`}
              </span>
              <span className={e.points < 0 ? "text-warn" : "text-ok"}>
                {e.points > 0 ? "+" : ""}
                {e.points}
              </span>
            </li>
          ))}
        </ul>
      </div>
      {changing && (
        <ChangeSheet
          balance={a.balance}
          onClose={() => setChanging(false)}
          onSubmit={async (change) => {
            const next = await api.admin.engage.changeRewards(clientId, change);
            account.setValue(next);
            setChanging(false);
          }}
        />
      )}
    </>
  );
}

function ChangeSheet({
  balance,
  onClose,
  onSubmit,
}: {
  balance: number;
  onClose: () => void;
  onSubmit: (change: RewardChange) => Promise<void>;
}) {
  const [kind, setKind] = useState<"REDEEMED" | "ADJUSTED">("REDEEMED");
  const [points, setPoints] = useState("");
  const [note, setNote] = useState("");
  const action = useAction();
  return (
    <Sheet open title="Reward points" onClose={onClose}>
      <div className="flex flex-col gap-4">
        <p className="text-ink-soft">{balance} points now</p>
        <SegmentedControl
          label="Change"
          options={[
            { value: "REDEEMED", label: "Redeem" },
            { value: "ADJUSTED", label: "Correct balance" },
          ]}
          value={kind}
          onChange={setKind}
        />
        <TextField
          label={kind === "REDEEMED" ? "Points to redeem" : "Points (− to take away)"}
          inputMode="numeric"
          value={points}
          onChange={(e) => setPoints(e.target.value)}
        />
        <TextField label="Note" maxLength={200} value={note} onChange={(e) => setNote(e.target.value)} />
        <ErrorNote>{action.error}</ErrorNote>
        <button
          type="button"
          className={buttonPrimary}
          disabled={action.busy}
          onClick={() => {
            const n = Number(points);
            if (!Number.isInteger(n) || n === 0) return action.setError("Enter whole points.");
            void action.run(() => onSubmit({ kind, points: n, orderId: null, note: note.trim() || null }));
          }}
        >
          {action.busy ? "Saving…" : "Save"}
        </button>
      </div>
    </Sheet>
  );
}
