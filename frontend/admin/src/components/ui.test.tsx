import { fireEvent, render, screen } from "@testing-library/react";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { BellIcon, TagIcon } from "./icons";
import {
  ChipGroup,
  EmptyState,
  ListCard,
  ListRow,
  PageHeader,
  SectionLabel,
  SegmentedControl,
  Sheet,
  StatTile,
  TextField,
  Toggle,
} from "./ui";

const ranges = [
  { value: "today", label: "Today" },
  { value: "week", label: "This week" },
] as const;

function Chips() {
  const [value, setValue] = useState<"today" | "week">("today");
  return <ChipGroup label="Period" options={[...ranges]} value={value} onChange={setValue} />;
}

function Segments() {
  const [value, setValue] = useState<"today" | "week">("week");
  return <SegmentedControl label="View" options={[...ranges]} value={value} onChange={setValue} />;
}

describe("dashboard building blocks", () => {
  it("renders a page header with back link, badge and actions", () => {
    render(
      <PageHeader title="New order" backHref="/orders/" badge="Order #ORD-2042" actions={<button>Save</button>} />,
    );

    expect(screen.getByRole("heading", { name: "New order" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Back" })).toHaveAttribute("href", "/orders/");
    expect(screen.getByText("Order #ORD-2042")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Save" })).toBeInTheDocument();
  });

  it("renders section labels and stat tiles", () => {
    render(
      <>
        <SectionLabel action={<button>Add date</button>}>Holidays</SectionLabel>
        <StatTile label="Outstanding" value="₹ 5,750" tone="bad" surface hint="3 orders" icon={TagIcon} />
      </>,
    );

    expect(screen.getByRole("heading", { name: "Holidays" })).toBeInTheDocument();
    expect(screen.getByText("₹ 5,750")).toHaveClass("text-bad");
    expect(screen.getByText("3 orders")).toBeInTheDocument();
  });

  it("renders empty states with an action", () => {
    render(
      <EmptyState icon={BellIcon} title="Nothing waiting" tone="warn" action={<button>New request</button>}>
        Requests appear here.
      </EmptyState>,
    );

    expect(screen.getByRole("heading", { name: "Nothing waiting" })).toBeInTheDocument();
    expect(screen.getByText("Requests appear here.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "New request" })).toBeInTheDocument();
  });

  it("switches chips and segments", () => {
    render(
      <>
        <Chips />
        <Segments />
      </>,
    );
    const [chipToday, chipWeek] = screen.getAllByRole("radio");

    expect(chipToday).toHaveAttribute("aria-checked", "true");
    fireEvent.click(chipWeek!);
    expect(chipWeek).toHaveAttribute("aria-checked", "true");
    expect(chipToday).toHaveAttribute("aria-checked", "false");

    const segmentToday = screen.getAllByRole("radio")[2]!;
    fireEvent.click(segmentToday);
    expect(segmentToday).toHaveAttribute("aria-checked", "true");
  });

  it("toggles a switch", () => {
    const onChange = vi.fn();
    render(<Toggle checked={false} onChange={onChange} label="Charge GST" />);

    fireEvent.click(screen.getByRole("switch", { name: "Charge GST" }));
    expect(onChange).toHaveBeenCalledWith(true);
  });

  it("renders list rows as links or plain rows", () => {
    render(
      <ListCard>
        <ListRow icon={TagIcon} title="Shop profile" subtitle="Name, logo, address" href="/settings/shop/" />
        <ListRow title="Currency" trailing="₹ INR" />
      </ListCard>,
    );

    expect(screen.getByRole("link", { name: /Shop profile/ })).toHaveAttribute("href", "/settings/shop/");
    expect(screen.getByText("₹ INR")).toBeInTheDocument();
  });

  it("labels text fields and shows hints or errors", () => {
    const { rerender } = render(<TextField label="GSTIN" hint="Leave blank to bill without GST" />);
    expect(screen.getByLabelText("GSTIN")).toHaveAccessibleDescription("Leave blank to bill without GST");

    rerender(<TextField label="GSTIN" error="Not a valid GSTIN" />);
    expect(screen.getByLabelText("GSTIN")).toHaveAttribute("aria-invalid", "true");
    expect(screen.getByText("Not a valid GSTIN")).toHaveClass("text-bad");
  });

  it("opens and closes a sheet", () => {
    const onClose = vi.fn();
    const { rerender } = render(
      <Sheet open={false} title="New request" onClose={onClose}>
        form
      </Sheet>,
    );
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();

    rerender(
      <Sheet open title="New request" onClose={onClose}>
        form
      </Sheet>,
    );
    expect(screen.getByRole("dialog", { name: "New request" })).toHaveTextContent("form");
    fireEvent.click(screen.getAllByRole("button", { name: "Close" })[0]!);
    fireEvent.keyDown(document, { key: "Escape" });
    fireEvent.keyDown(document, { key: "Enter" });
    expect(onClose).toHaveBeenCalledTimes(2);
  });
});
