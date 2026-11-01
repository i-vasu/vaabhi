"use client";

import { useId, useState } from "react";
import { TruckIcon } from "@vaabhi/ui";
import { deliveryWindow, formatDeliveryWindow, isValidPincode } from "@/lib/delivery";

/** Pincode check with an estimated delivery window (courier serviceability comes later). */
export function DeliveryCheck({ extraDays }: Readonly<{ extraDays: { min: number; max: number } }>) {
  const id = useId();
  const [pincode, setPincode] = useState("");
  const [checked, setChecked] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  return (
    <div>
      <form
        className="flex items-center gap-2 rounded-sm border border-border-strong bg-bg-surface py-1 pl-3 pr-1.5"
        onSubmit={(e) => {
          e.preventDefault();
          if (!isValidPincode(pincode)) {
            setError("Enter a 6-digit pincode, e.g. 600001.");
            setChecked(null);
            return;
          }
          setError(null);
          setChecked(pincode.trim());
        }}
      >
        <TruckIcon size={20} className="shrink-0" />
        <label htmlFor={id} className="sr-only">
          Pincode
        </label>
        <input
          id={id}
          inputMode="numeric"
          autoComplete="postal-code"
          maxLength={6}
          placeholder="Enter pincode for delivery date"
          value={pincode}
          onChange={(e) => setPincode(e.target.value.replaceAll(/\D/g, ""))}
          className="min-w-0 flex-1 bg-transparent py-2 tabular-nums outline-none"
        />
        <button type="submit" className="px-2 py-2 text-sm font-medium tracking-[0.04em] text-brand-primary">
          CHECK
        </button>
      </form>
      <p
        aria-live="polite"
        className={`mt-1.5 min-h-5 text-sm ${error ? "text-feedback-error" : "text-text-secondary"}`}
      >
        {error ??
          (checked &&
            `Estimated delivery ${formatDeliveryWindow(deliveryWindow(new Date(), extraDays))} to ${checked} · COD available`)}
      </p>
    </div>
  );
}
