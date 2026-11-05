import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { describe, expect, it, vi } from "vitest";
import { getStaff, getToken } from "@/lib/auth";
import { API, server } from "@/test/server";
import { owner, validToken } from "@/test/tokens";
import LoginPage, { nextPath } from "./page";

const replace = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace }) }));

function fillIn(email: string, password: string) {
  fireEvent.change(screen.getByLabelText("Email"), { target: { value: email } });
  fireEvent.change(screen.getByLabelText("Password"), { target: { value: password } });
  fireEvent.click(screen.getByRole("button", { name: "Sign in" }));
}

describe("LoginPage", () => {
  it("signs in, keeps the session and goes home", async () => {
    const token = validToken();
    let body: unknown;
    server.use(
      http.post(`${API}/api/v1/auth/staff/login`, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({ accessToken: token, expiresIn: 3600, staff: owner });
      }),
    );
    render(<LoginPage />);

    fillIn("  asha@vaabhi.local ", "secret-password");

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/"));
    expect(body).toEqual({ email: "asha@vaabhi.local", password: "secret-password" });
    expect(getToken()).toBe(token);
    expect(getStaff()).toEqual(owner);
  });

  it("shows the server's reason when sign-in is refused", async () => {
    server.use(
      http.post(`${API}/api/v1/auth/staff/login`, () =>
        HttpResponse.json({ detail: "Email or password is wrong" }, { status: 401 }),
      ),
    );
    render(<LoginPage />);

    fillIn("asha@vaabhi.local", "wrong");

    expect(await screen.findByRole("alert")).toHaveTextContent("Email or password is wrong");
    expect(screen.getByRole("button", { name: "Sign in" })).toBeEnabled();
    expect(getToken()).toBeUndefined();
  });

  it("explains when the server cannot be reached", async () => {
    server.use(http.post(`${API}/api/v1/auth/staff/login`, () => HttpResponse.error()));
    render(<LoginPage />);

    fillIn("asha@vaabhi.local", "secret");

    expect(await screen.findByRole("alert")).toHaveTextContent("Could not reach the server");
  });

  it("asks for both fields when the request is rejected as invalid", async () => {
    server.use(http.post(`${API}/api/v1/auth/staff/login`, () => new HttpResponse(null, { status: 400 })));
    render(<LoginPage />);

    fillIn("asha@vaabhi.local", "x");

    expect(await screen.findByRole("alert")).toHaveTextContent("Enter your email and password.");
  });
});

describe("nextPath", () => {
  it("returns to pages inside the app only", () => {
    expect(nextPath("?next=%2Fproducts%2F")).toBe("/products/");
    expect(nextPath("")).toBe("/");
    expect(nextPath("?next=https%3A%2F%2Fevil.example")).toBe("/");
    expect(nextPath("?next=%2F%2Fevil.example")).toBe("/");
    expect(nextPath("?next=%2Flogin%2F")).toBe("/");
  });
});
