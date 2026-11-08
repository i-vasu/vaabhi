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
export const UserIcon = icon(
  "User",
  <>
    <circle cx="12" cy="8" r="4" />
    <path d="M4.5 20a7.5 7.5 0 0 1 15 0" />
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

/** WhatsApp's mark (filled, unlike the outline set), for "chat with us on WhatsApp" links. */
export function WhatsAppIcon({ size = 24, ...props }: IconProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="currentColor"
      aria-hidden="true"
      focusable="false"
      {...props}
    >
      <path d="M17.47 14.38c-.3-.15-1.76-.87-2.03-.97-.27-.1-.47-.15-.67.15-.2.3-.77.97-.94 1.17-.17.2-.35.22-.65.07-.3-.15-1.26-.46-2.4-1.48-.89-.79-1.49-1.77-1.66-2.07-.17-.3-.02-.46.13-.61.13-.13.3-.35.45-.52.15-.17.2-.3.3-.5.1-.2.05-.37-.02-.52-.08-.15-.67-1.62-.92-2.22-.24-.58-.49-.5-.67-.51l-.57-.01c-.2 0-.52.07-.79.37-.27.3-1.04 1.02-1.04 2.48s1.07 2.88 1.21 3.08c.15.2 2.1 3.2 5.08 4.49.71.31 1.26.49 1.69.63.71.23 1.36.2 1.87.12.57-.09 1.76-.72 2.01-1.41.25-.69.25-1.29.17-1.41-.07-.12-.27-.2-.57-.35M12.05 21.5h-.01a9.9 9.9 0 0 1-5.03-1.38l-.36-.21-3.74.98 1-3.65-.24-.37a9.86 9.86 0 0 1-1.51-5.26c0-5.45 4.44-9.89 9.9-9.89 2.64 0 5.12 1.03 6.99 2.9a9.82 9.82 0 0 1 2.89 7c0 5.45-4.44 9.89-9.89 9.89m8.41-18.3A11.82 11.82 0 0 0 12.05 0C5.5 0 .16 5.34.16 11.89c0 2.1.55 4.14 1.59 5.95L.06 24l6.3-1.65a11.88 11.88 0 0 0 5.68 1.45h.01c6.55 0 11.89-5.34 11.89-11.89 0-3.18-1.24-6.17-3.48-8.41" />
    </svg>
  );
}
