/** Store-wide wording shown on product pages. Edit here; a CMS can take over later. */
export const SHIPPING_AND_RETURNS = [
  "Ships in 1–2 days; stitched blouses add 5–10 days. Free shipping above ₹1,999.",
  "Returns within 7 days of delivery, unworn with tags. Stitched blouses cannot be returned.",
] as const;

export const REASSURANCE = ["Cash on delivery", "7-day easy returns", "Secure payment by Razorpay"] as const;

/** Same rule as the API (vaabhi.checkout): free shipping from ₹1,999 of items, otherwise a flat fee. */
export const FREE_SHIPPING_FROM = 1999;
export const SHIPPING_FEE = 99;

export function shippingFor(itemsRupees: number): number {
  return itemsRupees >= FREE_SHIPPING_FROM ? 0 : SHIPPING_FEE;
}
