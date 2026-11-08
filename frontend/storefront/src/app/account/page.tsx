import type { Metadata } from "next";
import type { ShopMeasurementForm } from "@vaabhi/api-client";
import { AccountView } from "@/components/auth/account-view";
import { api } from "@/lib/api";

export const dynamic = "force-dynamic";

export const metadata: Metadata = { title: "Your account", robots: { index: false } };

/** The shop's measurement forms (Settings → Garments); none when the API cannot be reached. */
async function loadForms(): Promise<ShopMeasurementForm[]> {
  try {
    return await api.store.listMeasurementForms({ next: { revalidate: 300 } });
  } catch {
    return [];
  }
}

export default async function AccountPage() {
  const forms = await loadForms();
  return (
    <section className="mx-auto max-w-6xl px-4 pb-20 pt-12">
      <h1 className="mb-2 text-center font-serif text-[32px] font-semibold">Your account</h1>
      <AccountView measurementForms={forms} />
    </section>
  );
}
