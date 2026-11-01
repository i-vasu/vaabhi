import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { sessionStore } from "@/lib/session";
import { server } from "@/test/server";
import { SignInForm } from "./sign-in-form";

const replace = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace }) }));

const NEW_CUSTOMER = { id: 7, name: null, email: null, phone: "+919876543210" };

function sendsCode(resendAfterSeconds = 30) {
  server.use(
    http.post("*/api/auth/otp/request", async ({ request }) => {
      const { phone } = (await request.json()) as { phone: string };
      // As typed the first time; resends use the number the API confirmed.
      expect(["98765 43210", "+919876543210"]).toContain(phone);
      return HttpResponse.json({ phone: "+919876543210", expiresInSeconds: 300, resendAfterSeconds });
    }),
  );
}

async function enterPhone() {
  fireEvent.change(screen.getByLabelText("Mobile number"), { target: { value: "98765 43210" } });
  fireEvent.click(screen.getByRole("button", { name: "Send OTP" }));
  expect(await screen.findByText("+91 98765 43210")).toBeInTheDocument();
}

describe("SignInForm", () => {
  beforeEach(() => replace.mockReset());
  afterEach(() => {
    localStorage.clear();
    delete window.google;
  });

  it("signs in a new customer with phone + OTP, asks for a name and returns to the page", async () => {
    sendsCode();
    server.use(
      http.post("*/api/auth/otp/verify", async ({ request }) => {
        expect(await request.json()).toEqual({ phone: "+919876543210", code: "123456" });
        return HttpResponse.json({ customer: NEW_CUSTOMER, newCustomer: true });
      }),
      http.put("*/api/auth/profile", async ({ request }) => {
        expect(await request.json()).toEqual({ name: "Priya" });
        return HttpResponse.json({ customer: { ...NEW_CUSTOMER, name: "Priya" } });
      }),
    );
    render(<SignInForm googleClientId={null} next="/bag" />);
    expect(screen.queryByTestId("google-button")).not.toBeInTheDocument();

    await enterPhone();
    expect(screen.getByText("Resend code in 30s")).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Enter OTP"), { target: { value: "12a3456" } });
    fireEvent.click(screen.getByRole("button", { name: "Verify and continue" }));

    fireEvent.change(await screen.findByLabelText("What should we call you?"), { target: { value: " Priya " } });
    expect(sessionStore.read()).toEqual({ name: null, phone: "+919876543210", email: null });
    fireEvent.click(screen.getByRole("button", { name: "Save and continue" }));

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/bag"));
    expect(sessionStore.read()?.name).toBe("Priya");
  });

  it("shows the API's message under the input for a wrong code and an invalid number", async () => {
    server.use(
      http.post("*/api/auth/otp/request", () =>
        HttpResponse.json(
          {
            detail: "Enter a valid 10-digit Indian mobile number",
            errors: [{ field: "phone", message: "Enter a valid 10-digit Indian mobile number" }],
          },
          { status: 400 },
        ),
      ),
    );
    render(<SignInForm googleClientId={null} next="/account" />);
    fireEvent.click(screen.getByRole("button", { name: "Send OTP" }));
    expect(screen.getByRole("alert")).toHaveTextContent("Enter a valid 10-digit Indian mobile number");

    fireEvent.change(screen.getByLabelText("Mobile number"), { target: { value: "58765 43210" } });
    fireEvent.click(screen.getByRole("button", { name: "Send OTP" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Enter a valid 10-digit Indian mobile number");

    sendsCode();
    server.use(
      http.post("*/api/auth/otp/verify", () =>
        HttpResponse.json(
          {
            detail: "Incorrect code. 4 tries left.",
            errors: [{ field: "code", message: "Incorrect code. 4 tries left." }],
          },
          { status: 400 },
        ),
      ),
    );
    await enterPhone();
    fireEvent.click(screen.getByRole("button", { name: "Verify and continue" }));
    expect(screen.getByRole("alert")).toHaveTextContent("Enter the 6-digit code");
    fireEvent.change(screen.getByLabelText("Enter OTP"), { target: { value: "000000" } });
    fireEvent.click(screen.getByRole("button", { name: "Verify and continue" }));
    expect(await screen.findByText("Incorrect code. 4 tries left.")).toBeInTheDocument();
    expect(replace).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("button", { name: "Change" }));
    expect(screen.getByLabelText("Mobile number")).toBeInTheDocument();
  });

  it("goes straight back for a returning customer and lets them resend after the wait", async () => {
    sendsCode(1);
    server.use(
      http.post("*/api/auth/otp/verify", () =>
        HttpResponse.json({ customer: { ...NEW_CUSTOMER, name: "Asha" }, newCustomer: false }),
      ),
    );
    render(<SignInForm googleClientId={null} next="/account" />);
    await enterPhone();
    expect(screen.getByText("Resend code in 1s")).toBeInTheDocument();
    fireEvent.click(await screen.findByRole("button", { name: "Resend code" }, { timeout: 3000 }));
    expect(await screen.findByText("Resend code in 1s")).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Enter OTP"), { target: { value: "654321" } });
    fireEvent.click(screen.getByRole("button", { name: "Verify and continue" }));
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/account"));
    expect(sessionStore.read()?.name).toBe("Asha");
  });

  it("signs in with Google's credential", async () => {
    let callback: ((response: { credential: string }) => void) | undefined;
    const renderButton = vi.fn();
    window.google = {
      accounts: {
        id: {
          initialize: (options) => {
            expect(options.client_id).toBe("client-1");
            callback = options.callback;
          },
          renderButton,
        },
      },
    };
    server.use(
      http.post("*/api/auth/google", async ({ request }) => {
        expect(await request.json()).toEqual({ credential: "id-token" });
        return HttpResponse.json({
          customer: { id: 9, name: "Meera", email: "meera@example.com", phone: null },
          newCustomer: true,
        });
      }),
    );
    render(<SignInForm googleClientId="client-1" next="/account" />);
    await waitFor(() => expect(renderButton).toHaveBeenCalled());

    callback!({ credential: "id-token" });
    // Google already gave a name, so no name step.
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/account"));
    expect(sessionStore.read()).toEqual({ name: "Meera", phone: null, email: "meera@example.com" });
  });
});
