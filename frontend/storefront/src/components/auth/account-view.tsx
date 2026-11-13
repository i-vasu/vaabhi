"use client";

import Link from "next/link";
import { useEffect, useId, useState, useSyncExternalStore } from "react";
import type { CustomerDetails, ShopMeasurementForm } from "@vaabhi/api-client";
import { Button, buttonClasses } from "@vaabhi/ui";
import { AuthError, auth, displayName, sessionStore, syncSession, toSession, type Session } from "@/lib/session";

const label = "mb-1.5 block text-xs uppercase tracking-[0.08em] text-text-muted";
const input =
  "w-full rounded-sm border border-border-strong bg-bg-surface px-3.5 py-3 text-base text-text-primary outline-none focus:border-brand-primary disabled:bg-bg-muted";
const card = "flex flex-col gap-4 rounded-sm border border-border-default bg-bg-surface p-4";

const EMPTY: CustomerDetails = {
  dateOfBirth: null,
  addressLine1: null,
  addressLine2: null,
  city: null,
  state: null,
  pincode: null,
  measurements: {},
};

/**
 * The account page: the customer's details (name, date of birth, mobile, email), delivery address and
 * measurements for the shop's garments. Orders, wishlist and log out live in the account menu. Guests get a
 * login prompt.
 */
export function AccountView({ measurementForms }: Readonly<{ measurementForms: ShopMeasurementForm[] }>) {
  const session = useSyncExternalStore(sessionStore.subscribe, sessionStore.read, sessionStore.serverValue);
  const [checked, setChecked] = useState(false);
  const [details, setDetails] = useState<CustomerDetails | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    void syncSession().then(async (current) => {
      setChecked(true);
      if (!current) return;
      try {
        setDetails((await auth.details()).details);
      } catch (error) {
        setLoadError(error instanceof AuthError ? error.message : "Your details could not be loaded.");
      }
    });
  }, []);

  if (!session) {
    return (
      <div className="text-center">
        <p className="mx-auto mb-6 mt-2 max-w-[40ch] text-text-muted">
          {checked ? "Log in to see your profile, address and measurements." : "Checking your account…"}
        </p>
        {checked && (
          <Link href="/login?next=%2Faccount" className={buttonClasses("primary")}>
            Login
          </Link>
        )}
      </div>
    );
  }

  return (
    <div className="mx-auto flex max-w-xl flex-col gap-6">
      <p className="text-center text-text-muted">Hi, {displayName(session)}</p>
      {loadError ? (
        <p role="alert" className="text-center text-sm text-feedback-error">
          {loadError}
        </p>
      ) : !details ? (
        <p className="text-center text-sm text-text-muted">Loading your details…</p>
      ) : (
        <AccountForm session={session} initial={details} forms={measurementForms} />
      )}
    </div>
  );
}

/** 98765 43210 → +91 98765 43210, for "we sent a code to …". */
const pretty = (e164: string) => `+91 ${e164.slice(3, 8)} ${e164.slice(8)}`;

/** A seconds counter that ticks down to 0; start it with the returned setter. */
function useCountdown(): [number, (seconds: number) => void] {
  const [left, setLeft] = useState(0);
  useEffect(() => {
    if (left <= 0) return;
    const timer = setTimeout(() => setLeft((s) => s - 1), 1000);
    return () => clearTimeout(timer);
  }, [left]);
  return [left, setLeft];
}

type PhoneProblem = { field?: "phone" | "code"; message: string };

