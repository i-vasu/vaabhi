import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { lastSent, serveTailoring, type Sent } from "@/test/tailoring-fixtures";
import EditCustomerPage from "./edit/page";
import NewCustomerPage from "./new/page";
import CustomersPage from "./page";
import CustomerPage from "./view/page";

const replace = vi.fn();
let search = new URLSearchParams();
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace }), useSearchParams: () => search }));

let sent: Sent[];
beforeEach(() => {
  sent = serveTailoring().sent;
  replace.mockReset();
  search = new URLSearchParams();
});

const type = (label: string | RegExp, value: string) =>
  fireEvent.change(screen.getByLabelText(label), { target: { value } });

describe("customers list", () => {
  it("shows customers with what they owe and searches on Enter", async () => {
    render(<CustomersPage />);

    const row = await screen.findByRole("link", { name: /Meera Iyer/ });
    expect(row).toHaveAttribute("href", "/customers/view/?id=7");
    expect(within(row).getByText("₹ 690 due")).toBeInTheDocument();
    expect(within(row).getByText(/98765 00001/)).toBeInTheDocument();

    type("Search customers", "98765");
    fireEvent.submit(screen.getByRole("search"));
    await waitFor(() => expect(lastSent(sent, "GET", "/clients")?.query).toContain("q=98765"));

    fireEvent.click(screen.getByRole("radio", { name: "Owing" }));
    await waitFor(() => expect(lastSent(sent, "GET", "/clients")?.query).toContain("sort=OWING"));
  });
});

describe("add and edit customer", () => {
  it("needs a mobile number and first name", async () => {
    render(<NewCustomerPage />);
    fireEvent.click(screen.getByRole("button", { name: "Add customer" }));
    expect(await screen.findByText("Enter the mobile number and first name.")).toBeInTheDocument();
  });

  it("saves blank optional fields as empty and opens the customer", async () => {
    render(<NewCustomerPage />);
    type("Mobile number", "98765 00001");
    type("First name", " Meera ");
    type("City", "  ");
    fireEvent.click(screen.getByRole("radio", { name: "Male" }));
    fireEvent.click(screen.getByRole("button", { name: "Add customer" }));

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/customers/view/?id=7"));
    expect(lastSent(sent, "POST", "/clients")?.body).toMatchObject({
      phone: "98765 00001",
      firstName: "Meera",
      gender: "MALE",
      city: null,
      birthDate: null,
    });
  });

  it("loads the customer into the form for editing", async () => {
    search = new URLSearchParams("id=7");
    render(<EditCustomerPage />);
    expect(await screen.findByLabelText("Mobile number")).toHaveValue("9876500001");
    type("City", "Guntur");
    fireEvent.click(screen.getByRole("button", { name: "Save customer" }));
    await waitFor(() => expect(lastSent(sent, "PUT", "/clients/7")?.body).toMatchObject({ city: "Guntur" }));
  });
});

describe("customer page", () => {
  it("shows contact, money, measurements, orders and alterations", async () => {
    search = new URLSearchParams("id=7");
    render(<CustomerPage />);

    expect(await screen.findByRole("heading", { name: "Meera Iyer" })).toBeInTheDocument();
    expect(screen.getByText("Boat necks")).toBeInTheDocument();
    expect(screen.getByText("Loose fit")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /New order/ })).toHaveAttribute("href", "/orders/new/?clientId=7");
    expect(await screen.findByRole("link", { name: /ORD-2042/ })).toHaveAttribute("href", "/orders/view/?id=42");
    expect(await screen.findByRole("link", { name: /ALT-109/ })).toHaveAttribute("href", "/alterations/view/?id=9");
  });

  it("adds measurements starting from the last ones", async () => {
    search = new URLSearchParams("id=7");
    render(<CustomerPage />);
    fireEvent.click(await screen.findByRole("button", { name: "+ Add measurement" }));

    const dialog = await screen.findByRole("dialog", { name: "Add measurement" });
    const bust = await within(dialog).findByLabelText("Bust (in)");
    expect(bust).toHaveValue("34");
    fireEvent.change(within(dialog).getByLabelText("Waist (in)"), { target: { value: "29" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Save measurement" }));

    await waitFor(() =>
      expect(lastSent(sent, "POST", "/clients/7/measurements")?.body).toEqual({
        templateId: 1,
        values: { bust: "34", waist: "29" },
        notes: null,
      }),
    );
  });
});
