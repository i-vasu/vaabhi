import type { Metadata } from "next";
import { AccountView } from "@/components/auth/account-view";

export const metadata: Metadata = { title: "Your account | Vaabhi", robots: { index: false } };

export default function AccountPage() {
  return (
    <section className="mx-auto max-w-6xl px-4 pb-20 pt-12">
      <h1 className="mb-2 text-center font-serif text-[32px] font-semibold">Your account</h1>
      <AccountView />
    </section>
  );
}