/** The mobile number row: shows the number, or a 2-step form to add or change it (verified by a WhatsApp code). */
function PhoneEditor({ phone, id }: Readonly<{ phone: string | null; id: string }>) {
  const [editing, setEditing] = useState(false);
  const [step, setStep] = useState<{ kind: "number" } | { kind: "code"; phone: string }>({ kind: "number" });
  const [number, setNumber] = useState("");
  const [code, setCode] = useState("");
  const [problem, setProblem] = useState<PhoneProblem | null>(null);
  const [busy, setBusy] = useState(false);
  const [resendIn, startResendTimer] = useCountdown();

  const cancel = () => {
    setEditing(false);
    setStep({ kind: "number" });
    setNumber("");
    setCode("");
    setProblem(null);
    setBusy(false);
  };

  async function run(action: () => Promise<void>, field: PhoneProblem["field"]) {
    setBusy(true);
    setProblem(null);
    try {
      await action();
    } catch (error) {
      if (error instanceof AuthError) {
        setProblem({
          field: error.forField(field ?? "") ? field : undefined,
          message: error.forField(field ?? "") ?? error.message,
        });
        if (error.retryAfterSeconds) startResendTimer(error.retryAfterSeconds);
      } else {
        setProblem({ message: "Something went wrong. Please try again." });
      }
    } finally {
      setBusy(false);
    }
  }

  const sendCode = (to: string) =>
    run(async () => {
      const sent = await auth.requestPhoneChange(to);
      setCode("");
      setStep({ kind: "code", phone: sent.phone });
      startResendTimer(sent.resendAfterSeconds);
    }, "phone");

  const verify = (phoneToVerify: string) =>
    run(async () => {
      const { customer } = await auth.changePhone(phoneToVerify, code);
      sessionStore.write(toSession(customer));
      cancel();
    }, "code");

  const submitNumber = () => {
    if (number.replaceAll(/\D/g, "").length < 10) {
      setProblem({ field: "phone", message: "Enter a valid 10-digit Indian mobile number" });
      return;
    }
    void sendCode(number);
  };
  const submitCode = (phoneToVerify: string) => {
    if (!/^\d{6}$/.test(code)) {
      setProblem({ field: "code", message: "Enter the 6-digit code" });
      return;
    }
    void verify(phoneToVerify);
  };

  const message = (field?: PhoneProblem["field"]) =>
    problem && problem.field === field ? (
      <p role="alert" className="mt-1.5 text-sm text-feedback-error">
        {problem.message}
      </p>
    ) : null;

  if (!editing) {
    return (
      <div>
        <label htmlFor={`${id}-phone`} className={label}>
          Mobile
        </label>
        <div className="flex items-start gap-2">
          <input id={`${id}-phone`} value={phone ? `+91 ${phone.slice(3)}` : "Not added"} disabled className={input} />
          <button
            type="button"
            onClick={() => {
              setProblem(null);
              setStep({ kind: "number" });
              setNumber("");
              setEditing(true);
            }}
            className="mt-3 shrink-0 text-sm font-medium text-brand-primary underline"
          >
            {phone ? "Change" : "Add mobile number"}
          </button>
        </div>
        <p className="mt-1.5 text-xs text-text-muted">
          {phone
            ? "Your login number, verified by WhatsApp."
            : "Add your mobile number to place orders and get updates on WhatsApp."}
        </p>
      </div>
    );
  }

  if (step.kind === "number") {
    return (
      <div>
        <label htmlFor={`${id}-new-phone`} className={label}>
          New mobile number
        </label>
        <div className="flex items-stretch rounded-sm border border-border-strong bg-bg-surface focus-within:border-brand-primary">
          <span className="flex items-center border-r border-border-default px-3 text-text-secondary">+91</span>
          <input
            id={`${id}-new-phone`}
            type="tel"
            inputMode="numeric"
            autoComplete="tel-national"
            maxLength={14}
            placeholder="98765 43210"
            value={number}
            onChange={(e) => setNumber(e.target.value.replaceAll(/[^\d ]/g, ""))}
            onKeyDown={(e) => {
              // Inside the account form: Enter sends the code instead of saving the other fields.
              if (e.key === "Enter") {
                e.preventDefault();
                submitNumber();
              }
            }}
            className="min-w-0 flex-1 bg-transparent px-3 py-3 text-base tabular-nums outline-none"
          />
        </div>
        {message("phone")}
        {message()}
        <div className="mt-2 flex items-center gap-3">
          <Button variant="secondary" disabled={busy} onClick={submitNumber}>
            {busy ? "Sending…" : "Send code"}
          </Button>
          <button type="button" onClick={cancel} className="text-sm text-text-secondary underline">
            Cancel
          </button>
        </div>
      </div>
    );
  }

  return (
    <div>
      <label htmlFor={`${id}-code`} className={label}>
        Verification code
      </label>
      <p className="mb-1.5 text-sm text-text-secondary">
        We sent a 6-digit code on WhatsApp to{" "}
        <span className="font-medium text-text-primary">{pretty(step.phone)}</span>.{" "}
        <button type="button" className="text-brand-primary underline" onClick={() => setStep({ kind: "number" })}>
          Change
        </button>
      </p>
      <input
        id={`${id}-code`}
        inputMode="numeric"
        autoComplete="one-time-code"
        maxLength={6}
        value={code}
        onChange={(e) => setCode(e.target.value.replaceAll(/\D/g, ""))}
        onKeyDown={(e) => {
          if (e.key === "Enter") {
            e.preventDefault();
            submitCode(step.phone);
          }
        }}
        className={`${input} text-center text-xl tracking-[0.5em] tabular-nums`}
      />
      {message("code")}
      {message("phone")}
      {message()}
      <div className="mt-2 flex items-center gap-3">
        <Button variant="secondary" disabled={busy} onClick={() => submitCode(step.phone)}>
          {busy ? "Verifying…" : "Verify"}
        </Button>
        <button type="button" onClick={cancel} className="text-sm text-text-secondary underline">
          Cancel
        </button>
      </div>
      <p className="mt-2 text-center text-sm text-text-secondary">
        {resendIn > 0 ? (
          `Resend code in ${resendIn}s`
        ) : (
          <button
            type="button"
            disabled={busy}
            onClick={() => void sendCode(step.phone)}
            className="text-brand-primary underline"
          >
            Resend code
          </button>
        )}
      </p>
    </div>
  );
}

