import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import {
  BagIcon,
  Badge,
  Button,
  buttonClasses,
  CashIcon,
  CategoryTile,
  ChatIcon,
  ChevronRightIcon,
  cn,
  HandloomIcon,
  HeartIcon,
  MenuIcon,
  ProductCard,
  ReturnIcon,
  SearchIcon,
  SectionHeader,
  TruckIcon,
} from "./index";

describe("cn", () => {
  it("joins truthy class names", () => {
    expect(cn("a", false, null, undefined, "b")).toBe("a b");
  });
});

describe("Button", () => {
  it("renders the primary style by default as a non-submitting button", () => {
    render(<Button>Add to bag</Button>);
    const button = screen.getByRole("button", { name: "Add to bag" });
    expect(button).toHaveAttribute("type", "button");
    expect(button.className).toContain("bg-brand-primary");
  });

  it("renders the outlined secondary style", () => {
    render(<Button variant="secondary">Meet the weavers</Button>);
    expect(screen.getByRole("button").className).toContain("border-brand-primary");
  });

  it("exposes the classes for links", () => {
    expect(buttonClasses("secondary", "w-full")).toMatch(/border-brand-primary.*w-full/);
  });
});

describe("Badge", () => {
  it.each([
    ["gold", "border-brand-accent"],
    ["olive", "bg-brand-olive-soft"],
    ["festive", "bg-brand-festive"],
  ] as const)("uses the %s tone", (tone, expected) => {
    render(<Badge tone={tone}>Handloom</Badge>);
    expect(screen.getByText("Handloom").className).toContain(expected);
  });
});

describe("ProductCard", () => {
  const base = {
    name: "Emerald Kanjeevaram silk saree",
    detail: "Pure silk · Zari border",
    price: "₹12,499",
    href: "/p/emerald",
  };

  it("shows name, detail, prices, badge and a labelled wishlist button", () => {
    render(
      <ProductCard
        {...base}
        mrp="₹15,999"
        discount="22% off"
        badge={{ label: "Handloom", tone: "gold" }}
        image={{ placeholder: ["#1E6B52", "#0E3B2E"] }}
      />,
    );
    expect(screen.getByRole("heading", { level: 3 })).toHaveTextContent(base.name);
    expect(screen.getByText("₹12,499")).toBeInTheDocument();
    expect(screen.getByText("₹15,999")).toHaveClass("line-through");
    expect(screen.getByText("22% off")).toBeInTheDocument();
    expect(screen.getByText("Handloom")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: `Add ${base.name} to wishlist` })).toBeInTheDocument();
    expect(screen.getByRole("img", { name: base.name })).toHaveStyle({
      backgroundImage: "linear-gradient(180deg, #1E6B52, #0E3B2E)",
    });
  });

  it("renders a real photo when one is given and omits optional parts", () => {
    render(<ProductCard {...base} image={{ src: "https://img.example/saree.jpg", alt: "Model wearing the saree" }} />);
    expect(screen.getByAltText("Model wearing the saree")).toHaveAttribute("src", "https://img.example/saree.jpg");
    expect(screen.queryByText(/off$/)).not.toBeInTheDocument();
  });
});

describe("CategoryTile", () => {
  it("links to the category", () => {
    render(<CategoryTile label="Sarees" href="/c/sarees" colors={["#8E4450", "#4E1A24"]} />);
    expect(screen.getByRole("link", { name: "Sarees" })).toHaveAttribute("href", "/c/sarees");
  });
});

describe("SectionHeader", () => {
  it("renders the title with an optional action link", () => {
    const { rerender } = render(
      <SectionHeader title="Bestsellers" action={{ label: "View all", href: "/bestsellers" }} />,
    );
    expect(screen.getByRole("heading", { level: 2, name: "Bestsellers" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "View all" })).toHaveAttribute("href", "/bestsellers");
    rerender(<SectionHeader title="Shop by category" />);
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });
});

describe("icons", () => {
  it("render decorative 24px SVGs by default and accept a size", () => {
    const icons = [
      MenuIcon,
      SearchIcon,
      HeartIcon,
      BagIcon,
      ChevronRightIcon,
      TruckIcon,
      ReturnIcon,
      HandloomIcon,
      CashIcon,
      ChatIcon,
    ];
    const { container } = render(
      <>
        {icons.map((Icon, i) => (
          <Icon key={i} size={i === 0 ? 16 : undefined} />
        ))}
      </>,
    );
    const svgs = container.querySelectorAll("svg");
    expect(svgs).toHaveLength(icons.length);
    expect(svgs[0]).toHaveAttribute("width", "16");
    expect(svgs[1]).toHaveAttribute("width", "24");
    svgs.forEach((svg) => expect(svg).toHaveAttribute("aria-hidden", "true"));
  });
});
