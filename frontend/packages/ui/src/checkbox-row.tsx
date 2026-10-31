import type { InputHTMLAttributes } from "react";
import { cn } from "./cn";
import { CheckIcon } from "./icons";

export type CheckboxRowProps = Omit<InputHTMLAttributes<HTMLInputElement>, "type" | "children"> & {
  label: string;
  /** Result count shown at the end of the row. */
  count?: number;
  /** "radio" for single-choice groups such as price. */
  kind?: "checkbox" | "radio";
};

/** The Figma "Checkbox row": a filter option with its result count. A native input keeps it accessible. */
export function CheckboxRow({ label, count, kind = "checkbox", className, disabled, ...input }: CheckboxRowProps) {
  return (
    <label
      className={cn(
        "flex min-h-10 cursor-pointer items-center gap-2.5 py-2.5 text-base text-text-primary",
        disabled && "cursor-not-allowed opacity-50",
        className,
      )}
    >
      <input type={kind} disabled={disabled} className="peer sr-only" {...input} />
      <span
        aria-hidden="true"
        className={cn(
          "flex size-[18px] shrink-0 items-center justify-center border-[1.5px] border-border-strong bg-bg-surface text-transparent",
          kind === "radio" ? "rounded-full" : "rounded-sm",
          "peer-checked:border-brand-primary peer-checked:bg-brand-primary peer-checked:text-text-on-brand",
          "peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-brand-primary",
        )}
      >
        <CheckIcon size={14} strokeWidth={2} />
      </span>
      <span className="grow">{label}</span>
      {count !== undefined && <span className="text-sm text-text-muted">{count}</span>}
    </label>
  );
}
