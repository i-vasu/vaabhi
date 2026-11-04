"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";

/** Online orders now live in Orders (tagged Online); old links land there. */
export default function OnlineOrdersPage() {
  const router = useRouter();
  useEffect(() => router.replace("/orders/?source=ONLINE"), [router]);
  return <p className="text-ink-soft">Opening orders…</p>;
}
