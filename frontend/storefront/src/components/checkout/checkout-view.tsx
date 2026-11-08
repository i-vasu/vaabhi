"use client";

import Link from "next/link";
import { useEffect, useState, useSyncExternalStore } from "react";
import type { BagQuote, CheckoutStart, FieldError, WebOrder } from "@vaabhi/api-client";
import { Button, buttonClasses, cn } from "@vaabhi/ui";
import { bagStore } from "@/lib/bag";
import { fetchQuote } from "@/lib/bag-quote";
import { formatPrice } from "@/lib/format";
import { shippingFor } from "@/lib/policies";
import { openRazorpay } from "@/lib/razorpay";
import { syncSession, type Session } from "@/lib/session";

type Address = {
  name: string;
  phone: string;
  line1: string;
  line2: string;
  city: string;
  state: string;
  pincode: string;
};
const EMPTY_ADDRESS: Address = { name: "", phone: "", line1: "", line2: "", city: "", state: "", pincode: "" };

type Stage =
  | { kind: "loading" }
  | { kind: "signedOut" }
  | { kind: "ready" }
  | { kind: "paying" }
  | { kind: "placed"; placed: Placed };

type Method = WebOrder["paymentMethod"];

/** What the thank-you screen shows. */
type Placed = {
  number: string;
  totalRupees: number;
  paymentMethod: Method;
  addressLine1: string;
  city: string;
  pincode: string;
};

const input =
  "w-full rounded-sm border border-border-strong bg-bg-surface px-3 py-3 text-base outline-none focus:border-brand-primary";

