import type { Metadata } from "next";
import { BagView } from "@/components/bag/bag-view";

export const metadata: Metadata = { title: "Your bag", robots: { index: false } };

// Per request, so the layout reads MEDIA_URL at runtime (it is not set when the Docker image is built).
export const dynamic = "force-dynamic";

export default function BagPage() {
  return <BagView />;
}
