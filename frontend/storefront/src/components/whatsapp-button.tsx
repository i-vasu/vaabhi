"use client";

import { usePathname } from "next/navigation";
import { WhatsAppIcon } from "@vaabhi/ui";
import { whatsappLink } from "@/lib/whatsapp";

/** Where the chat button would cover the page's own controls, or the chat makes no sense. */
const HIDDEN_ON = ["/checkout"];

/**
 * Floating "chat on WhatsApp" button on every page. Opens WhatsApp (the app on phones, WhatsApp Web on computers)
 * with a message to the shop that names the page the customer is on, so a question about a product arrives with
 * its link. `number` is the shop phone from Settings → Shop profile, as digits with the country code.
 */
export function WhatsAppButton({ number }: Readonly<{ number: string }>) {
  const pathname = usePathname();
  if (HIDDEN_ON.some((path) => pathname.startsWith(path))) return null;

  const onProduct = pathname.startsWith("/p/");
  const href = () => {
    const page = typeof window === "undefined" ? "" : window.location.href.split("#")[0];
    return whatsappLink(
      number,
      onProduct ? `Hi Vaabhi, I have a question about this: ${page}` : "Hi Vaabhi, I have a question",
    );
  };

  return (
    <a
      href={whatsappLink(number, "Hi Vaabhi, I have a question")}
      onClick={(event) => {
        // Built on click so the message carries the page's full address, colour choice included.
        event.currentTarget.href = href();
      }}
      target="_blank"
      rel="noopener"
      aria-label="Chat with us on WhatsApp"
      title="Chat with us on WhatsApp"
      // Above the product page's sticky "Add to bag" bar on phones.
      className="fixed bottom-20 right-4 z-40 grid size-14 place-items-center rounded-full bg-[#25D366] text-white shadow-lg transition-transform hover:scale-105 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-primary md:bottom-6 md:right-6"
    >
      <WhatsAppIcon size={30} />
    </a>
  );
}
