const ONES = [
  "Zero",
  "One",
  "Two",
  "Three",
  "Four",
  "Five",
  "Six",
  "Seven",
  "Eight",
  "Nine",
  "Ten",
  "Eleven",
  "Twelve",
  "Thirteen",
  "Fourteen",
  "Fifteen",
  "Sixteen",
  "Seventeen",
  "Eighteen",
  "Nineteen",
];
const TENS = ["", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"];

function underHundred(n: number): string {
  if (n < 20) return ONES[n] ?? "";
  return TENS[Math.floor(n / 10)] + (n % 10 ? ` ${ONES[n % 10]}` : "");
}

function underThousand(n: number): string {
  const hundreds = Math.floor(n / 100);
  const rest = n % 100;
  return [hundreds ? `${ONES[hundreds]} Hundred` : "", rest ? underHundred(rest) : ""].filter(Boolean).join(" ");
}

/** Whole number in Indian numbering (thousand, lakh, crore). */
function wholeInWords(n: number): string {
  if (n === 0) return "Zero";
  const parts: string[] = [];
  const crore = Math.floor(n / 10_000_000);
  const lakh = Math.floor((n % 10_000_000) / 100_000);
  const thousand = Math.floor((n % 100_000) / 1000);
  const rest = n % 1000;
  if (crore) parts.push(`${wholeInWords(crore)} Crore`);
  if (lakh) parts.push(`${underHundred(lakh)} Lakh`);
  if (thousand) parts.push(`${underHundred(thousand)} Thousand`);
  if (rest) parts.push(underThousand(rest));
  return parts.join(" ");
}

/** "Rupees One Thousand Fifty only", or "… and Fifty Paise only" when there are paise. */
export function amountInWords(rupees: number): string {
  const totalPaise = Math.round(Math.abs(rupees) * 100);
  const whole = Math.floor(totalPaise / 100);
  const paise = totalPaise % 100;
  const base = `Rupees ${wholeInWords(whole)}`;
  return paise > 0 ? `${base} and ${underHundred(paise)} Paise only` : `${base} only`;
}
