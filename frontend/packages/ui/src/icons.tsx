import type { SVGProps } from "react";

export type IconProps = Omit<SVGProps<SVGSVGElement>, "children"> & { size?: number };

function icon(name: string, paths: React.ReactNode) {
  function Icon({ size = 24, strokeWidth = 1.6, ...props }: IconProps) {
    return (
      <svg
        width={size}
        height={size}
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={strokeWidth}
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
        focusable="false"
        {...props}
      >
        {paths}
      </svg>
    );
  }
  Icon.displayName = `Icon${name}`;
  return Icon;
}

// Same 24px, 1.6-stroke set as the Figma "Icon/*" components.
export const MenuIcon = icon("Menu", <path d="M4 7h16M4 12h16M4 17h16" />);
export const SearchIcon = icon(
  "Search",
  <>
    <circle cx="11" cy="11" r="7" />
    <path d="m20 20-3.5-3.5" />
  </>,
);
export const HeartIcon = icon(
  "Heart",
  <path d="M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10Z" />,
);
export const BagIcon = icon(
  "Bag",
  <>
    <path d="M5.5 8h13l-1 12h-11l-1-12Z" />
    <path d="M9 8a3 3 0 0 1 6 0" />
  </>,
);
export const ChevronRightIcon = icon("ChevronRight", <path d="m9 6 6 6-6 6" />);
export const TruckIcon = icon(
  "Truck",
  <>
    <path d="M3 6h11v10H3z" />
    <path d="M14 10h4l3 3v3h-7" />
    <circle cx="7.5" cy="18" r="1.6" />
    <circle cx="17.5" cy="18" r="1.6" />
  </>,
);
export const ReturnIcon = icon(
  "Return",
  <>
    <path d="M4 12a8 8 0 1 0 2.4-5.7L4 8.6" />
    <path d="M4 4v4.6h4.6" />
  </>,
);
export const HandloomIcon = icon(
  "Handloom",
  <>
    <path d="M12 3c3 3 6 5.5 6 10a6 6 0 0 1-12 0c0-4.5 3-7 6-10Z" />
    <path d="M12 9v9M9 13h6" />
  </>,
);
export const CashIcon = icon(
  "Cash",
  <>
    <rect x="3" y="6" width="18" height="12" rx="2" />
    <circle cx="12" cy="12" r="2.5" />
  </>,
);
export const ChatIcon = icon("Chat", <path d="M4 18.5 5.3 15A7.5 7.5 0 1 1 8.6 18l-4.6.5Z" />);
export const FilterIcon = icon("Filter", <path d="M4 6h16M7 12h10M10 18h4" />);
export const SortIcon = icon("Sort", <path d="M7 4v16M7 20l-3-3M7 20l3-3M17 20V4M17 4l-3 3M17 4l3 3" />);
export const CloseIcon = icon("Close", <path d="M6 6l12 12M18 6 6 18" />);
export const ChevronDownIcon = icon("ChevronDown", <path d="m6 9 6 6 6-6" />);
export const CheckIcon = icon("Check", <path d="m5 12.5 4.5 4.5L19 7.5" />);
