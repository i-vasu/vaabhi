import { cn } from "./cn";

export type ChipState = "default" | "selected" | "removable";

/**
 * Classes for the Figma "Chip": default = quick filter, selected = active quick filter, removable = applied
 * filter (render a CloseIcon after the label). Usable on links and buttons.
 */
export function chipClasses(state: ChipState = "default", className?: string): string {
  return cn(
    "inline-flex shrink-0 items-center gap-1.5 whitespace-nowrap rounded-full py-[7px] text-sm transition-colors",
    "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-primary",
    state === "removable" ? "pl-3.5 pr-2.5" : "px-3.5",
    state === "selected" && "bg-brand-primary text-text-on-brand",
    state === "removable" && "bg-brand-primary-soft text-brand-primary hover:bg-brand-primary hover:text-text-on-brand",
    state === "default" && "border border-border-strong bg-bg-surface text-text-primary hover:border-brand-primary",
    className,
  );
}
