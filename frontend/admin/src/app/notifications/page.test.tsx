import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import NotificationsPage from "./page";

describe("NotificationsPage", () => {
  it("explains that nothing has arrived yet", () => {
    render(<NotificationsPage />);

    expect(screen.getByRole("heading", { name: "No notifications yet" })).toBeInTheDocument();
  });
});
