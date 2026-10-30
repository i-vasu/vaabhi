"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api";

type Status = "checking" | "UP" | "DOWN";

export default function DashboardPage() {
  const [status, setStatus] = useState<Status>("checking");
  const [categoryCount, setCategoryCount] = useState<number | null>(null);

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
    <section>
      <h1 className="text-2xl font-semibold">Dashboard</h1>
      <div className="mt-6 grid max-w-xl grid-cols-2 gap-4">
        <div className="rounded-lg border border-neutral-200 p-4">
          <p className="text-sm text-neutral-500">API status</p>
          <p className={`mt-1 text-xl font-semibold ${status === "DOWN" ? "text-red-600" : ""}`}>{status}</p>
        </div>
        <div className="rounded-lg border border-neutral-200 p-4">
          <p className="text-sm text-neutral-500">Categories</p>
          <p className="mt-1 text-xl font-semibold">{categoryCount ?? "—"}</p>
        </div>
      </div>
    </section>
  );
}
