import { fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { getToken } from "@/lib/auth";
import LoginPage from "./page";

describe("LoginPage", () => {
  afterEach(() => sessionStorage.clear());

  it("saves the pasted token for this browser session", () => {
    render(<LoginPage />);

    fireEvent.change(screen.getByLabelText("Access token"), { target: { value: "  header.payload.sig  " } });
    fireEvent.click(screen.getByRole("button", { name: "Save token" }));

    expect(getToken()).toBe("header.payload.sig");
    expect(screen.getByText("Token saved for this browser session.")).toBeInTheDocument();
  });

  it("clears the token when submitted empty", () => {
    sessionStorage.setItem("vaabhi.admin.token", "old");
    render(<LoginPage />);

    fireEvent.click(screen.getByRole("button", { name: "Save token" }));

    expect(getToken()).toBeUndefined();
  });
});
