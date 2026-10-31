import { Badge, type BadgeTone } from "./badge";
import { HeartIcon } from "./icons";

export type ProductCardProps = {
  name: string;
  detail: string;
  /** Formatted prices, e.g. "₹12,499". */
  price: string;
  mrp?: string;
  discount?: string;
  href: string;
  badge?: { label: string; tone: BadgeTone };
  /** Product photo; until photos exist, a two-colour placeholder gradient. */
  image: { src: string; alt: string } | { placeholder: [string, string] };
};

export function ProductCard({ name, detail, price, mrp, discount, href, badge, image }: ProductCardProps) {
  return (
    <article className="flex flex-col gap-1.5">
      <div className="relative aspect-[3/4] overflow-hidden rounded-sm">
        <a href={href} className="block h-full" aria-label={name}>
          {"src" in image ? (
            // eslint-disable-next-line @next/next/no-img-element -- package is framework-agnostic; apps pass optimised URLs
            <img src={image.src} alt={image.alt} className="h-full w-full object-cover" />
          ) : (
            <div
              role="img"
              aria-label={name}
              className="h-full w-full"
              style={{ backgroundImage: `linear-gradient(180deg, ${image.placeholder[0]}, ${image.placeholder[1]})` }}
            />
          )}
        </a>
        {badge && (
          <Badge tone={badge.tone} className="absolute left-2 top-2">
            {badge.label}
          </Badge>
        )}
        <button
          type="button"
          aria-label={`Add ${name} to wishlist`}
          className="absolute right-2 top-2 rounded-full bg-bg-surface p-[5px] text-text-primary hover:text-brand-primary"
        >
          <HeartIcon size={16} />
        </button>
      </div>
      <h3 className="text-sm leading-5 text-text-primary">
        <a href={href} className="hover:underline">
          {name}
        </a>
      </h3>
      <p className="text-xs text-text-muted">{detail}</p>
      <p className="flex items-center gap-1.5">
        <span className="text-[15px] font-semibold text-text-primary">{price}</span>
        {mrp && (
          <span className="text-xs text-text-muted line-through">
            <span className="sr-only">MRP </span>
            {mrp}
          </span>
        )}
        {discount && <span className="text-xs text-brand-primary">{discount}</span>}
      </p>
    </article>
  );
}
