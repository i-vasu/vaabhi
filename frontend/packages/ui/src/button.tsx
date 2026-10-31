import type { ButtonHTMLAttributes } from "react";
import { cn } from "./cn";

export type ButtonVariant = "primary" | "secondary";

/** Classes for the Figma "Button" component; also usable on links (`<Link className={buttonClasses()}>`). */
export function buttonClasses(variant: ButtonVariant = "primary", className?: string): string {
  return cn(
    "inline-flex items-center justify-center rounded-sm px-6 py-3.5 text-sm font-medium uppercase tracking-[0.04em] transition-colors",
    "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-primary disabled:opacity-50",
    variant === "primary"
      ? "bg-brand-primary text-text-on-brand hover:bg-brand-primary-hover"
      : "border border-brand-primary text-brand-primary hover:bg-brand-primary-soft",
    className,
  );
}

export type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & { variant?: ButtonVariant };

export function Button({ variant = "primary", className, type = "button", ...props }: ButtonProps) {
  return <button type={type} className={buttonClasses(variant, className)} {...props} />;
}