async function post<T>(path: string, body: unknown): Promise<T> {
  const response = await fetch(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const json = (await response.json().catch(() => ({}))) as T & { detail?: string; errors?: FieldError[] };
  if (!response.ok) {
    throw Object.assign(new Error(json.detail ?? "Something went wrong. Please try again."), {
      status: response.status,
      errors: json.errors ?? [],
    });
  }
  return json;
}

/**
 * Checkout: the delivery address, the bag priced by the server, then Razorpay Checkout (UPI, cards,
 * netbanking). The order is placed when Pay is pressed and confirmed once Razorpay returns a signed payment.
 */
export function CheckoutView() {
  const lines = useSyncExternalStore(bagStore.subscribe, bagStore.read, bagStore.serverValue);
  const [stage, setStage] = useState<Stage>({ kind: "loading" });
  const [quote, setQuote] = useState<BagQuote | null>(null);
  const [address, setAddress] = useState<Address>(EMPTY_ADDRESS);
  const [error, setError] = useState<{ text: string; errors: FieldError[] } | null>(null);
  const [started, setStarted] = useState<CheckoutStart | null>(null);
  const [method, setMethod] = useState<Method>("ONLINE");

  useEffect(() => {
    void syncSession().then((session: Session) => {
      if (!session) {
        setStage({ kind: "signedOut" });
        return;
      }
      setAddress((a) => ({
        ...a,
        name: a.name || session.name || "",
        phone: a.phone || (session.phone ? session.phone.replace(/^\+91/, "") : ""),
      }));
      setStage({ kind: "ready" });
    });
  }, []);

  useEffect(() => {
    if (lines.length === 0) return;
    fetchQuote(lines)
      .then(setQuote)
      .catch(() => setError({ text: "We could not price your bag. Check your connection and try again.", errors: [] }));
  }, [lines]);

  if (stage.kind === "placed") return <Thanks placed={stage.placed} />;
  if (lines.length === 0) {
    return (
      <Shell>
        <p className="text-text-muted">Your bag is empty.</p>
        <Link href="/" className={buttonClasses("primary", "mt-4")}>
          Continue shopping
        </Link>
      </Shell>
    );
  }
  if (stage.kind === "loading") {
    return (
      <Shell>
        <p className="text-text-muted">Loading…</p>
      </Shell>
    );
  }
  if (stage.kind === "signedOut") {
    return (
      <Shell>
        <p className="mb-4 text-text-muted">Log in with your mobile number to check out. Your bag stays as it is.</p>
        <Link href="/login?next=/checkout" className={buttonClasses("primary")}>
          Login to check out
        </Link>
      </Shell>
    );
  }

  const items = quote?.totalRupees ?? 0;
  const shipping = shippingFor(items);
  const total = items + shipping;
  const fieldError = (field: string) => error?.errors.find((e) => e.field === `address.${field}`)?.message;
  const set = (patch: Partial<Address>) => setAddress((a) => ({ ...a, ...patch }));

  const placeOrder = (paymentMethod: Method) =>
    post<CheckoutStart>("/api/auth/checkout", {
      lines: lines.map((l) => ({
        sku: l.sku,
        addonCode: l.addonCode,
        blouseSize: l.blouseSize,
        quantity: l.quantity,
      })),
      address: { ...address, phone: address.phone.replaceAll(/\D/g, ""), line2: address.line2 || null },
      paymentMethod,
    });

  const thanks = (number: string, totalRupees: number, paymentMethod: Method) => {
    bagStore.write([]);
    setStage({
      kind: "placed",
      placed: {
        number,
        totalRupees,
        paymentMethod,
        addressLine1: address.line1,
        city: address.city,
        pincode: address.pincode,
      },
    });
  };

  async function pay() {
    setError(null);
    setStage({ kind: "paying" });
    try {
      if (method === "COD") {
        const placed = await placeOrder("COD");
        thanks(placed.orderNumber, placed.totalRupees, "COD");
        return;
      }
      const order = started ?? (await placeOrder("ONLINE"));
      setStarted(order);
      await openRazorpay(
        order,
        (response) => {
          void post<WebOrder>(`/api/auth/checkout/${encodeURIComponent(order.orderNumber)}/payment`, {
            razorpayOrderId: response.razorpay_order_id,
            razorpayPaymentId: response.razorpay_payment_id,
            razorpaySignature: response.razorpay_signature,
          })
            .then((paid) => {
              thanks(paid.number, paid.totalRupees, "ONLINE");
            })
            .catch((e: Error) => {
              setError({
                text: `${e.message}. If money left your account, it is safe: your order ${order.orderNumber} will be confirmed shortly.`,
                errors: [],
              });
              setStage({ kind: "ready" });
            });
        },
        () => {
          setError({ text: "Payment was not completed. You can try again.", errors: [] });
          setStage({ kind: "ready" });
        },
      );
    } catch (e) {
      const failure = e as Error & { errors?: FieldError[]; status?: number };
      if (failure.status === 401) {
        setStage({ kind: "signedOut" });
        return;
      }
      setError({ text: failure.message, errors: failure.errors ?? [] });
      setStage({ kind: "ready" });
    }
  }

  const paying = stage.kind === "paying";
  let buttonLabel = method === "COD" ? `Place order · ${formatPrice(total)} on delivery` : `Pay ${formatPrice(total)}`;
  if (paying) buttonLabel = method === "COD" ? "Placing order…" : "Opening payment…";
  return (
    <section className="mx-auto max-w-6xl px-4 pb-28 pt-6 md:pb-16">
      <h1 className="font-serif text-[32px] font-semibold">Checkout</h1>
      <div className="mt-4 grid gap-8 md:grid-cols-[minmax(0,1fr)_360px]">
        <form
          className="flex flex-col gap-4"
          onSubmit={(e) => {
            e.preventDefault();
            void pay();
          }}
        >
          <h2 className="text-lg font-semibold">Delivery address</h2>
          <TextField
            label="Full name"
            value={address.name}
            onChange={(v) => set({ name: v })}
            error={fieldError("name")}
            autoComplete="name"
            required
          />
          <TextField
            label="Mobile number (10 digits)"
            value={address.phone}
            onChange={(v) => set({ phone: v.replaceAll(/[^\d ]/g, "") })}
            error={fieldError("phone")}
            autoComplete="tel-national"
            inputMode="numeric"
            required
          />
          <TextField
            label="House, building, street"
            value={address.line1}
            onChange={(v) => set({ line1: v })}
            error={fieldError("line1")}
            autoComplete="address-line1"
            required
          />
          <TextField
            label="Area, landmark (optional)"
            value={address.line2}
            onChange={(v) => set({ line2: v })}
            error={fieldError("line2")}
            autoComplete="address-line2"
          />
          <div className="grid grid-cols-2 gap-3">
            <TextField
              label="City"
              value={address.city}
              onChange={(v) => set({ city: v })}
              error={fieldError("city")}
              autoComplete="address-level2"
              required
            />
            <TextField
              label="Pincode"
              value={address.pincode}
              onChange={(v) => set({ pincode: v.replaceAll(/\D/g, "").slice(0, 6) })}
              error={fieldError("pincode")}
              autoComplete="postal-code"
              inputMode="numeric"
              required
            />
          </div>
          <TextField
            label="State"
            value={address.state}
            onChange={(v) => set({ state: v })}
            error={fieldError("state")}
            autoComplete="address-level1"
            required
          />

          <fieldset className="mt-2 flex flex-col gap-2">
            <legend className="mb-1 text-lg font-semibold">Payment</legend>
            <PaymentChoice
              value="ONLINE"
              chosen={method}
              onChoose={setMethod}
              title="Pay now"
              detail="UPI, cards or netbanking, securely with Razorpay"
            />
            <PaymentChoice
              value="COD"
              chosen={method}
              onChoose={setMethod}
              title="Cash on delivery"
              detail="Pay in cash or UPI when the order arrives"
            />
          </fieldset>

          {error && (
            <p role="alert" className="text-sm text-feedback-error">
              {error.text}
            </p>
          )}
          <Button type="submit" disabled={paying || !quote} className="w-full md:hidden">
            {buttonLabel}
          </Button>
        </form>

        <aside className="h-fit rounded-md border border-border-default p-4">
          <h2 className="mb-3 text-lg font-semibold">Order summary</h2>
          <ul className="flex flex-col gap-2 text-sm">
            {lines.map((l) => (
              <li key={`${l.sku}|${l.addonCode ?? ""}|${l.blouseSize ?? ""}`} className="flex justify-between gap-3">
                <span className="min-w-0">
                  {l.name}{" "}
                  <span className="text-text-muted">· {[l.colourName, l.size].filter(Boolean).join(", ")}</span>
                  {l.addonName && <span className="block text-xs text-text-muted">{l.addonName}</span>}
                  <span className="block text-xs text-text-muted">Qty {l.quantity}</span>
                </span>
              </li>
            ))}
          </ul>
          <dl className="mt-4 flex flex-col gap-1.5 border-t border-border-default pt-3 text-sm tabular-nums">
            <Row label="Items" value={quote ? formatPrice(items) : "—"} />
            <Row label="Shipping" value={!quote ? "—" : shipping === 0 ? "Free" : formatPrice(shipping)} />
            <Row label="Total" value={quote ? formatPrice(total) : "—"} strong />
          </dl>
          <p className="mt-2 text-xs text-text-muted">
            Prices include GST. Final amount is confirmed on the payment screen.
          </p>
          <Button
            disabled={paying || !quote}
            onClick={() => void pay()}
            className={cn("mt-4 hidden w-full md:inline-flex")}
          >
            {buttonLabel}
          </Button>
          <p className="mt-3 text-center text-xs text-text-muted">
            UPI · Cards · Netbanking by Razorpay · Cash on delivery
          </p>
        </aside>
      </div>
    </section>
  );
}

function Shell({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <section className="mx-auto max-w-xl px-4 pb-20 pt-12 text-center">
      <h1 className="mb-4 font-serif text-[32px] font-semibold">Checkout</h1>
      {children}
    </section>
  );
}

function Row({ label, value, strong }: Readonly<{ label: string; value: string; strong?: boolean }>) {
  return (
    <div className={cn("flex justify-between", strong && "text-base font-semibold")}>
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  );
}

function TextField({
  label,
  value,
  onChange,
  error,
  ...props
}: Readonly<{ label: string; value: string; onChange: (value: string) => void; error?: string }> &
  Omit<React.InputHTMLAttributes<HTMLInputElement>, "value" | "onChange">) {
  return (
    <label className="flex flex-col gap-1.5 text-sm font-medium">
      <span>{label}</span>
      <input {...props} value={value} onChange={(e) => onChange(e.target.value)} className={input} />
      {error && <span className="text-xs font-normal text-feedback-error">{error}</span>}
    </label>
  );
}

function PaymentChoice({
  value,
  chosen,
  onChoose,
  title,
  detail,
}: Readonly<{ value: Method; chosen: Method; onChoose: (method: Method) => void; title: string; detail: string }>) {
  return (
    <label
      className={cn(
        "flex cursor-pointer items-start gap-3 rounded-sm border p-3 has-[:focus-visible]:outline-2",
        chosen === value ? "border-brand-primary" : "border-border-default",
      )}
    >
      <input
        type="radio"
        name="payment"
        value={value}
        checked={chosen === value}
        onChange={() => onChoose(value)}
        className="mt-1"
      />
      <span>
        <span className="block font-medium">{title}</span>
        <span className="block text-sm text-text-muted">{detail}</span>
      </span>
    </label>
  );
}

function Thanks({ placed }: Readonly<{ placed: Placed }>) {
  return (
    <Shell>
      <p className="text-lg font-semibold">Thank you! Your order is placed.</p>
      <p className="mt-1 text-text-muted">
        Order <strong className="text-text-primary">{placed.number}</strong> · {formatPrice(placed.totalRupees)}{" "}
        {placed.paymentMethod === "COD" ? "to pay on delivery" : "paid"}
      </p>
      <p className="mt-3 text-sm text-text-muted">
        We will deliver to {placed.addressLine1}, {placed.city} {placed.pincode}. We will text you when it ships.
      </p>
      <div className="mt-6 flex justify-center gap-3">
        <Link href="/account/orders" className={buttonClasses("secondary")}>
          Your orders
        </Link>
        <Link href="/" className={buttonClasses("primary")}>
          Continue shopping
        </Link>
      </div>
    </Shell>
  );
}
