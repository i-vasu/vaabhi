import type { InputHTMLAttributes } from "react";
import { cn } from "./cn";

export type ColourSwatchProps = Omit<InputHTMLAttributes<HTMLInputElement>, "type" | "children"> & {
  name: string;
  hex: string;
  count?: number;
  /** "radio" when one colour must be chosen (product page); "checkbox" for filters. */
  kind?: "checkbox" | "radio";
  /** The input's `name` (radio group); `name` itself is the colour's label. */
  group?: string;
};

/** The Figma "Colour swatch": a colour filter or variant picker, backed by a native checkbox. */
export function ColourSwatch({
  name,
  hex,
  count,
  kind = "checkbox",
  group,
  className,
  disabled,
  ...input
}: ColourSwatchProps) {
  return (
    <label
      className={cn(
        "flex w-14 cursor-pointer flex-col items-center gap-1.5",
        disabled && "cursor-not-allowed opacity-50",
        className,
      )}
    >
      <input type={kind} name={group} disabled={disabled} className="peer sr-only" {...input} />
      <span
        aria-hidden="true"
        className={cn(
          "flex size-10 items-center justify-center rounded-full border-2 border-transparent",
          "peer-checked:border-brand-primary",
          "peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-brand-primary",
        )}
      >
        <span className="size-8 rounded-full border border-border-default" style={{ backgroundColor: hex }} />
      </span>
      <span className="text-center text-xs leading-4 text-text-secondary">
        {name}
        {count !== undefined && <span className="sr-only">, {count} products</span>}
      </span>
    </label>
  );
}
