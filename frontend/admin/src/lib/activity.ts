import type { ActivityEntry } from "@vaabhi/api-client";

/** The dashboard page an activity entry is about, if any. */
export function activityHref(entry: Pick<ActivityEntry, "refType" | "refId">): string | null {
  if (!entry.refId && entry.refType !== "SETTINGS") return null;
  switch (entry.refType) {
    case "ORDER":
      return `/orders/view/?id=${entry.refId}`;
    case "ALTERATION":
      return `/alterations/view/?id=${entry.refId}`;
    case "SETTINGS":
      return "/settings/";
    default:
      return null;
  }
}

const when = new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", hour: "numeric", minute: "2-digit" });

export const activityTime = (at: string) => when.format(new Date(at));
