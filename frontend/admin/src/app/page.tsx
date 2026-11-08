"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import type { Dashboard } from "@vaabhi/api-client";
import { cn } from "@vaabhi/ui";
import { useStaff } from "@/components/app-shell";
import { BagIcon, BankIcon, PlusIcon, ReceiptIcon, TagIcon } from "@/components/icons";
import { ListCard, ListRow, SectionLabel, StatTile } from "@/components/ui";
import { api } from "@/lib/api";
import { getStaff } from "@/lib/auth";
import { canSee, hasPermission, quickActions } from "@/lib/nav";
import { rupees } from "@/lib/settings";

type Status = "checking" | "UP" | "DOWN";

function greeting(hour: number): string {
  if (hour < 12) return "Good morning";
  if (hour < 17) return "Good afternoon";
  return "Good evening";
}

const quickAction =
  "inline-flex items-center gap-2 rounded-xl border border-line bg-panel px-4 py-2.5 text-sm font-medium text-ink hover:border-accent hover:text-accent-ink";

/** Home: this month's revenue, profit and open orders, the API's health, and shortcuts. */
export default function HomePage() {
  const staff = useStaff() ?? getStaff();
  const [status, setStatus] = useState<Status>("checking");
  const [figures, setFigures] = useState<Dashboard | null>(null);
  const [figuresFailed, setFiguresFailed] = useState(false);
  const firstName = staff?.name.split(" ")[0];
  const showFigures = hasPermission(staff, "REPORTS");
  const today = new Date();

  useEffect(() => {
    api
      .request<{ status: string }>("/actuator/health")
      .then((health) => setStatus(health.status === "UP" ? "UP" : "DOWN"))
      .catch(() => setStatus("DOWN"));
  }, []);

  useEffect(() => {
    if (!showFigures) return;
    api.admin.reports
      .dashboard()
      .then(setFigures)
      .catch(() => setFiguresFailed(true));
  }, [showFigures]);

  const month = today.toLocaleDateString("en-IN", { month: "long" });
  const placeholder = figuresFailed ? "—" : "…";

  return (
    <section className="mx-auto max-w-5xl">
      <div className="flex items-start justify-between gap-4">
        <div>
          <p className="text-sm text-ink-soft">
            {today.toLocaleDateString("en-IN", { weekday: "long", day: "numeric", month: "long" })}
          </p>
          <h1 className="text-2xl font-semibold">
            {greeting(today.getHours())}
            {firstName ? `, ${firstName}` : ""}
          </h1>
        </div>
        <span
          role="status"
          title={status === "UP" ? "API is up" : status === "DOWN" ? "API is down" : "Checking API"}
          className="mt-1 inline-flex items-center gap-2 text-xs text-ink-faint"
        >
          <span
            aria-hidden="true"
            className={cn(
              "size-2.5 rounded-full",
              status === "UP" ? "bg-ok" : status === "DOWN" ? "bg-bad" : "animate-pulse bg-ink-faint",
            )}
          />
          <span className="sr-only">
            {status === "UP" ? "API is up" : status === "DOWN" ? "API is down" : "Checking API"}
          </span>
          <span aria-hidden="true">API</span>
        </span>
      </div>

      {showFigures && (
        <div className="mt-6 grid grid-cols-1 gap-3 sm:grid-cols-3">
          <StatTile
            label={`Revenue · ${month}`}
            icon={BankIcon}
            value={figures ? rupees(figures.revenueRupees) : placeholder}
            hint="Counter payments and website orders"
            tone="accent"
          />
          <StatTile
            label="Orders in pipeline"
            icon={BagIcon}
            value={figures ? figures.ordersInPipeline : placeholder}
            hint="Not yet delivered"
          />
          <StatTile
            label={`Profit · ${month}`}
            icon={ReceiptIcon}
            value={figures ? rupees(figures.profitRupees) : placeholder}
            hint={figures ? `After ${rupees(figures.expensesRupees)} expenses` : undefined}
            tone={figures && figures.profitRupees < 0 ? "bad" : "ok"}
          />
        </div>
      )}

      <SectionLabel>Quick actions</SectionLabel>
      <div className="flex flex-wrap gap-2">
        {hasPermission(staff, "CATALOGUE") && (
          <Link
            href="/products/new/"
            className="inline-flex items-center gap-2 rounded-xl bg-accent px-4 py-2.5 text-sm font-medium text-white hover:bg-accent-strong"
          >
            <PlusIcon size={18} /> Add product
          </Link>
        )}
        {quickActions
          .filter((item) => canSee(item, staff))
          .map((item) => (
            <Link key={item.href} href={item.href} className={quickAction}>
              <item.icon size={18} /> {item.label}
            </Link>
          ))}
      </div>

      <SectionLabel>Shop</SectionLabel>
      <ListCard>
        <ListRow icon={TagIcon} title="Products" subtitle="Catalogue, prices and photos" href="/products/" />
      </ListCard>
    </section>
  );
}
