import type { MessagePurpose, StockUnit } from "@vaabhi/api-client";

export const UNITS: { value: StockUnit; label: string }[] = [
  { value: "PCS", label: "pieces" },
  { value: "M", label: "metres" },
  { value: "CM", label: "cm" },
  { value: "KG", label: "kg" },
  { value: "G", label: "grams" },
  { value: "L", label: "litres" },
  { value: "ROLL", label: "rolls" },
  { value: "BOX", label: "boxes" },
  { value: "SET", label: "sets" },
];

export const unitLabel = (unit: StockUnit) => UNITS.find((u) => u.value === unit)?.label ?? unit;

/** 3.5 → "3.5", 2 → "2" (stock quantities keep at most two decimals). */
export const qty = (n: number) => String(Number(n.toFixed(2)));

export const PURPOSES: { value: MessagePurpose; label: string }[] = [
  { value: "ORDER_PLACED", label: "Order booked" },
  { value: "ORDER_READY", label: "Order ready" },
  { value: "ORDER_DELIVERED", label: "Order delivered" },
  { value: "PAYMENT", label: "Payment received" },
  { value: "REVIEW", label: "Review request" },
  { value: "REWARD", label: "Reward code" },
  { value: "CUSTOM", label: "Own message" },
];

export const purposeLabel = (purpose: MessagePurpose) => PURPOSES.find((p) => p.value === purpose)?.label ?? purpose;