type SaveState = { status: "idle" | "saving" | "saved" } | { status: "error"; message: string };

function AccountForm({
  session,
  initial,
  forms,
}: Readonly<{ session: NonNullable<Session>; initial: CustomerDetails; forms: ShopMeasurementForm[] }>) {
  const id = useId();
  const [name, setName] = useState(session.name ?? "");
  const [details, setDetails] = useState<CustomerDetails>({ ...EMPTY, ...initial });
  const [state, setState] = useState<SaveState>({ status: "idle" });
  const touched = () => state.status !== "idle" && setState({ status: "idle" });
  const set = (patch: Partial<CustomerDetails>) => {
    setDetails((d) => ({ ...d, ...patch }));
    touched();
  };
  const text = (value: string) => (value.trim() ? value : null);
  const measurement = (formId: number, key: string) => details.measurements[String(formId)]?.[key] ?? "";
  const setMeasurement = (formId: number, key: string, value: string) =>
    set({
      measurements: {
        ...details.measurements,
        [String(formId)]: { ...details.measurements[String(formId)], [key]: value },
      },
    });

  const field = (key: keyof Omit<CustomerDetails, "measurements" | "dateOfBirth">, title: string, extra = {}) => (
    <div>
      <label htmlFor={`${id}-${key}`} className={label}>
        {title}
      </label>
      <input
        id={`${id}-${key}`}
        maxLength={key === "pincode" ? 6 : key === "city" || key === "state" ? 80 : 200}
        value={details[key] ?? ""}
        onChange={(e) => set({ [key]: text(key === "pincode" ? e.target.value.replace(/\D/g, "") : e.target.value) })}
        className={input}
        {...extra}
      />
    </div>
  );

  return (
    <form
      className="flex flex-col gap-6"
      onSubmit={async (event) => {
        event.preventDefault();
        setState({ status: "saving" });
        try {
          if (name.trim() && name.trim() !== (session.name ?? "")) {
            const { customer } = await auth.updateName(name.trim());
            sessionStore.write(toSession(customer));
          }
          const saved = await auth.saveDetails(details);
          setDetails({ ...EMPTY, ...saved.details });
          setState({ status: "saved" });
        } catch (error) {
          setState({
            status: "error",
            message:
              error instanceof AuthError
                ? error.errors.length
                  ? error.errors.map((e) => e.message).join("; ")
                  : error.message
                : "Could not save. Try again.",
          });
        }
      }}
    >
      <section className={card} aria-labelledby={`${id}-details`}>
        <h2 id={`${id}-details`} className="font-serif text-[22px] font-semibold text-text-primary">
          Your details
        </h2>
        <div>
          <label htmlFor={`${id}-name`} className={label}>
            Name
          </label>
          <input
            id={`${id}-name`}
            autoComplete="name"
            maxLength={80}
            value={name}
            onChange={(e) => {
              setName(e.target.value);
              touched();
            }}
            className={input}
          />
        </div>
        <div>
          <label htmlFor={`${id}-dob`} className={label}>
            Date of birth
          </label>
          <input
            id={`${id}-dob`}
            type="date"
            autoComplete="bday"
            max={new Date().toISOString().slice(0, 10)}
            value={details.dateOfBirth ?? ""}
            onChange={(e) => set({ dateOfBirth: e.target.value || null })}
            className={input}
          />
        </div>
        <PhoneEditor phone={session.phone} id={id} />
        <div>
          <label htmlFor={`${id}-email`} className={label}>
            Email
          </label>
          <input id={`${id}-email`} value={session.email ?? "Not added"} disabled className={input} />
          <p className="mt-1.5 text-xs text-text-muted">
            {session.email ? "From your Google login." : "Log in with Google to add your email."}
          </p>
        </div>
      </section>

      <section className={card} aria-labelledby={`${id}-address`}>
        <h2 id={`${id}-address`} className="font-serif text-[22px] font-semibold text-text-primary">
          Address
        </h2>
        {field("addressLine1", "House, street", { autoComplete: "address-line1" })}
        {field("addressLine2", "Area, landmark", { autoComplete: "address-line2" })}
        <div className="grid grid-cols-2 gap-3">
          {field("city", "City", { autoComplete: "address-level2" })}
          {field("state", "State", { autoComplete: "address-level1" })}
        </div>
        <div className="max-w-[12rem]">
          {field("pincode", "PIN code", { inputMode: "numeric", autoComplete: "postal-code" })}
        </div>
      </section>

      {forms.length > 0 && (
        <section className={card} aria-labelledby={`${id}-measurements`}>
          <div>
            <h2 id={`${id}-measurements`} className="font-serif text-[22px] font-semibold text-text-primary">
              Measurements
            </h2>
            <p className="mt-1 text-sm text-text-muted">
              Fill in what you know; we use these for made-to-measure pieces.
            </p>
          </div>
          {forms.map((form, index) => (
            <fieldset
              key={form.id}
              className={index > 0 ? "flex flex-col gap-3 border-t border-border-default pt-4" : "flex flex-col gap-3"}
            >
              <legend className="mb-1 text-base font-semibold text-text-primary">{form.name}</legend>
              <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
                {form.fields.map((f) => (
                  <div key={f.key}>
                    <label htmlFor={`${id}-${form.id}-${f.key}`} className={label}>
                      {f.label}
                      {f.unit ? ` (${f.unit})` : ""}
                    </label>
                    <input
                      id={`${id}-${form.id}-${f.key}`}
                      inputMode={f.unit ? "decimal" : "text"}
                      maxLength={20}
                      value={measurement(form.id, f.key)}
                      onChange={(e) => setMeasurement(form.id, f.key, e.target.value)}
                      className={input}
                    />
                  </div>
                ))}
              </div>
            </fieldset>
          ))}
        </section>
      )}

      <div className="flex items-center gap-3">
        <Button type="submit" variant="primary" disabled={state.status === "saving"}>
          {state.status === "saving" ? "Saving…" : "Save"}
        </Button>
        <p role="status" className="text-sm">
          {state.status === "saved" && <span className="text-feedback-success">Saved</span>}
          {state.status === "error" && <span className="text-feedback-error">{state.message}</span>}
        </p>
      </div>
    </form>
  );
}
