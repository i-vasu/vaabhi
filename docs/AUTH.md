# Customer sign-in

Customers sign in (or sign up, the same flow) on `/signup` with **Google** or an **Indian mobile number + OTP**.
They stay signed in for 60 days after their last visit; **Log out** ends the session on the server.

## How it works

```
browser ──/api/auth/*──► storefront (Next.js) ──/api/v1/auth/*──► backend ──► MSG91 (WhatsApp) / Google keys
           httpOnly cookies              tokens in JSON              Postgres: customer, otp_challenge, refresh_token
```

- **OTP:** the backend generates the 6-digit code, stores only an HMAC of it, and checks it. MSG91 only delivers
  it on WhatsApp (`WhatsAppSender` in `shared/whatsapp`; there is no SMS), so another provider means one new class.
  Phone sign-in is offered only while the WhatsApp sign-in template is set up.
  Codes last 5 minutes, allow 5 wrong tries, can be resent after 30 s, at most 5 per number per hour and
  300 per hour in total (caps the WhatsApp bill if the form is abused). Only Indian mobiles (+91, 6–9…) are accepted.
- **Google:** Google's button gives the page an ID token; the backend checks Google's signature, the issuer and
  that it was issued for our client id. A Google account with the same verified email as an existing customer
  signs into that account.
- **Sessions:** a 15-minute access token (JWT, role `CUSTOMER`) plus a 60-day refresh token. The storefront keeps
  both in httpOnly, SameSite=Lax cookies scoped to `/api/auth`, so page scripts never see them. Every page load
  calls `/api/auth/session`, which refreshes when needed: each refresh issues a new refresh token (valid another
  60 days) and retires the old one. A retired token reused after 30 s ends all of that customer's sessions
  (stolen-token protection). Logout revokes the token immediately.

## Settings

| Setting                 | Where                   | Dev (local)                           | Prod (VPS)                              |
| ----------------------- | ----------------------- | ------------------------------------- | --------------------------------------- |
| `GOOGLE_CLIENT_ID`      | backend **and** storefront | dev OAuth client (optional)        | prod OAuth client                       |
| `WHATSAPP_PROVIDER`       | backend                 | `log` (default)                       | `msg91` (default; `log` is refused)     |
| `MSG91_AUTH_KEY`          | backend                 | only to send real WhatsApp from your laptop | required for phone sign-in        |
| `WHATSAPP_LOGIN_TEMPLATE` | backend                 | `vaabhi_login` (dev default)          | required for phone sign-in              |

Each one is optional: without Google the page shows phone sign-in only; without MSG91 in prod, phone sign-in answers
*"not available right now"* (HTTP 503) and Google still works.

## 1. Google (one-time, ~15 minutes)

Use **two OAuth clients in one Google Cloud project**, so the production client never trusts `localhost`.

