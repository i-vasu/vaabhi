import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { saveSession } from "@/lib/auth";
import { API, server } from "@/test/server";
import { serveSettings } from "@/test/settings-fixtures";
import { owner, validToken } from "@/test/tokens";
import AccountPage from "./account/page";
import BillingPage, { preview } from "./billing/page";
import DeliveryPackagesPage from "./delivery/page";
import DocumentsPage from "./documents/page";
import GarmentTemplatesPage, { fieldKey } from "./garments/page";
import HolidaysPage from "./holidays/page";
import MessageTemplatesPage, { fillTemplate } from "./messages/page";
import NotificationSettingsPage from "./notifications/page";
import OrderSettingsPage from "./orders/page";
import SettingsPage from "./page";
import PrintFormatsPage from "./print/page";
import PrintingPage from "./printing/page";
import ShopProfilePage from "./shop/page";
import StagesPage from "./stages/page";
import WatermarkPage from "./watermark/page";

const replace = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace }) }));

let saved: Record<string, unknown>;
beforeEach(() => {
  saved = serveSettings().saved;
  replace.mockReset();
});

const click = (name: string | RegExp) => fireEvent.click(screen.getByRole("button", { name }));
const type = (label: string | RegExp | HTMLElement, value: string) =>
  fireEvent.change(label instanceof HTMLElement ? label : screen.getByLabelText(label), { target: { value } });

describe("settings hub", () => {
  it("lists the areas and filters them", () => {
    render(<SettingsPage />);

    expect(screen.getByRole("link", { name: /Billing & tax/ })).toHaveAttribute("href", "/settings/billing/");
    type("Search settings", "gst");
    expect(screen.getByRole("link", { name: /Billing & tax/ })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Shop profile/ })).not.toBeInTheDocument();
    type("Search settings", "zzz");
    expect(screen.getByText("No setting matches “zzz”.")).toBeInTheDocument();
  });

  it("links to printing pages", () => {
    render(<PrintingPage />);
    expect(screen.getByRole("link", { name: /Message templates/ })).toHaveAttribute("href", "/settings/messages/");
  });
});

describe("shop profile", () => {
  it("edits and saves the profile", async () => {
    render(<ShopProfilePage />);

    type(await screen.findByLabelText("Shop name"), "Vaabhi Boutique");
    type("City", "Vijayawada");
    type("Website", "");
    fireEvent.change(screen.getByLabelText("Business type"), { target: { value: "DESIGNER_STUDIO" } });
    type("Closes at", "20:30");
    click("Save");

    await screen.findByText("Saved");
    expect(saved["shop-profile"]).toMatchObject({
      shopName: "Vaabhi Boutique",
      city: "Vijayawada",
      website: null,
      businessType: "DESIGNER_STUDIO",
      closesAt: "20:30",
    });
  });

  it("shows field errors from the API", async () => {
    server.use(
      http.put(`${API}/api/v1/admin/settings/shop-profile`, () =>
        HttpResponse.json(
          {
            detail: "pinCode must be a 6-digit PIN code",
            errors: [{ field: "pinCode", message: "must be a 6-digit PIN code" }],
          },
          { status: 400 },
        ),
      ),
    );
    render(<ShopProfilePage />);

    type(await screen.findByLabelText("PIN code"), "12");
    click("Save");

    expect(await screen.findByText("must be a 6-digit PIN code")).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("pinCode must be a 6-digit PIN code");
  });

  it("explains when the profile cannot load", async () => {
    server.use(http.get(`${API}/api/v1/admin/settings/shop-profile`, () => HttpResponse.error()));
    render(<ShopProfilePage />);
    expect(await screen.findByRole("alert")).toHaveTextContent("Could not reach the server");
  });

  it("reports a logo upload that storage refuses", async () => {
    server.use(
      http.post(`${API}/api/v1/admin/settings/images`, () =>
        HttpResponse.json({ detail: "Photo storage is not configured on this server" }, { status: 503 }),
      ),
    );
    vi.stubGlobal("createImageBitmap", vi.fn().mockRejectedValue(new Error("no canvas in tests")));
    render(<ShopProfilePage />);

    const input = (await screen.findByText("Change logo")).parentElement!.querySelector("input")!;
    fireEvent.change(input, { target: { files: [new File(["x"], "logo.png", { type: "image/png" })] } });

    expect(await screen.findByText(/Could not reach the server|not configured|canvas/)).toBeInTheDocument();
    vi.unstubAllGlobals();
  });
});

