"use client";

import { Button } from "@vaabhi/ui";

export function PrintButton() {
  return (
    <Button variant="secondary" className="print:hidden" onClick={() => window.print()}>
      Print invoice
    </Button>
  );
}
