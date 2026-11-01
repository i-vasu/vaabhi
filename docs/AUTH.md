# Customer sign-in

Customers sign in (or sign up, the same flow) on `/signup` with **Google** or an **Indian mobile number + OTP**.
They stay signed in for 60 days after their last visit; **Log out** ends the session on the server.

## How it works

```
browser ──/api/auth/*──► storefront (Next.js) ──/api/v1/auth/*──► backend ──► MSG91 (SMS) / Google keys
           httpOnly cookies              tokens in JSON              Postgres: customer, otp_challenge, refresh_token
```

- **OTP:** the backend generates the 6-digit code, stores only an HMAC of it, and checks it. MSG91 only delivers
  the SMS (`SmsSender` in `shared/sms`), so another provider means one new class, nothing else.
  Codes last 5 minutes, allow 5 wrong tries, can be resent after 30 s, at most 5 per number per hour and
  300 per hour in total (caps the SMS bill if the form is abused). Only Indian mobiles (+91, 6–9…) are accepted.
- **Google:** Google's button gives the page an ID token; the backend checks Google's signature, the issuer and
  that it was issued for our client id. A Google account with the same verified email as an existing customer
  signs into that account.
- **Sessions:** a 15-minute access token (JWT, role `CUSTOMER`) plus a 60-day refresh token. The storefront keeps
  both in httpOnly, SameSite=Lax cookies scoped to `/api/auth`, so page scripts never see them. Every page load
  calls `/api/auth/session`, which refreshes when needed: each refresh issues a new refresh token (valid another
  60 days) and retires the old one. A retired token reused after 30 s ends all of that customer's sessions
  (stolen-token protection). Logout revokes the token immediately.

## Local development

Nothing to set up: the `dev` profile uses the **log** SMS provider, so codes appear in the backend log:

```
SMS (not sent, dev log provider) to +919876543210 [otp]: {otp=042137}
```

Google sign-in needs `GOOGLE_CLIENT_ID` (below) in the backend environment and in `frontend/storefront/.env.local`,
with `http://localhost:3000` added as an authorised JavaScript origin. Without it the page shows phone sign-in only.

## Production setup

### Google

1. Google Cloud console → APIs & Services → **OAuth consent screen**: app name *Vaabhi*, support email, logo,
   `vaabhi.com` as authorised domain, privacy policy URL. Scopes: just `openid`, `email`, `profile`.
2. **Credentials → Create credentials → OAuth client ID → Web application.** Authorised JavaScript origins:
   `https://vaabhi.com` (and `http://localhost:3000` for development). No redirect URIs are needed.
3. Put the client id in `/opt/vaabhi/.env` as `GOOGLE_CLIENT_ID=…apps.googleusercontent.com`
   (used by both the backend and the storefront; it is not a secret).

### MSG91 (phone OTP)

1. Create an MSG91 account and complete **DLT registration** (entity / PE ID, a 6-character sender ID such as
   `VAABHI`, and a *Service Implicit* content template). Suggested template text:
   `{#var#} is your Vaabhi sign-in code. It is valid for 5 minutes. Do not share it with anyone. - Vaabhi`
2. In MSG91 → SMS → **Templates**, add that DLT template with the variable written as `##otp##`, attach the sender ID
   and DLT template id, and copy MSG91's **template id**.
3. MSG91 → **API** → copy the auth key.
4. Add to `/opt/vaabhi/.env`:

   ```
   MSG91_AUTH_KEY=…
   MSG91_OTP_TEMPLATE_ID=…
   ```

Until both are set, phone sign-in answers *"not available right now"* and Google sign-in still works. The `prod`
profile refuses the dev log provider, so codes can never end up in production logs.