describe("orders & production", () => {
  it("shows counts and saves a switch at once", async () => {
    render(<OrderSettingsPage />);

    expect(await screen.findByText("5 stages")).toBeInTheDocument();
    expect(await screen.findByText("No packages yet")).toBeInTheDocument();
    expect(await screen.findByText("1 garment")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("switch", { name: "Trial / fitting date" }));

    await waitFor(() => expect(saved.orders).toMatchObject({ trialDate: true }));
    expect(screen.getByRole("switch", { name: "Trial / fitting date" })).toHaveAttribute("aria-checked", "true");
  });

  it("edits stages: rename, add, move and remove", async () => {
    render(<StagesPage />);

    type(await screen.findByLabelText("Stage 2 name"), "Stitching & lining");
    click("Add work stage");
    click("Move New stage up");
    click("Remove Cutting");
    click("Save");

    await waitFor(() =>
      expect(saved.stages).toEqual([
        { id: 2, name: "Stitching & lining", kind: "WORK" },
        { id: null, name: "New stage", kind: "WORK" },
        { id: 3, name: "Finishing", kind: "WORK" },
        { id: 4, name: "Ready for pickup", kind: "READY" },
        { id: 5, name: "Delivered", kind: "DELIVERED" },
      ]),
    );
    expect(screen.queryByRole("button", { name: /Remove Ready/ })).not.toBeInTheDocument();
  });

  it("adds a delivery package", async () => {
    render(<DeliveryPackagesPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Add package/ }));
    type("Package name", "Express");
    type("Charge ₹", "200");
    type("Days", "3");
    click("Save");

    await waitFor(() =>
      expect(saved["delivery-packages"]).toEqual([{ id: null, name: "Express", priceRupees: 200, days: 3 }]),
    );
    click("Remove Express");
    expect(screen.getByText("No packages yet")).toBeInTheDocument();
  });

  it("adds a garment with measurements", async () => {
    let created: unknown;
    server.use(
      http.post(`${API}/api/v1/admin/settings/garment-templates`, async ({ request }) => {
        created = await request.json();
        return HttpResponse.json(created as never, { status: 201 });
      }),
    );
    render(<GarmentTemplatesPage />);

    expect(await screen.findByText("Bust, Waist")).toBeInTheDocument();
    click("Add garment");
    const sheet = screen.getByRole("dialog", { name: "New garment" });
    fireEvent.change(within(sheet).getByLabelText("Garment name"), { target: { value: "Kurta" } });
    fireEvent.change(within(sheet).getByLabelText("New measurement"), { target: { value: "Sleeve length" } });
    fireEvent.keyDown(within(sheet).getByLabelText("New measurement"), { key: "Enter" });
    fireEvent.change(within(sheet).getByLabelText("New measurement"), { target: { value: "Neck style" } });
    fireEvent.click(within(sheet).getByRole("button", { name: "Add" }));
    fireEvent.change(within(sheet).getByLabelText("Unit for Neck style"), { target: { value: "" } });
    fireEvent.click(within(sheet).getByRole("button", { name: "Save garment" }));

    await waitFor(() =>
      expect(created).toEqual({
        id: null,
        name: "Kurta",
        businessType: "ALL",
        fields: [
          { key: "sleeve_length", label: "Sleeve length", unit: "in" },
          { key: "neck_style", label: "Neck style", unit: "" },
        ],
      }),
    );
  });

  it("edits and removes an existing garment, showing failures", async () => {
    server.use(
      http.delete(`${API}/api/v1/admin/settings/garment-templates/1`, () =>
        HttpResponse.json({ detail: "Garment template not found" }, { status: 404 }),
      ),
    );
    render(<GarmentTemplatesPage />);

    fireEvent.click(await screen.findByText("Blouse"));
    const sheet = screen.getByRole("dialog", { name: "Edit garment" });
    fireEvent.click(within(sheet).getByRole("button", { name: "Remove Bust" }));
    fireEvent.click(within(sheet).getByRole("button", { name: /Remove$/ }));

    expect(await within(sheet).findByRole("alert")).toHaveTextContent("Garment template not found");
  });

  it("turns labels into measurement keys", () => {
    expect(fieldKey("Sleeve length")).toBe("sleeve_length");
    expect(fieldKey("  Front-neck (depth) ")).toBe("front_neck_depth");
    expect(fieldKey("3/4 sleeve")).toBe("m_3_4_sleeve");
  });
});

