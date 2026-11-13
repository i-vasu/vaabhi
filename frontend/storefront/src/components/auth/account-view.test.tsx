import { fireEvent, render, screen } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { afterEach, describe, expect, it } from "vitest";
import type { CustomerDetails, CustomerProfile } from "@vaabhi/api-client";
import { sessionStore } from "@/lib/session";
import { server } from "@/test/server";
import { AccountView } from "./account-view";

const DETAILS: CustomerDetails = {
  dateOfBirth: null,
  addressLine1: null,
  addressLine2: null,
  city: null,
  state: null,
  pincode: null,
  measurements: {},
};

const ASHA: CustomerProfile = { id: 1, name: "Asha", email: "asha@example.com", phone: null };

/** Signs the test in as `customer`, so the account form (not the login prompt) renders. */
function signedIn(customer: CustomerProfile) {
  server.use(
    http.get("*/api/auth/session", () => HttpResponse.json({ customer })),
    http.get("*/api/auth/details", () => HttpResponse.json({ details: DETAILS })),
  );
}

describe("AccountView", () => {
  afterEach(() => {
    localStorage.clear();
  });

  it("adds a mobile number with a WhatsApp code and shows it", async () => {
    signedIn(ASHA);
    server.use(
      http.post("*/api/auth/phone/otp", async ({ request }) => {
        expect(await request.json()).toEqual({ phone: "98765 43210" });
        return HttpResponse.json({ phone: "+919876543210", expiresInSeconds: 300, resendAfterSeconds: 30 });
      }),
      http.put("*/api/auth/phone", async ({ request }) => {
        expect(await request.json()).toEqual({ phone: "+919876543210", code: "123456" });
        return HttpResponse.json({ customer: { ...ASHA, phone: "+919876543210" } });
      }),
    );
    render(<AccountView measurementForms={[]} />);

    fireEvent.click(await screen.findByRole("button", { name: "Add mobile number" }));
    fireEvent.change(screen.getByLabelText("New mobile number"), { target: { value: "98765 43210" } });
    fireEvent.click(screen.getByRole("button", { name: "Send code" }));

    expect(await screen.findByText("+91 98765 43210")).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Verification code"), { target: { value: "123456" } });
    fireEvent.click(screen.getByRole("button", { name: "Verify" }));

    expect(await screen.findByDisplayValue("+91 9876543210")).toBeInTheDocument();
    expect(sessionStore.read()?.phone).toBe("+919876543210");
  });

  it("shows the API's message when the number is already on another account", async () => {
    signedIn(ASHA);
    server.use(
      http.post("*/api/auth/phone/otp", () =>
        HttpResponse.json({ detail: "This number is already on another account", errors: [] }, { status: 409 }),
      ),
    );
    render(<AccountView measurementForms={[]} />);

    fireEvent.click(await screen.findByRole("button", { name: "Add mobile number" }));
    fireEvent.change(screen.getByLabelText("New mobile number"), { target: { value: "98765 43210" } });
    fireEvent.click(screen.getByRole("button", { name: "Send code" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("This number is already on another account");
  });
});
