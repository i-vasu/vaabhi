import type { Metadata } from "next";
import { connection } from "next/server";
import { SignInForm } from "@/components/auth/sign-in-form";
import { safeNext } from "@/lib/session";

export const metadata: Metadata = { title: "Sign in or sign up | Vaabhi", robots: { index: false } };

/** One page for both: a new number or Google account simply creates the account. */
export default async function SignupPage({ searchParams }: Readonly<{ searchParams: Promise<{ next?: string }> }>) {
  // Read at request time, so the same image works in every environment (no rebuild to set the client id).
  await connection();
  const googleClientId = process.env.GOOGLE_CLIENT_ID || null;
  const { next } = await searchParams;

  return (
    <section className="mx-auto max-w-sm px-4 pb-20 pt-12">
      <h1 className="text-center font-serif text-[32px] font-semibold">Sign in or sign up</h1>
      <p className="mb-8 mt-2 text-center text-text-muted">
        Track orders, keep your wishlist and check out faster. New here? Your account is created as you sign in.
      </p>
      <SignInForm googleClientId={googleClientId} next={safeNext(next)} />
      <p className="mt-8 text-center text-xs text-text-muted">
        By continuing you agree to our terms and privacy policy. We never post or share anything without asking.
      </p>
    </section>
  );
}