describe("billing & tax", () => {
  it("saves GST settings and numbering separately", async () => {
    render(<BillingPage />);

    fireEvent.click(await screen.findByRole("switch", { name: "Charge GST" }));
    type("GSTIN", "27abcde1234f1z5");
    type("Default GST rate %", "12");
    fireEvent.change(screen.getByLabelText("Discount comes off"), { target: { value: "ITEMS_BEFORE_TAX" } });
    click("Save GST settings");
    await waitFor(() =>
      expect(saved.billing).toMatchObject({
        chargeGst: true,
        gstin: "27ABCDE1234F1Z5",
        defaultGstRate: 12,
        discountAppliesTo: "ITEMS_BEFORE_TAX",
      }),
    );

    const order = (await screen.findByRole("heading", { name: "Order numbering" })).parentElement!.parentElement!;
    fireEvent.change(within(order).getByLabelText("Next no."), { target: { value: "3000" } });
    expect(within(order).getByText("Next: ORD-3000")).toBeInTheDocument();
    click("Save numbering");
    await waitFor(() =>
      expect((saved.numbering as { kind: string; nextValue: number }[]).find((s) => s.kind === "ORDER")).toMatchObject({
        nextValue: 3000,
      }),
    );
  });

  it("previews padded numbers", () => {
    expect(preview({ kind: "ITEM", prefix: "IT", nextValue: 7, suffix: "/26", pad: 3, preview: "" })).toBe("IT007/26");
  });

  it("saves bank and document details", async () => {
    render(<DocumentsPage />);

    type(await screen.findByLabelText("Bank name"), "State Bank of India");
    type("IFSC code", "sbin0001234");
    type("UPI ID", "vaabhi@oksbi");
    type("Printed at the foot of the invoice", "No returns.");
    click("Save");

    await waitFor(() =>
      expect(saved.documents).toMatchObject({
        bankName: "State Bank of India",
        ifsc: "SBIN0001234",
        upiId: "vaabhi@oksbi",
        terms: "No returns.",
      }),
    );
  });
});

describe("printing & messages", () => {
  it("keeps at least one paper size and the default among them", async () => {
    render(<PrintFormatsPage />);

    fireEvent.click(await screen.findByRole("switch", { name: "A4" }));
    expect(screen.getByRole("switch", { name: "Thermal 80 mm" })).toBeDisabled();
    expect(screen.getByLabelText("Preselected when printing")).toHaveValue("THERMAL_80MM");
    fireEvent.click(screen.getByRole("switch", { name: "Staff name" }));
    click("Save");

    await waitFor(() =>
      expect(saved.printing).toMatchObject({
        formats: ["THERMAL_80MM"],
        defaultFormat: "THERMAL_80MM",
        showStaffName: true,
      }),
    );
  });

  it("previews message templates and switches channel", async () => {
    render(<MessageTemplatesPage />);

    expect(await screen.findByText(/Preview: Hi Priya, order ORD-2042 booked at vaabhi\./)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("radio", { name: "SMS" }));
    type("Order ready message", "Ready, {customer}!");
    click("Save");

    await waitFor(() => expect(saved.messages).toMatchObject({ channel: "SMS", orderReady: "Ready, {customer}!" }));
    expect(fillTemplate("Use {code} {unknown}")).toBe("Use RWD-7F3K {unknown}");
  });

  it("previews and saves the watermark", async () => {
    render(<WatermarkPage />);

    fireEvent.click(await screen.findByRole("switch", { name: "Watermark shared photos" }));
    type("Text", "VAABHI COUTURE");
    fireEvent.change(screen.getByLabelText("Position"), { target: { value: "CENTER" } });
    fireEvent.change(screen.getByLabelText(/Opacity/), { target: { value: "40" } });
    expect(screen.getByRole("img", { name: "Watermark preview" })).toHaveTextContent("VAABHI COUTURE");
    click("Save");

    await waitFor(() =>
      expect(saved.watermark).toEqual({
        enabled: true,
        text: "VAABHI COUTURE",
        position: "CENTER",
        opacityPercent: 40,
      }),
    );
  });
});

