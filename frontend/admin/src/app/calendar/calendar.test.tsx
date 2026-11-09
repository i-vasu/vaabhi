import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { CalendarSettings, StaffProfile } from "@vaabhi/api-client";
import { API, server } from "@/test/server";
import { calendar, serveSettings } from "@/test/settings-fixtures";
import { owner } from "@/test/tokens";
import CalendarPage from "./page";

let staff: StaffProfile | undefined = owner;
vi.mock("@/components/app-shell", () => ({ useStaff: () => staff }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace: vi.fn() }) }));

const SHOP_CALENDAR = "shop@gmail.com";
const withCalendar = (googleCalendarId: string | null): CalendarSettings => ({ ...calendar, googleCalendarId });

const serveCalendar = (value: CalendarSettings) =>
  server.use(http.get(`${API}/api/v1/admin/settings/calendar`, () => HttpResponse.json(value)));

/** The calendar id and view the embedded Google Calendar was given. */
const shownParams = () => new URL(screen.getByTitle("Google Calendar").getAttribute("src")!).searchParams;

let saved: Record<string, unknown>;
beforeEach(() => {
  staff = owner;
  saved = serveSettings().saved;
});

describe("calendar view", () => {
  it("shows a loading state, then the shop calendar in the week view", async () => {
    serveCalendar(withCalendar(SHOP_CALENDAR));
    render(<CalendarPage />);

    expect(screen.getByText("Loading…")).toBeInTheDocument();
    expect(await screen.findByTitle("Google Calendar")).toBeInTheDocument();
    expect(shownParams().get("src")).toBe(SHOP_CALENDAR);
    expect(shownParams().get("mode")).toBe("WEEK");
    expect(screen.getByRole("tab", { name: "Week" })).toHaveAttribute("aria-selected", "true");
  });

  it("switches between week, month and list views", async () => {
    serveCalendar(withCalendar(SHOP_CALENDAR));
    render(<CalendarPage />);
    await screen.findByTitle("Google Calendar");

    fireEvent.click(screen.getByRole("tab", { name: "Month" }));
    expect(screen.getByRole("tab", { name: "Month" })).toHaveAttribute("aria-selected", "true");
    expect(shownParams().get("mode")).toBe("MONTH");

    fireEvent.click(screen.getByRole("tab", { name: "List" }));
    expect(shownParams().get("mode")).toBe("AGENDA");
  });

  it("links Add event to Google Calendar in a new tab", async () => {
    serveCalendar(withCalendar(SHOP_CALENDAR));
    render(<CalendarPage />);

    const link = screen.getByRole("link", { name: "Add event" });
    expect(link).toHaveAttribute("href", "https://calendar.google.com/calendar/r/eventedit");
    expect(link).toHaveAttribute("target", "_blank");
    await screen.findByTitle("Google Calendar");
  });

  it("explains when the calendar settings cannot load", async () => {
    server.use(http.get(`${API}/api/v1/admin/settings/calendar`, () => HttpResponse.error()));
    render(<CalendarPage />);
    expect(await screen.findByRole("alert")).toHaveTextContent("Could not reach the server");
  });
});

describe("choosing the calendar", () => {
  it("asks for a calendar ID when none is set, then saves it and shows it", async () => {
    render(<CalendarPage />);

    fireEvent.change(await screen.findByLabelText("Google Calendar ID"), { target: { value: SHOP_CALENDAR } });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByTitle("Google Calendar")).toBeInTheDocument();
    expect(saved.calendar).toEqual({ weeklyOff: [], holidays: [], googleCalendarId: SHOP_CALENDAR });
    expect(shownParams().get("src")).toBe(SHOP_CALENDAR);
  });

  it("keeps the form open while typing and saves a cleared ID as none", async () => {
    render(<CalendarPage />);

    const field = await screen.findByLabelText("Google Calendar ID");
    fireEvent.change(field, { target: { value: "s" } });
    expect(screen.getByLabelText("Google Calendar ID")).toHaveValue("s");
    fireEvent.change(screen.getByLabelText("Google Calendar ID"), { target: { value: "" } });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(saved.calendar).toEqual({ weeklyOff: [], holidays: [], googleCalendarId: null }));
  });

  it("shows why a calendar ID was refused", async () => {
    server.use(
      http.put(`${API}/api/v1/admin/settings/calendar`, () =>
        HttpResponse.json(
          {
            detail: "Calendar could not be saved",
            errors: [{ field: "googleCalendarId", message: "must be an email address or calendar ID" }],
          },
          { status: 400 },
        ),
      ),
    );
    render(<CalendarPage />);

    fireEvent.change(await screen.findByLabelText("Google Calendar ID"), { target: { value: "not a calendar" } });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByText("Calendar could not be saved")).toBeInTheDocument();
    expect(screen.getByText("must be an email address or calendar ID")).toBeInTheDocument();
  });

  it("changes the calendar from the page and saves it", async () => {
    serveCalendar(withCalendar(SHOP_CALENDAR));
    render(<CalendarPage />);

    fireEvent.click(await screen.findByRole("button", { name: "Change calendar" }));
    const field = screen.getByLabelText("Google Calendar ID");
    expect(field).toHaveValue(SHOP_CALENDAR);
    fireEvent.change(field, { target: { value: "vaabhi@gmail.com" } });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(saved.calendar).toMatchObject({ googleCalendarId: "vaabhi@gmail.com" }));
    await waitFor(() => expect(shownParams().get("src")).toBe("vaabhi@gmail.com"));
  });

  it("leaves the calendar alone when changing it is cancelled", async () => {
    serveCalendar(withCalendar(SHOP_CALENDAR));
    render(<CalendarPage />);

    fireEvent.click(await screen.findByRole("button", { name: "Change calendar" }));
    fireEvent.click(screen.getByRole("button", { name: "Cancel" }));

    expect(await screen.findByTitle("Google Calendar")).toBeInTheDocument();
    expect(saved.calendar).toBeUndefined();
  });
});

describe("permissions", () => {
  it("hides the set-up form and shows the empty state to staff without the settings permission", async () => {
    staff = { ...owner, role: "STAFF", permissions: ["ORDERS"] };
    render(<CalendarPage />);

    expect(await screen.findByText("No calendar set up yet")).toBeInTheDocument();
    expect(screen.queryByLabelText("Google Calendar ID")).not.toBeInTheDocument();
  });

  it("shows the calendar but not the change control to staff without the settings permission", async () => {
    staff = { ...owner, role: "STAFF", permissions: ["ORDERS"] };
    serveCalendar(withCalendar(SHOP_CALENDAR));
    render(<CalendarPage />);

    expect(await screen.findByTitle("Google Calendar")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Change calendar" })).not.toBeInTheDocument();
  });
});
