"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense } from "react";
import { ProductEditor } from "@/components/product-editor";

/** /products/edit/?id=42 — a query string, because the static export cannot pre-render product ids. */
function EditProduct() {
  const params = useSearchParams();
  const id = Number(params.get("id"));
  if (!Number.isInteger(id) || id <= 0) {
    return (
      <p className="text-sm">
        No product selected.{" "}
        <Link href="/products/" className="underline">
          Back to products
        </Link>
      </p>
    );
  }
  return <ProductEditor key={id} id={id} notice={params.get("notice") ?? undefined} />;
}

export default function EditProductPage() {
  return (
    <Suspense fallback={<p className="text-sm text-neutral-500">Loading…</p>}>
      <EditProduct />
    </Suspense>
  );
}
