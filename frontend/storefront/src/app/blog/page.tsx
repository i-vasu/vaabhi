import type { Metadata } from "next";
import Link from "next/link";
import type { ContentPageLink } from "@vaabhi/api-client";
import { buttonClasses } from "@vaabhi/ui";
import { api } from "@/lib/api";
import { postDate } from "@/lib/content-page";
import { pageHref } from "@/lib/site-links";

export const dynamic = "force-dynamic";

export const metadata: Metadata = {
  title: "Blog",
  description: "Stories from the loom: weaves, weavers, care and styling from Vaabhi.",
  alternates: { canonical: "/blog" },
};

async function loadPosts(): Promise<ContentPageLink[] | null> {
  try {
    return (await api.store.listPages({ next: { revalidate: 60 } })).filter((p) => p.section === "BLOG");
  } catch {
    return null;
  }
}

/** Blog posts published in the dashboard (Storefront → Website pages, column Blog), newest first. */
export default async function BlogPage() {
  const posts = await loadPosts();
  return (
    <section className="mx-auto flex w-full max-w-3xl flex-col gap-6 px-4 py-8">
      <h1 className="font-serif text-[32px] font-semibold leading-10 text-text-primary">Blog</h1>
      {posts === null ? (
        <p className="text-sm text-feedback-error">The blog is unavailable right now. Please try again shortly.</p>
      ) : posts.length === 0 ? (
        <div className="flex flex-col items-start gap-4 py-8">
          <p className="font-serif text-2xl text-text-primary">Stories are on their way</p>
          <p className="text-sm text-text-muted">Nothing is published yet. Browse what is new meanwhile.</p>
          <Link href="/new" className={buttonClasses("secondary")}>
            New arrivals
          </Link>
        </div>
      ) : (
        <ul className="flex flex-col divide-y divide-border-default">
          {posts.map((post) => (
            <li key={post.slug} className="py-5 first:pt-0">
              <article className="flex flex-col gap-1.5">
                <h2 className="font-serif text-[22px] font-semibold leading-tight text-text-primary">
                  <Link href={pageHref(post)} className="hover:text-brand-primary">
                    {post.title}
                  </Link>
                </h2>
                {post.summary && <p className="text-sm text-text-secondary">{post.summary}</p>}
                <p className="text-xs text-text-muted">{postDate(post.updatedAt)}</p>
              </article>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
