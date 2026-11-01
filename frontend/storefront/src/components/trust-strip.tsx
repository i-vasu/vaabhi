import { CashIcon, HandloomIcon, ReturnIcon, TruckIcon } from "@vaabhi/ui";

const ITEMS = [
  [HandloomIcon, "Authentic handloom"],
  [TruckIcon, "Free shipping over ₹1,999"],
  [ReturnIcon, "7-day returns"],
  [CashIcon, "Cash on delivery"],
] as const;

export function TrustStrip() {
  return (
    <section aria-label="Why shop with us" className="border-y border-border-default bg-bg-muted">
      <ul className="mx-auto grid w-full max-w-6xl grid-cols-4 gap-2 px-4 py-6">
        {ITEMS.map(([Icon, label]) => (
          <li key={label} className="flex flex-col items-center gap-1.5 text-center text-xs text-text-secondary">
            <Icon size={24} className="text-brand-accent" />
            {label}
          </li>
        ))}
      </ul>
    </section>
  );
}
