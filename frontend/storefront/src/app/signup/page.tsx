import type { Metadata } from "next";
import Link from "next/link";
import { buttonClasses } from "@vaabhi/ui";

export const metadata: Metadata = { title: "Sign up or log in | Vaabhi", robots: { index: false } };

// Placeholder until customer sign-in is built.
export default function SignupPage() {
  return (
    <section className="mx-auto max-w-6xl px-4 pb-20 pt-12 text-center">
      <h1 className="font-serif text-[32px] font-semibold">Sign up or log in</h1>
      <p className="mx-auto mb-6 mt-2 max-w-[40ch] text-text-muted">
        Customer accounts are coming soon. Your bag and wishlist are saved on this device meanwhile.
      </p>
      <Link href="/" className={buttonClasses("primary")}>
        Continue shopping
      </Link>
    </section>
  );
}
