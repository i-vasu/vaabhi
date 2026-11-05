"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ActivityIcon, PlusIcon, TagIcon } from "@/components/icons";
import { ListCard, ListRow, SectionLabel, StatTile } from "@/components/ui";
import { api } from "@/lib/api";
import { getStaff } from "@/lib/auth";

type Status = "checking" | "UP" | "DOWN";

function greeting(hour: number): string {
  if (hour < 12) return "Good morning";
  if (hour < 17) return "Good afternoon";
  return "Good evening";
}

/** Home: today's figures and shortcuts. Order, delivery and money tiles join as those modules land. */
export default function HomePage() {
  const [status, setStatus] = useState<Status>("checking");
  const [categoryCount, setCategoryCount] = useState<number | null>(null);
  const firstName = getStaff()?.name.split(" ")[0];
  const today = new Date();

  useEffect(() => {
    api
      .request<{ status: string }>("/actuator/health")
      .then((health) => setStatus(health.status === "UP" ? "UP" : "DOWN"))
      .catch(() => setStatus("DOWN"));
    api.store
      .listCategories()
      .then((categories) => setCategoryCount(categories.length))
      .catch(() => setCategoryCount(null));
  }, []);

  return (
    <section className="mx-auto max-w-5xl">
      <p className="text-sm text-ink-soft">
        {today.toLocaleDateString("en-IN", { weekday: "long", day: "numeric", month: "long" })}
      </p>
      <h1 className="text-2xl font-semibold">
        {greeting(today.getHours())}
        {firstName ? `, ${firstName}` : ""}
      </h1>

      <div className="mt-6 grid grid-cols-2 gap-3 lg:grid-cols-4">
        <StatTile
          label="API status"
          icon={ActivityIcon}
          value={status}
          tone={status === "DOWN" ? "bad" : status === "UP" ? "ok" : "default"}
        />
        <StatTile label="Categories" icon={TagIcon} value={categoryCount ?? "—"} tone="accent" />
      </div>

      <SectionLabel>Quick actions</SectionLabel>
      <div className="flex flex-wrap gap-2">
        <Link
          href="/products/new/"
          className="inline-flex items-center gap-2 rounded-xl bg-accent px-4 py-2.5 text-sm font-medium text-white hover:bg-accent-strong"
        >
          <PlusIcon size={18} /> Add product
        </Link>
      </div>

      <SectionLabel>Shop</SectionLabel>
      <ListCard>
        <ListRow icon={TagIcon} title="Products" subtitle="Catalogue, prices and photos" href="/products/" />
      </ListCard>
    </section>
  );
}