describe("holidays, notifications and account", () => {
  it("sets weekly off and holidays", async () => {
    render(<HolidaysPage />);

    expect(await screen.findByText("No holidays added yet.")).toBeInTheDocument();
    click("Sun");
    click(/Add date/);
    const sheet = screen.getByRole("dialog", { name: "Add holiday" });
    fireEvent.change(within(sheet).getByLabelText("Date"), { target: { value: "2026-11-08" } });
    fireEvent.change(within(sheet).getByLabelText("Holiday name"), { target: { value: "Diwali" } });
    fireEvent.click(within(sheet).getByRole("button", { name: "Add holiday" }));
    expect(screen.getByText("Diwali")).toBeInTheDocument();
    click("Save");

    await waitFor(() =>
      expect(saved.calendar).toEqual({
        weeklyOff: ["SUNDAY"],
        holidays: [{ date: "2026-11-08", name: "Diwali" }],
        googleCalendarId: null,
      }),
    );
    click("Remove Diwali");
    expect(screen.getByText("No holidays added yet.")).toBeInTheDocument();
  });

  it("saves a notification switch at once and shows failures", async () => {
    render(<NotificationSettingsPage />);

    fireEvent.click(await screen.findByRole("switch", { name: "Daily summary" }));
    await waitFor(() => expect(saved.notifications).toMatchObject({ dailySummary: true }));

    server.use(http.put(`${API}/api/v1/admin/settings/notifications`, () => HttpResponse.json({}, { status: 500 })));
    fireEvent.click(screen.getByRole("switch", { name: "New order placed" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Could not save. Try again.");
  });

  it("changes the password and signs out", async () => {
    saveSession({ accessToken: validToken(), expiresIn: 3600, staff: owner });
    let body: unknown;
    server.use(
      http.put(`${API}/api/v1/admin/me/password`, async ({ request }) => {
        body = await request.json();
        return new HttpResponse(null, { status: 204 });
      }),
    );
    render(<AccountPage />);

    expect(screen.getByText("asha@vaabhi.local")).toBeInTheDocument();
    type("Current password", "old-password");
    type("New password", "new-password-1");
    type("Repeat new password", "different");
    click("Change password");
    expect(screen.getByRole("alert")).toHaveTextContent("The new passwords do not match.");

    type("Repeat new password", "new-password-1");
    click("Change password");
    expect(await screen.findByRole("status")).toHaveTextContent("Password changed.");
    expect(body).toEqual({ currentPassword: "old-password", newPassword: "new-password-1" });

    server.use(
      http.put(`${API}/api/v1/admin/me/password`, () =>
        HttpResponse.json(
          { detail: "x", errors: [{ field: "newPassword", message: "must be at least 10 characters" }] },
          { status: 400 },
        ),
      ),
    );
    type("Current password", "old-password");
    type("New password", "short-one!");
    type("Repeat new password", "short-one!");
    click("Change password");
    expect(await screen.findByRole("alert")).toHaveTextContent("New password must be at least 10 characters.");

    click("Sign out");
    expect(replace).toHaveBeenCalledWith("/login/");
  });
});
