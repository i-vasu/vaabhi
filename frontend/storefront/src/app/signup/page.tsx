import { permanentRedirect } from "next/navigation";

/** The login page used to live here; old links and bookmarks go to /login, keeping where to return afterwards. */
export default async function SignupRedirect({ searchParams }: Readonly<{ searchParams: Promise<{ next?: string }> }>) {
  const { next } = await searchParams;
  permanentRedirect(next ? `/login?next=${encodeURIComponent(next)}` : "/login");
}
