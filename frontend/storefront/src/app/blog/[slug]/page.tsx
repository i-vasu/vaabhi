import type { Metadata } from "next";
import { ContentPageView, pageMetadata } from "@/lib/content-page";

export const dynamic = "force-dynamic";

type Props = { params: Promise<{ slug: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  return pageMetadata((await params).slug);
}

/** A blog post written in the dashboard (Storefront → Website pages, column Blog). */
export default async function BlogPostPage({ params }: Readonly<Props>) {
  const { slug } = await params;
  return <ContentPageView slug={slug} path={`/blog/${slug}`} />;
}
