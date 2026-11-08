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
        <div>
          <label htmlFor={`${id}-phone`} className={label}>
            Mobile
          </label>
          <input
            id={`${id}-phone`}
            value={session.phone ? `+91 ${session.phone.slice(3)}` : "Not added"}
            disabled
            className={input}
          />
          <p className="mt-1.5 text-xs text-text-muted">
            {session.phone ? "Your login number, verified by OTP." : "Log in with your mobile number to add it."}
          </p>
        </div>
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
