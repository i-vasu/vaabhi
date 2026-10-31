import { cn } from "./cn";

export type BadgeTone = "gold" | "olive" | "festive";

const tones: Record<BadgeTone, string> = {
  // Gold = craft/quality (Handloom, Bestseller); olive = craft stories; festive = New/Sale, sparingly.
  gold: "border border-brand-accent bg-bg-page text-text-secondary",
  olive: "bg-brand-olive-soft text-brand-olive",
  festive: "bg-brand-festive text-white",
};

export function Badge({
  tone = "gold",
  children,
  className,
}: {
  tone?: BadgeTone;
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-sm px-[7px] py-[3px] text-[9px] font-medium uppercase tracking-[0.04em]",
        tones[tone],
        className,
      )}
    >
      {children}
    </span>
  );
}
