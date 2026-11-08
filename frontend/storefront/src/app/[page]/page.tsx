import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { ContentPageView, pageMetadata } from "@/lib/content-page";

export const dynamic = "force-dynamic";

type Props = { params: Promise<{ page: string }> };

/** Same rule as the API's page slugs; anything else is not a page, so it 404s without asking the API. */
const SLUG = /^[a-z0-9]+(-[a-z0-9]+)*$/;

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { page } = await params;
  return SLUG.test(page) ? pageMetadata(page) : {};
}

/** A help or about page written in the dashboard (Storefront → Pages), e.g. /size-guide or /about. */
export default async function TopLevelPage({ params }: Readonly<Props>) {
  const { page } = await params;
  if (!SLUG.test(page)) notFound();
  return <ContentPageView slug={page} path={`/${page}`} />;
}
