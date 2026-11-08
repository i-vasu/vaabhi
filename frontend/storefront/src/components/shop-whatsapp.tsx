import { api } from "@/lib/api";
import { WhatsAppButton } from "./whatsapp-button";

/** The floating WhatsApp button, shown once the shop has a phone number in Settings → Shop profile. */
export async function ShopWhatsApp() {
  let number: string | null = null;
  try {
    number = (await api.store.getShop({ next: { revalidate: 60 } })).whatsapp ?? null;
  } catch {
    // API down: no button rather than a broken one.
  }
  return number ? <WhatsAppButton number={number} /> : null;
}
