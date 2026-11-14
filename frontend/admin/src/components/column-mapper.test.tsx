import { fireEvent, render, screen } from "@testing-library/react";
import type { ColumnMapping, FilePreview } from "@vaabhi/api-client";
import { describe, expect, it, vi } from "vitest";
import { ColumnMapper, initialMapping } from "./column-mapper";

const preview: FilePreview = {
  rows: [
    ["Date", "Narration", "Withdrawal", "Deposit", "Balance"],
    ["04/11/2026", "NEFT LAKSHMI SILKS", "10500", "", "500"],
  ],
  saved: null,
};

const mapping: ColumnMapping = { headerRow: 0, columns: {}, dateFormat: "dd/MM/yyyy", amountStyle: "DEBIT_CREDIT" };

describe("initialMapping", () => {
  it("uses the saved mapping when there is one", () => {
    const saved: ColumnMapping = { ...mapping, headerRow: 2, columns: { date: 1 } };
    expect(initialMapping("bank", { ...preview, saved })).toBe(saved);
  });

  it("otherwise guesses from the first row with day-first dates", () => {
    for (const kind of ["bank", "cod", "shiprocket"] as const) {
      const guessed = initialMapping(kind, preview);
      expect(guessed.headerRow).toBe(0);
      expect(guessed.dateFormat).toBe("dd/MM/yyyy");
      expect(guessed.amountStyle).toBe("DEBIT_CREDIT");
    }
    expect(initialMapping("bank", { rows: [], saved: null }).columns).toEqual({});
  });
});

describe("ColumnMapper", () => {
  it("edits the header row, date format and a column's field", () => {
    const onChange = vi.fn();
    render(<ColumnMapper kind="bank" preview={preview} value={mapping} onChange={onChange} />);

    fireEvent.change(screen.getByLabelText("Header row"), { target: { value: "2" } });
    expect(onChange).toHaveBeenLastCalledWith({ ...mapping, headerRow: 1 });

    fireEvent.change(screen.getByLabelText("Date format"), { target: { value: "yyyy-MM-dd" } });
    expect(onChange).toHaveBeenLastCalledWith({ ...mapping, dateFormat: "yyyy-MM-dd" });

    fireEvent.change(screen.getByLabelText("Column 2 field"), { target: { value: "description" } });
    expect(onChange).toHaveBeenLastCalledWith({ ...mapping, columns: { description: 1 } });
  });

  it("moves a field to its new column and can clear a column", () => {
    const onChange = vi.fn();
    const value = { ...mapping, columns: { date: 0, description: 1 } };
    render(<ColumnMapper kind="bank" preview={preview} value={value} onChange={onChange} />);

    fireEvent.change(screen.getByLabelText("Column 2 field"), { target: { value: "" } });
    expect(onChange).toHaveBeenLastCalledWith({ ...value, columns: { date: 0 } });
  });

  it("shows the Shiprocket columns", () => {
    render(<ColumnMapper kind="shiprocket" preview={preview} value={mapping} onChange={vi.fn()} />);
    expect(screen.getAllByRole("combobox").length).toBeGreaterThanOrEqual(5);
  });
});
