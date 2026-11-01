"use client";

import type { CustomerProfile } from "@vaabhi/api-client";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useId, useState } from "react";
import { Button } from "@vaabhi/ui";
import { AuthError, auth, sessionStore, toSession } from "@/lib/session";
import { GoogleButton } from "./google-button";

type Step =
  | { kind: "phone" }
  | { kind: "code"; phone: string }
  | { kind: "name" };

type Problem = { field?: "phone" | "code" | "name"; message: string };

const input =
  "w-full rounded-sm border border-border-strong bg-bg-surface px-3 py-3 text-base outline-none focus:border-brand-primary";

function problemOf(error: unknown, field: Problem["field"]): Problem {
  if (error instanceof AuthError) {
    return { field: error.forField(field ?? "") ? field : undefined, message: error.forField(field ?? "") ?? error.message };
  }
  return { message: "Something went wrong. Please try again." };
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

/**
 * Sign in or sign up in one flow: Google, or mobile number + 6-digit code. New phone customers are asked for a
 * name (optional). Afterwards the customer goes back to where they came from.
 */
export function SignInForm({ googleClientId, next }: Readonly<{ googleClientId: string | null; next: string }>) {
  const router = useRouter();
  const ids = { phone: useId(), code: useId(), name: useId() };
  const [step, setStep] = useState<Step>({ kind: "phone" });
  const [phone, setPhone] = useState("");
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [problem, setProblem] = useState<Problem | null>(null);
  const [busy, setBusy] = useState(false);
  const [resendIn, startResendTimer] = useCountdown();

  const done = useCallback(() => router.replace(next), [router, next]);

  const signedIn = useCallback(
    (customer: CustomerProfile, newCustomer: boolean) => {
      sessionStore.write(toSession(customer));
      if (newCustomer && !customer.name) {
        setProblem(null);
        setStep({ kind: "name" });
      } else {
        done();
      }
    },
    [done],
  );

  async function run(action: () => Promise<void>, field: Problem["field"]) {
    setBusy(true);
    setProblem(null);
    try {
      await action();
    } catch (error) {
      setProblem(problemOf(error, field));
      if (error instanceof AuthError && error.retryAfterSeconds) startResendTimer(error.retryAfterSeconds);
    } finally {
      setBusy(false);
    }
  }

  const sendCode = () =>
    run(async () => {
      const sent = await auth.requestOtp(step.kind === "code" ? step.phone : phone);
      setCode("");
      setStep({ kind: "code", phone: sent.phone });
      startResendTimer(sent.resendAfterSeconds);
    }, "phone");

  const onGoogle = useCallback(
    (credential: string) => {
      void run(async () => {
        const result = await auth.google(credential);
        signedIn(result.customer, result.newCustomer);
      }, undefined);
    },
    [signedIn],
  );
  const onGoogleError = useCallback((message: string) => setProblem({ message }), []);

  const message = (field?: Problem["field"]) =>
    problem && problem.field === field ? (
      <p role="alert" className="mt-1.5 text-sm text-feedback-error">
        {problem.message}
      </p>
    ) : null;

  if (step.kind === "name") {
    return (
      <form
        className="space-y-4"
        onSubmit={(e) => {
          e.preventDefault();
          if (!name.trim()) {
            setProblem({ field: "name", message: "Enter your name, or skip for now." });
            return;
          }
          void run(async () => {
            const { customer } = await auth.updateName(name.trim());
            sessionStore.write(toSession(customer));
            done();
          }, "name");
        }}
      >
        <div>
          <label htmlFor={ids.name} className="mb-1.5 block text-sm font-medium">
            What should we call you?
          </label>
          <input
            id={ids.name}
            autoComplete="name"
            maxLength={80}
            value={name}
            onChange={(e) => setName(e.target.value)}
            className={input}
          />
          {message("name")}
        </div>
        {message()}
        <Button type="submit" disabled={busy} className="w-full">
          Save and continue
        </Button>
        <button type="button" onClick={done} className="w-full py-2 text-sm text-text-secondary underline">
          Skip for now
        </button>
      </form>
    );
  }

  return (
    <div className="space-y-6">
      {googleClientId && step.kind === "phone" && (
        <>
          <GoogleButton clientId={googleClientId} onCredential={onGoogle} onError={onGoogleError} />
          <div className="flex items-center gap-3 text-xs uppercase tracking-[0.08em] text-text-muted">
            <span className="h-px flex-1 bg-border-default" />
            or
            <span className="h-px flex-1 bg-border-default" />
          </div>
        </>
      )}

      {step.kind === "phone" ? (
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            if (phone.replaceAll(/\D/g, "").length < 10) {
              setProblem({ field: "phone", message: "Enter a valid 10-digit Indian mobile number" });
              return;
            }
            void sendCode();
          }}
        >
          <div>
            <label htmlFor={ids.phone} className="mb-1.5 block text-sm font-medium">
              Mobile number
            </label>
            <div className="flex items-stretch rounded-sm border border-border-strong bg-bg-surface focus-within:border-brand-primary">
              <span className="flex items-center border-r border-border-default px-3 text-text-secondary">+91</span>
              <input
                id={ids.phone}
                type="tel"
                inputMode="numeric"
                autoComplete="tel-national"
                maxLength={14}
                placeholder="98765 43210"
                value={phone}
                onChange={(e) => setPhone(e.target.value.replaceAll(/[^\d ]/g, ""))}
                className="min-w-0 flex-1 bg-transparent px-3 py-3 text-base tabular-nums outline-none"
              />
            </div>
            {message("phone")}
          </div>
          {message()}
          <Button type="submit" disabled={busy} className="w-full">
            {busy ? "Sending…" : "Send OTP"}
          </Button>
        </form>
      ) : (
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            if (!/^\d{6}$/.test(code)) {
              setProblem({ field: "code", message: "Enter the 6-digit code" });
              return;
            }
            void run(async () => {
              const result = await auth.verifyOtp(step.phone, code);
              signedIn(result.customer, result.newCustomer);
            }, "code");
          }}
        >
          <p className="text-sm text-text-secondary">
            We sent a 6-digit code to <span className="font-medium text-text-primary">{pretty(step.phone)}</span>.{" "}
            <button
              type="button"
              className="text-brand-primary underline"
              onClick={() => {
                setProblem(null);
                setStep({ kind: "phone" });
              }}
            >
              Change
            </button>
          </p>
          <div>
            <label htmlFor={ids.code} className="mb-1.5 block text-sm font-medium">
              Enter OTP
            </label>
            <input
              id={ids.code}
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={6}
              value={code}
              onChange={(e) => setCode(e.target.value.replaceAll(/\D/g, ""))}
              className={`${input} text-center text-xl tracking-[0.5em] tabular-nums`}
            />
            {message("code")}
          </div>
          {message()}
          {message("phone")}
          <Button type="submit" disabled={busy} className="w-full">
            {busy ? "Checking…" : "Verify and continue"}
          </Button>
          <p className="text-center text-sm text-text-secondary">
            {resendIn > 0 ? (
              `Resend code in ${resendIn}s`
            ) : (
              <button type="button" disabled={busy} onClick={() => void sendCode()} className="text-brand-primary underline">
                Resend code
              </button>
            )}
          </p>
        </form>
      )}
    </div>
  );
}
