const inr = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 });

/** ₹12,499 (Indian digit grouping, no paise). */
export function formatPrice(amount: number): string {
  return inr.format(amount);
}

/** "22% off" from selling price and MRP; undefined when there is no discount. */
export function discountLabel(price: number, mrp?: number): string | undefined {
  if (!mrp || mrp <= price) return undefined;
  return `${Math.round(((mrp - price) / mrp) * 100)}% off`;
}
