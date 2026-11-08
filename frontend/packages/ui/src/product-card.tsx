import type { ReactNode } from "react";
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
  image: { src: string; alt: string; srcSet?: string; sizes?: string } | { placeholder: [string, string] };
  /**
   * The wishlist control in the photo's corner, supplied by the app (it owns where the wishlist is kept); usually
   * a {@link WishlistHeart}. Without it the card shows no heart.
   */
  wishlist?: ReactNode;
};

/** The round heart button the app puts in a card's `wishlist` slot; `pressed` when the product is saved. */
export function WishlistHeart({ name, pressed, onToggle }: { name: string; pressed: boolean; onToggle: () => void }) {
  return (
    <button
      type="button"
      aria-pressed={pressed}
      aria-label={pressed ? `Remove ${name} from wishlist` : `Add ${name} to wishlist`}
      onClick={onToggle}
      className="absolute right-2 top-2 rounded-full bg-bg-surface p-[5px] text-text-primary hover:text-brand-primary"
    >
      <HeartIcon size={16} className={pressed ? "fill-brand-primary text-brand-primary" : undefined} />
    </button>
  );
}

export function ProductCard({ name, detail, price, mrp, discount, href, badge, image, wishlist }: ProductCardProps) {
  return (
    <article className="flex flex-col gap-1.5">
      <div className="relative aspect-[3/4] overflow-hidden rounded-sm">
        <a href={href} className="block h-full" aria-label={name}>
          {"src" in image ? (
            // eslint-disable-next-line @next/next/no-img-element -- package is framework-agnostic; apps pass optimised URLs
            <img
              src={image.src}
              srcSet={image.srcSet}
              sizes={image.sizes}
              alt={image.alt}
              loading="lazy"
              decoding="async"
              className="h-full w-full object-cover"
            />
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
        {wishlist}
      </div>
      <h3 className="text-sm leading-5 text-text-primary">
        <a href={href} className="hover:underline">
          {name}
        </a>
      </h3>
      <p className="text-xs text-text-muted">{detail}</p>
      {/* The spaces keep the copied/read text apart ("₹12,499 MRP ₹15,999 22% off"); flex ignores them in layout. */}
      <p className="flex items-center gap-1.5">
        <span className="text-[15px] font-semibold text-text-primary">{price}</span>{" "}
        {mrp && (
          <span className="text-xs text-text-muted line-through">
            <span className="sr-only">MRP </span>
            {mrp}
          </span>
        )}{" "}
        {discount && <span className="text-xs text-brand-primary">{discount}</span>}
      </p>
    </article>
  );
}
