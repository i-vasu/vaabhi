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
export const StoreIcon = icon(
  "Store",
  <>
    <path d="M4 9.5 5.5 5h13L20 9.5a2.5 2.5 0 0 1-4 2 2.5 2.5 0 0 1-4 0 2.5 2.5 0 0 1-4 0 2.5 2.5 0 0 1-4-2Z" />
    <path d="M5.5 12v7h13v-7M10 19v-4h4v4" />
  </>,
);
export const TruckIcon = icon(
  "Truck",
  <>
    <path d="M3 6h11v10H3zM14 9h4l3 3v4h-7" />
    <circle cx="7" cy="17.5" r="1.8" />
    <circle cx="17" cy="17.5" r="1.8" />
  </>,
);
export const ReceiptIcon = icon(
  "Receipt",
  <>
    <path d="M6 3h12v18l-3-2-3 2-3-2-3 2V3Z" />
    <path d="M9 8h6M9 12h6M9 16h3" />
  </>,
);
export const PrinterIcon = icon(
  "Printer",
  <>
    <path d="M7 9V4h10v5M7 17H5a2 2 0 0 1-2-2v-4a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v4a2 2 0 0 1-2 2h-2" />
    <path d="M7 14h10v6H7z" />
  </>,
);
export const BadgeIcon = icon(
  "Badge",
  <>
    <rect x="4" y="6" width="16" height="14" rx="2" />
    <path d="M9 6V4h6v2M8 16c.5-1.5 2-2 4-2s3.5.5 4 2" />
    <circle cx="12" cy="11" r="2" />
  </>,
);
export const PhoneIcon = icon(
  "Phone",
  <>
    <rect x="7" y="3" width="10" height="18" rx="2" />
    <path d="M11 17h2" />
  </>,
);
export const FlowIcon = icon(
  "Flow",
  <>
    <rect x="3" y="4" width="6" height="5" rx="1" />
    <rect x="15" y="4" width="6" height="5" rx="1" />
    <rect x="15" y="15" width="6" height="5" rx="1" />
    <path d="M9 6.5h6M18 9v6" />
  </>,
);
export const RulerIcon = icon(
  "Ruler",
  <>
    <rect x="3" y="8" width="18" height="8" rx="1.5" />
    <path d="M7 8v3M11 8v4M15 8v3M19 8v4" />
  </>,
);
export const BankIcon = icon(
  "Bank",
  <>
    <path d="M3 9 12 4l9 5H3ZM5 9v8M9.5 9v8M14.5 9v8M19 9v8M3 20h18" />
  </>,
);
export const DocumentIcon = icon(
  "Document",
  <>
    <path d="M6 3h8l4 4v14H6z" />
    <path d="M14 3v4h4M9 12h6M9 16h6" />
  </>,
);
export const LayoutIcon = icon(
  "Layout",
  <>
    <rect x="4" y="4" width="7" height="7" rx="1" />
    <rect x="13" y="4" width="7" height="7" rx="1" />
    <rect x="4" y="13" width="7" height="7" rx="1" />
    <path d="M16.5 13v7M13 16.5h7" />
  </>,
);
export const ChatIcon = icon(
  "Chat",
  <>
    <path d="M4 5h12v9H8l-4 3V5Z" />
    <path d="M16 9h4v9l-3-2h-6v-2" />
  </>,
);
export const ImageIcon = icon(
  "Image",
  <>
    <rect x="3" y="5" width="18" height="14" rx="2" />
    <circle cx="9" cy="10" r="1.6" />
    <path d="m21 16-5-5-8 8" />
  </>,
);
export const CalendarIcon = icon(
  "Calendar",
  <>
    <rect x="4" y="5" width="16" height="15" rx="2" />
    <path d="M4 10h16M9 3v4M15 3v4" />
  </>,
);
export const CalendarPlusIcon = icon(
  "CalendarPlus",
  <>
    <rect x="4" y="5" width="16" height="15" rx="2" />
    <path d="M4 10h16M9 3v4M15 3v4M12 13v5M9.5 15.5h5" />
  </>,
);
export const LockIcon = icon(
  "Lock",
  <>
    <rect x="5" y="11" width="14" height="9" rx="2" />
    <path d="M8 11V8a4 4 0 0 1 8 0v3" />
  </>,
);
export const SettingsIcon = icon(
  "Settings",
  <>
    <circle cx="12" cy="12" r="3" />
    <path d="M12 3v2.5M12 18.5V21M3 12h2.5M18.5 12H21M5.6 5.6l1.8 1.8M16.6 16.6l1.8 1.8M5.6 18.4l1.8-1.8M16.6 7.4l1.8-1.8" />
  </>,
);
export const TrashIcon = icon(
  "Trash",
  <>
    <path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13" />
  </>,
);
export const ArrowUpIcon = icon("ArrowUp", <path d="M12 19V5m0 0-6 6m6-6 6 6" />);
export const ArrowDownIcon = icon("ArrowDown", <path d="M12 5v14m0 0-6-6m6 6 6-6" />);
export const UploadIcon = icon(
  "Upload",
  <>
    <path d="M12 16V5m0 0-4 4m4-4 4 4" />
    <path d="M5 20h14" />
  </>,
);
