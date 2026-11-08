import type { Metadata } from "next";
import Link from "next/link";
import { notFound, permanentRedirect } from "next/navigation";
import { ApiError, type ContentPage } from "@vaabhi/api-client";
import { PageBody } from "@/components/page-body";
import { api } from "@/lib/api";
import { pageHref } from "@/lib/site-links";

/** "8 November 2026". */
export function postDate(iso: string): string {
  return new Date(iso).toLocaleDateString("en-IN", { dateStyle: "long" });
}

/** A published page, or 404 (draft, unknown slug). Throws other API failures so the error page shows. */
export async function loadPage(slug: string): Promise<ContentPage> {
  try {
    return await api.store.getPage(slug, { next: { revalidate: 60 } });
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) notFound();
    throw error;
  }
}

export async function pageMetadata(slug: string): Promise<Metadata> {
  try {
    const page = await api.store.getPage(slug, { next: { revalidate: 60 } });
    return { title: page.title, description: page.summary ?? undefined, alternates: { canonical: pageHref(page) } };
  } catch {
    return {};
  }
}

/**
 * Renders the page if it lives at `path`; a page opened at another address (a policy at /shipping, a blog post at
 * /my-post) is sent to its own.
 */
export async function ContentPageView({ slug, path }: Readonly<{ slug: string; path: string }>) {
  const page = await loadPage(slug);
  if (pageHref(page) !== path) permanentRedirect(pageHref(page));
  const isPost = page.section === "BLOG";

  return (
    <article className="mx-auto flex w-full max-w-3xl flex-col gap-4 px-4 py-8">
      <nav aria-label="Breadcrumb">
        <ol className="flex gap-1.5 text-xs text-text-muted">
          <li>
            <Link href="/" className="hover:underline">
              Home
            </Link>
          </li>
          <li aria-hidden="true">/</li>
          {isPost && (
            <>
              <li>
                <Link href="/blog" className="hover:underline">
                  Blog
                </Link>
              </li>
              <li aria-hidden="true">/</li>
            </>
          )}
          <li aria-current="page" className="text-text-primary">
            {page.title}
          </li>
        </ol>
      </nav>
      <h1 className="font-serif text-[32px] font-semibold leading-10 text-text-primary">{page.title}</h1>
      {isPost && page.summary && <p className="text-lg text-text-secondary">{page.summary}</p>}
      <PageBody body={page.body} />
      <p className="pt-4 text-xs text-text-muted">
        {isPost ? "Posted" : "Last updated"} {postDate(page.updatedAt)}
      </p>
    </article>
  );
}
