"use client";

import { useEffect, useId, useRef } from "react";
import { CloseIcon } from "@vaabhi/ui";

type SheetProps = {
  open: boolean;
  onClose: () => void;
  title: string;
  /** Text action in the header, e.g. "Clear all". */
  action?: { label: string; onClick: () => void };
  footer?: React.ReactNode;
  /** Bottom sheet height on mobile (from the Figma frames). */
  size?: "tall" | "short";
  /** "start": a left-hand drawer at every width (site menu) instead of the bottom sheet / right drawer. */
  side?: "end" | "start";
  children: React.ReactNode;
};

/**
 * Bottom sheet on mobile, right-hand drawer from md up. A native modal <dialog> gives focus trapping,
 * Escape to close and an inert page behind it; tapping the backdrop closes too.
 */
export function Sheet({
  open,
  onClose,
  title,
  action,
  footer,
  size = "tall",
  side = "end",
  children,
}: Readonly<SheetProps>) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  // Light dismiss: a click on the dialog box itself (not its content) is a click on the backdrop.
  // Keyboard users close with Escape, which the native dialog turns into a "close" event.
  useEffect(() => {
    const dialog = ref.current;
    const onBackdrop = (event: MouseEvent) => {
      if (event.target === dialog) onClose();
    };
    dialog?.addEventListener("click", onBackdrop);
    return () => dialog?.removeEventListener("click", onBackdrop);
  }, [onClose]);

  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      onClose={onClose}
      className={[
        "fixed m-0 flex flex-col overflow-hidden bg-bg-surface p-0 text-text-primary",
        "backdrop:bg-[#2B2622]/45 [&:not([open])]:hidden",
        side === "start"
          ? "inset-y-0 left-0 right-auto h-dvh max-h-dvh w-[85vw] max-w-[360px]"
          : [
              "inset-x-0 top-auto bottom-0 max-h-none w-full max-w-none rounded-t-lg",
              size === "tall" ? "h-[88dvh]" : "h-auto max-h-[60dvh]",
              "md:inset-y-0 md:right-0 md:left-auto md:h-dvh md:max-h-dvh md:w-[420px] md:rounded-none",
            ].join(" "),
      ].join(" ")}
    >
      {side === "end" && (
        <div aria-hidden="true" className="flex justify-center pt-2 md:hidden">
          <span className="h-1 w-10 rounded-sm bg-border-strong" />
        </div>
      )}
      <header className="flex items-center justify-between border-b border-border-default px-4 py-3">
        <h2 id={titleId} className="font-serif text-2xl font-semibold">
          {title}
        </h2>
        <div className="flex items-center gap-4">
          {action && (
            <button type="button" onClick={action.onClick} className="text-sm text-brand-primary underline">
              {action.label}
            </button>
          )}
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="rounded-sm p-0.5 hover:text-brand-primary"
          >
            <CloseIcon size={22} />
          </button>
        </div>
      </header>
      <div className="min-h-0 grow overflow-y-auto">{children}</div>
      {footer && (
        <footer className="flex gap-3 border-t border-border-default bg-bg-surface px-4 py-3">{footer}</footer>
      )}
    </dialog>
  );
}
