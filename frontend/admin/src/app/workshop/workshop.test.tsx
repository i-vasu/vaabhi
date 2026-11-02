import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { stages } from "@/test/settings-fixtures";
import { board, lastSent, serveTailoring, workshopStaff, type Sent } from "@/test/tailoring-fixtures";
import WorkBoardPage, { canWork, nextColumn } from "./board/page";
import WorkshopPage from "./page";
import WorkshopStaffPage from "./staff/page";

let search = new URLSearchParams();
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace: vi.fn() }), useSearchParams: () => search }));

let sent: Sent[];
beforeEach(() => {
  sent = serveTailoring().sent;
  search = new URLSearchParams();
});

describe("board rules", () => {
  it("offers staff only the stages they work", () => {
    expect(canWork(workshopStaff[0]!, 2)).toBe(true);
    expect(canWork(workshopStaff[1]!, 1)).toBe(true);
    expect(canWork(workshopStaff[1]!, 2)).toBe(false);
  });

  it("finds the next stage, but none after Ready", () => {
    expect(nextColumn(board, 0)?.stage.name).toBe("Stitching");
    expect(nextColumn(board, 3)).toBeUndefined();
    const withRemoved = [...board, { stage: { id: 9, name: "Old", kind: "WORK" as const }, removed: true, items: [] }];
    expect(nextColumn(withRemoved, 4)?.stage.name).toBe("Cutting");
  });
});

describe("workshop overview", () => {
  it("shows load, deadlines and staff with links to act on them", async () => {
    render(<WorkshopPage />);
    expect(await screen.findByText("2 not given to anyone")).toBeInTheDocument();
    expect(screen.getByText("Overdue").closest("a")).toHaveAttribute("href", "/orders/?filter=OVERDUE");
    expect(screen.getByText("Due today").closest("a")).toHaveAttribute("href", "/orders/?filter=DUE_TODAY");
    expect(screen.getByRole("meter", { name: "Cutting: 4 items" })).toBeInTheDocument();
    expect(screen.getByText("· 2 late")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Sita Cutter/ })).toHaveAttribute("href", "/workshop/board/?staffId=2");
  });
});

describe("work board", () => {
  it("lists the first stage with the overdue item and who may take it", async () => {
    render(<WorkBoardPage />);
    expect(await screen.findByText(/2 × 3 dart blouse/)).toBeInTheDocument();
    expect(screen.getByText(/Overdue/)).toBeInTheDocument();
    const given = await screen.findByLabelText("Given to (3 dart blouse, ORD-2042)");
    const options = within(given)
      .getAllByRole("option")
      .map((o) => o.textContent);
    expect(options).toEqual(["Nobody yet", "Asha Rao (0)", "Sita Cutter (3)"]);

    fireEvent.change(given, { target: { value: "2" } });
    await waitFor(() => expect(lastSent(sent, "PUT", "/workshop/items/101/assignee")?.body).toEqual({ staffId: 2 }));
    await waitFor(() => expect(screen.getByLabelText("Given to (3 dart blouse, ORD-2042)")).toHaveValue("2"));
  });

  it("moves one item to the next stage", async () => {
    render(<WorkBoardPage />);
    fireEvent.click(await screen.findByRole("button", { name: "Stitching →" }));
    await waitFor(() =>
      expect(lastSent(sent, "POST", "/workshop/items/move")?.body).toEqual({ stageId: 2, itemIds: [101] }),
    );
    expect(await screen.findByText("Nothing in Cutting")).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "Stitching · 1" })).toBeInTheDocument();
  });

  it("moves selected items in bulk", async () => {
    render(<WorkBoardPage />);
    fireEvent.click(await screen.findByLabelText("Select 3 dart blouse of ORD-2042"));
    expect(screen.getByText("1 selected")).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Move to"), { target: { value: String(stages[3]!.id) } });
    fireEvent.click(screen.getByRole("button", { name: "Move" }));
    await waitFor(() =>
      expect(lastSent(sent, "POST", "/workshop/items/move")?.body).toEqual({ stageId: 4, itemIds: [101] }),
    );
  });

  it("filters to one person's work and to unassigned work", async () => {
    search = new URLSearchParams("staffId=2");
    render(<WorkBoardPage />);
    expect(await screen.findByRole("heading", { name: "Sita Cutter's work" })).toBeInTheDocument();
    expect(lastSent(sent, "GET", "/workshop/board")?.query).toBe("?staffId=2");
  });

  it("hides work that has been given out when asked", async () => {
    search = new URLSearchParams("unassigned=1");
    render(<WorkBoardPage />);
    expect(await screen.findByText(/2 × 3 dart blouse/)).toBeInTheDocument();
    fireEvent.change(await screen.findByLabelText("Given to (3 dart blouse, ORD-2042)"), { target: { value: "2" } });
    expect(await screen.findByText("Nothing in Cutting")).toBeInTheDocument();
  });
});

describe("who works which stage", () => {
  it("saves the stages ticked for a person", async () => {
    render(<WorkshopStaffPage />);
    const group = await screen.findByRole("group", { name: "Stages for Sita Cutter" });
    expect(within(group).getByRole("button", { name: "Cutting" })).toHaveAttribute("aria-pressed", "true");
    fireEvent.click(within(group).getByRole("button", { name: "Stitching" }));
    fireEvent.click(screen.getByRole("button", { name: "Save" }));
    await waitFor(() => expect(lastSent(sent, "PUT", "/workshop/staff/2/stages")?.body).toEqual({ stageIds: [1, 2] }));
    await waitFor(() => expect(screen.queryByRole("button", { name: "Save" })).not.toBeInTheDocument());
  });
});
