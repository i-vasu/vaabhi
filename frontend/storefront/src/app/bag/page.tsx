import type { Metadata } from "next";
import { BagView } from "@/components/bag/bag-view";

export const metadata: Metadata = { title: "Your bag | Vaabhi", robots: { index: false } };

export default function BagPage() {
  return <BagView />;
}
