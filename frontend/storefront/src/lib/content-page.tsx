import type { Metadata } from "next";
import Link from "next/link";
import { notFound, permanentRedirect } from "next/navigation";
import { ApiError, type ContentPage } from "@vaabhi/api-client";
import { PageBody } from "@/components/page-body";
import { api } from "@/lib/api";
import { pageHref } from "@/lib/site-links";

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

/** Renders the page if it belongs at this URL; a page opened at the other URL form is sent to its own. */
export async function ContentPageView({ slug, at }: Readonly<{ slug: string; at: "policies" | "top" }>) {
  const page = await loadPage(slug);
  const isPolicy = page.section === "POLICY";
  if (isPolicy !== (at === "policies")) permanentRedirect(pageHref(page));

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
          <li aria-current="page" className="text-text-primary">
            {page.title}
          </li>
        </ol>
      </nav>
      <h1 className="font-serif text-[32px] font-semibold leading-10 text-text-primary">{page.title}</h1>
      <PageBody body={page.body} />
      <p className="pt-4 text-xs text-text-muted">
        Last updated {new Date(page.updatedAt).toLocaleDateString("en-IN", { dateStyle: "long" })}
      </p>
    </article>
  );
}
