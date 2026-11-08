import type { Metadata } from "next";
import { ContentPageView, pageMetadata } from "@/lib/content-page";

export const dynamic = "force-dynamic";

type Props = { params: Promise<{ slug: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  return pageMetadata((await params).slug);
}

/** A policy written in the dashboard (Storefront → Pages, section Policies), e.g. /policies/shipping. */
export default async function PolicyPage({ params }: Readonly<Props>) {
  return <ContentPageView slug={(await params).slug} at="policies" />;
}
