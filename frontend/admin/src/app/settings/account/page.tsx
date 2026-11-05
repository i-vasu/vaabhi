"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { appVersion } from "@vaabhi/observability";
import { LockIcon, LogoutIcon } from "@/components/icons";
import { ListCard, ListRow, PageHeader, SectionLabel, TextField, buttonPrimary } from "@/components/ui";
import { api } from "@/lib/api";
import { clearSession, getStaff } from "@/lib/auth";
import { describeError } from "@/lib/settings";

export default function AccountPage() {
  const router = useRouter();
  const staff = getStaff();
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [repeat, setRepeat] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);

  async function changePassword() {
    if (next !== repeat) {
      setMessage({ ok: false, text: "The new passwords do not match." });
      return;
    }
    setBusy(true);
    setMessage(null);
    try {
      await api.admin.changePassword({ currentPassword: current, newPassword: next });
      setCurrent("");
      setNext("");
      setRepeat("");
      setMessage({ ok: true, text: "Password changed." });
    } catch (e) {
      const { message: text, fields } = describeError(e);
      setMessage({ ok: false, text: fields[0] ? `New password ${fields[0].message}.` : text });
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="mx-auto max-w-3xl">
      <PageHeader title="App & account" backHref="/settings/" />

      <SectionLabel>Signed in as</SectionLabel>
      <ListCard>
        <ListRow title={staff?.name ?? "—"} subtitle={staff?.email} />
      </ListCard>

      <SectionLabel>Change password</SectionLabel>
      <form
        className="grid gap-4 rounded-2xl border border-line bg-panel p-5"
        onSubmit={(event) => {
          event.preventDefault();
          void changePassword();
        }}
      >
        <TextField
          label="Current password"
          type="password"
          autoComplete="current-password"
          required
          value={current}
          onChange={(event) => setCurrent(event.target.value)}
        />
        <TextField
          label="New password"
          type="password"
          autoComplete="new-password"
          required
          minLength={10}
          hint="At least 10 characters"
          value={next}
          onChange={(event) => setNext(event.target.value)}
        />
        <TextField
          label="Repeat new password"
          type="password"
          autoComplete="new-password"
          required
          value={repeat}
          onChange={(event) => setRepeat(event.target.value)}
        />
        {message && (
          <p role={message.ok ? "status" : "alert"} className={message.ok ? "text-ok" : "text-bad"}>
            {message.text}
          </p>
        )}
        <button type="submit" className={buttonPrimary} disabled={busy}>
          <LockIcon size={18} /> {busy ? "Changing…" : "Change password"}
        </button>
      </form>

      <SectionLabel>About</SectionLabel>
      <ListCard>
        <ListRow title="App version" trailing={appVersion().label} />
      </ListCard>

      <button
        type="button"
        onClick={() => {
          clearSession();
          router.replace("/login/");
        }}
        className="mt-6 flex w-full items-center justify-center gap-2 rounded-xl border border-line-strong py-3 text-bad hover:bg-bad-soft"
      >
        <LogoutIcon /> Sign out
      </button>
    </section>
  );
}
