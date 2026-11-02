import type { ReactNode, SVGProps } from "react";

export type IconProps = Omit<SVGProps<SVGSVGElement>, "children"> & { size?: number };
export type Icon = (props: IconProps) => ReactNode;

function icon(name: string, paths: ReactNode): Icon {
  function AdminIcon({ size = 20, strokeWidth = 1.8, ...props }: IconProps) {
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
  AdminIcon.displayName = `Icon${name}`;
  return AdminIcon;
}

// 24px line icons for the dashboard (navigation, actions, empty states).
export const HomeIcon = icon("Home", <path d="M4 11 12 4l8 7M6 9.5V20h4.5v-5h3v5H18V9.5" />);
export const BagIcon = icon(
  "Bag",
  <>
    <path d="M5 8h14l-1 12H6L5 8Z" />
    <path d="M9 8V6a3 3 0 0 1 6 0v2" />
  </>,
);
export const BoardIcon = icon(
  "Board",
  <>
    <rect x="4" y="4" width="16" height="16" rx="2" />
    <path d="M9 4v9M15 4v6M4 4h16" />
  </>,
);
export const UsersIcon = icon(
  "Users",
  <>
    <circle cx="9" cy="8" r="3" />
    <circle cx="17" cy="9" r="2.5" />
    <path d="M3.5 19a5.5 5.5 0 0 1 11 0M14.5 15.2A4.5 4.5 0 0 1 21 19" />
  </>,
);
export const MoreIcon = icon(
  "More",
  <>
    <circle cx="5" cy="12" r="1" fill="currentColor" />
    <circle cx="12" cy="12" r="1" fill="currentColor" />
    <circle cx="19" cy="12" r="1" fill="currentColor" />
  </>,
);
export const TagIcon = icon(
  "Tag",
  <>
    <path d="M3.5 12.5 11 5h8v8l-7.5 7.5a1.4 1.4 0 0 1-2 0l-6-6a1.4 1.4 0 0 1 0-2Z" />
    <circle cx="15" cy="9" r="1.3" />
  </>,
);
export const SearchIcon = icon(
  "Search",
  <>
    <circle cx="11" cy="11" r="7" />
    <path d="m20 20-3.5-3.5" />
  </>,
);
export const BellIcon = icon(
  "Bell",
  <>
    <path d="M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15L6 16Z" />
    <path d="M10 20a2 2 0 0 0 4 0" />
  </>,
);
export const HelpIcon = icon(
  "Help",
  <>
    <circle cx="12" cy="12" r="9" />
    <path d="M9.5 9.5a2.5 2.5 0 0 1 4.8 1c0 1.7-2.3 2-2.3 3.5M12 17h.01" />
  </>,
);
export const ChevronLeftIcon = icon("ChevronLeft", <path d="m15 5-7 7 7 7" />);
export const ChevronRightIcon = icon("ChevronRight", <path d="m9 5 7 7-7 7" />);
export const PlusIcon = icon("Plus", <path d="M12 5v14M5 12h14" />);
export const DownloadIcon = icon(
  "Download",
  <>
    <path d="M12 4v11m0 0-4-4m4 4 4-4" />
    <path d="M5 20h14" />
  </>,
);
export const LogoutIcon = icon(
  "Logout",
  <>
    <path d="M14 4h4a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-4" />
    <path d="M10 16l-4-4 4-4M6 12h10" />
  </>,
);
export const CloseIcon = icon("Close", <path d="M6 6l12 12M18 6 6 18" />);
export const ActivityIcon = icon("Activity", <path d="M3 12h4l3-7 4 14 3-7h4" />);
export const InboxIcon = icon(
  "Inbox",
  <>
    <rect x="4" y="5" width="16" height="14" rx="2" />
    <path d="M4 13h4l1.5 2.5h5L16 13h4" />
  </>,
);
