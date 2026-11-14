"use client";

import { Button } from "@vaabhi/ui";

export function PrintButton({ label = "Print invoice" }: Readonly<{ label?: string }>) {
  return (
    <Button variant="secondary" className="print:hidden" onClick={() => window.print()}>
      {label}
    </Button>
  );
}