1. [console.cloud.google.com](https://console.cloud.google.com) → create project **vaabhi** (or reuse one).
2. **Google Auth Platform → Branding** (older consoles: *APIs & Services → OAuth consent screen*):
   app name *Vaabhi*, user support email, developer contact email, authorised domain `vaabhi.com`,
   home page `https://vaabhi.com`, privacy policy and terms URLs. Skip the logo for now: adding one triggers
   Google's brand verification (days), while the basic scopes need none.
3. **Audience:** user type **External**. Then **Publish app** (status *In production*). While it stays in
   *Testing*, only the test users listed there can sign in.
4. **Data access:** only `openid`, `.../auth/userinfo.email`, `.../auth/userinfo.profile`.
5. **Clients → Create client → Web application**, twice:

   | Client name         | Authorised JavaScript origins                        |
   | ------------------- | ---------------------------------------------------- |
   | `Vaabhi web (dev)`  | `http://localhost` and `http://localhost:3000`       |
   | `Vaabhi web (prod)` | `https://vaabhi.com` and `https://www.vaabhi.com`    |

   No redirect URIs (the button returns an ID token to the page). Copy each **Client ID**
   (`….apps.googleusercontent.com`); the client secret is not used. New origins can take a few minutes to work.

## 2. MSG91 WhatsApp (one-time; Meta approves templates in minutes to a day)

Codes go out on WhatsApp only (no SMS, so no DLT registration).

1. [msg91.com](https://msg91.com) → sign up → complete KYC → **WhatsApp**: connect the shop's WhatsApp Business number
   (Meta Business verification). Its number with country code and no plus is `WHATSAPP_NUMBER`.
2. **WhatsApp → Templates → Create**: category **Authentication**, name e.g. `vaabhi_login`, language English, with
   the **Copy code** button. Meta writes the text (*"{{1}} is your verification code…"*). Once it is approved, its
   name → `WHATSAPP_LOGIN_TEMPLATE`.
3. **Profile → Authkey → Create** (whitelist the VPS IP if you turn on IP security) → `MSG91_AUTH_KEY`.
4. Add WhatsApp credit. Sign-in is capped at 300 codes an hour, so a wallet alert at ~₹500 is plenty.

## 3. Dev (your computer)

**Phone sign-in needs nothing:** the `dev` profile uses the **log** WhatsApp provider, so codes appear in the backend
log:

```
WhatsApp (not sent, dev log provider) to +919876543210 [otp = vaabhi_login]: {otp=042137}
```

**Google sign-in:** give the *dev* client id to both apps.

- Storefront: `frontend/storefront/.env.local` (git-ignored) → `GOOGLE_CLIENT_ID=….apps.googleusercontent.com`
- Backend: an environment variable (Spring does not read `.env` files). In IntelliJ: *Run → Edit Configurations →
  VaabhiApplication → Environment variables*. From PowerShell:

  ```powershell
  $env:GOOGLE_CLIENT_ID = "….apps.googleusercontent.com"; ./mvnw spring-boot:run
  ```

Open `http://localhost:3000/signup` (not `127.0.0.1`, which is not an authorised origin).

**Real WhatsApp from your laptop** (e.g. to try the template before going live): also set
`WHATSAPP_PROVIDER=msg91`, `MSG91_AUTH_KEY` and `WHATSAPP_LOGIN_TEMPLATE` on the backend. Each code costs a message.

## 4. Prod (VPS)

Store the values as **GitHub secrets** in the `production` environment; every deploy writes them into
`/opt/vaabhi/.env` (only those lines; like the R2 keys). From the repository root:

```bash
gh secret set GOOGLE_CLIENT_ID        --env production   # the *prod* client id
gh secret set MSG91_AUTH_KEY          --env production
gh secret set WHATSAPP_LOGIN_TEMPLATE --env production   # e.g. vaabhi_login, once Meta approved it
```

Apply them by redeploying the running build (or merge anything to `main`):

```bash
gh workflow run deploy.yml -f image_tag=$(ssh -p 20036 deploy@148.113.17.68 cat /opt/vaabhi/.current-tag)
```

Or, with SSH access, put the three lines in `/opt/vaabhi/.env` yourself and run
`docker compose -f docker-compose.prod.yml --env-file .env up -d`. The storefront reads `GOOGLE_CLIENT_ID` at
request time, so no rebuild is needed. Rotating a key is the same: update the secret, redeploy.

**Check it works:**

```bash
# 200 {"phone":"+91…","expiresInSeconds":300,"resendAfterSeconds":30} and a WhatsApp arrives; 503 means it is not configured yet
curl -s -X POST https://api.vaabhi.com/api/v1/auth/otp/request \
  -H 'Content-Type: application/json' -d '{"phone":"<your mobile>"}'
```

Then on `https://vaabhi.com/signup` sign in with the code and with Google. If MSG91 refuses a message, the backend
logs `MSG91 WhatsApp refused [otp]: HTTP … <reason>` (usually a template that is not approved yet or a
wrong name or language).

The `prod` profile refuses the dev log provider, so codes can never end up in production logs.
