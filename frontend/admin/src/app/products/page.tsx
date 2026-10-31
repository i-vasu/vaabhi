"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ApiError, type AdminProductPage, type Category, type ProductStatus } from "@vaabhi/api-client";
import { buttonClasses, cn } from "@vaabhi/ui";
import { api } from "@/lib/api";
import { STATUS_LABELS } from "@/lib/product-editor";

const PAGE_SIZE = 50;
const TABS: Array<{ label: string; value?: ProductStatus }> = [
  { label: "All" },
  { label: "Drafts", value: "DRAFT" },
  { label: "Published", value: "PUBLISHED" },
  { label: "Archived", value: "ARCHIVED" },
];

const inr = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 });
const date = new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });

type Filters = { status?: ProductStatus; category: string; q: string; page: number };

export default function ProductsPage() {
  const [filters, setFilters] = useState<Filters>({ category: "", q: "", page: 1 });
  const [search, setSearch] = useState("");
  const [categories, setCategories] = useState<Category[]>([]);
  const [result, setResult] = useState<AdminProductPage | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.store
      .listCategories()
      .then(setCategories)
      .catch(() => setCategories([]));
  }, []);

  useEffect(() => {
    let cancelled = false;
    api.admin
      .listProducts({
        status: filters.status,
        category: filters.category || undefined,
        q: filters.q || undefined,
        page: filters.page,
        pageSize: PAGE_SIZE,
      })
      .then((page) => {
        if (cancelled) return;
        setResult(page);
        setError(null);
      })
      .catch((e: unknown) => {
        if (cancelled) return;
        setError(
          e instanceof ApiError && (e.status === 401 || e.status === 403)
            ? "Sign in with an admin token to manage products."
            : "Could not load products. Check that the API is up.",
        );
      });
    return () => {
      cancelled = true;
    };
  }, [filters]);

  const lastPage = result ? Math.max(1, Math.ceil(result.total / PAGE_SIZE)) : 1;

  return (
    <section className="flex flex-col gap-5">
      <header className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Products</h1>
        <Link href="/products/new/" className={buttonClasses()}>
          New product
        </Link>
      </header>

      <div className="flex flex-wrap items-center gap-3">
        <div role="group" aria-label="Status" className="flex rounded-md border border-neutral-300 p-0.5 text-sm">
          {TABS.map((tab) => (
            <button
              key={tab.label}
              type="button"
              aria-pressed={filters.status === tab.value}
              onClick={() => setFilters({ ...filters, status: tab.value, page: 1 })}
              className={cn(
                "rounded px-3 py-1",
                filters.status === tab.value ? "bg-neutral-900 text-white" : "hover:bg-neutral-100",
              )}
            >
              {tab.label}
            </button>
          ))}
        </div>
        <select
          aria-label="Category"
          className="rounded-md border border-neutral-300 px-2 py-1.5 text-sm"
          value={filters.category}
          onChange={(e) => setFilters({ ...filters, category: e.target.value, page: 1 })}
        >
          <option value="">All categories</option>
          {categories.map((c) => (
            <option key={c.slug} value={c.slug}>
              {c.name}
            </option>
          ))}
        </select>
        <form
          role="search"
          onSubmit={(e) => {
            e.preventDefault();
            setFilters({ ...filters, q: search.trim(), page: 1 });
          }}
        >
          <input
            type="search"
            aria-label="Search products"
            placeholder="Name, URL or SKU"
            className="w-64 rounded-md border border-neutral-300 px-2.5 py-1.5 text-sm"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </form>
      </div>

      {error && (
        <p role="alert" className="text-sm text-red-700">
          {error}{" "}
          <Link href="/login/" className="underline">
            Sign in
          </Link>
        </p>
      )}

      {result && !error && (
        <>
          {result.items.length === 0 ? (
            <p className="text-sm text-neutral-500">No products match.</p>
          ) : (
            <table className="w-full text-left text-sm">
              <thead className="border-b border-neutral-200 text-xs uppercase text-neutral-500">
                <tr>
                  <th className="py-2 pr-3 font-medium">Name</th>
                  <th className="py-2 pr-3 font-medium">Category</th>
                  <th className="py-2 pr-3 font-medium">Status</th>
                  <th className="py-2 pr-3 text-right font-medium">Variants</th>
                  <th className="py-2 pr-3 text-right font-medium">Stock</th>
                  <th className="py-2 pr-3 text-right font-medium">From</th>
                  <th className="py-2 font-medium">Updated</th>
                </tr>
              </thead>
              <tbody>
                {result.items.map((p) => (
                  <tr key={p.id} className="border-b border-neutral-100">
                    <td className="py-2 pr-3">
                      <Link href={`/products/edit/?id=${p.id}`} className="font-medium hover:underline">
                        {p.name}
                      </Link>
                      <span className="block text-xs text-neutral-500">{p.slug}</span>
                    </td>
                    <td className="py-2 pr-3">{p.category.name}</td>
                    <td className="py-2 pr-3">{STATUS_LABELS[p.status]}</td>
                    <td className="py-2 pr-3 text-right">{p.variantCount}</td>
                    <td className={cn("py-2 pr-3 text-right", p.stockTotal === 0 && "text-red-700")}>{p.stockTotal}</td>
                    <td className="py-2 pr-3 text-right">
                      {p.minPricePaise === null ? "—" : inr.format(p.minPricePaise / 100)}
                    </td>
                    <td className="py-2 text-neutral-500">{date.format(new Date(p.updatedAt))}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          <footer className="flex items-center gap-3 text-sm">
            <span className="text-neutral-500">
              {result.total} {result.total === 1 ? "product" : "products"}
            </span>
            {lastPage > 1 && (
              <>
                <button
                  type="button"
                  disabled={filters.page <= 1}
                  onClick={() => setFilters({ ...filters, page: filters.page - 1 })}
                  className="underline disabled:opacity-40"
                >
                  Previous
                </button>
                <span>
                  Page {filters.page} of {lastPage}
                </span>
                <button
                  type="button"
                  disabled={filters.page >= lastPage}
                  onClick={() => setFilters({ ...filters, page: filters.page + 1 })}
                  className="underline disabled:opacity-40"
                >
                  Next
                </button>
              </>
            )}
          </footer>
        </>
      )}
    </section>
  );
}
