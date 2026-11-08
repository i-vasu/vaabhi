# Storefront audit: checked against the live site, and the plan

Checked on 2026-11-08 against https://vaabhi.com (release `v0.2.0 (043ab13)`) and the code on `feat/storefront`.
Source: the "Vaabhi Storefront Audit & Comprehensive Improvement Checklist" Google Doc (18 modules).

## Progress

- **Phase 0 — done** (`feat/storefront`): admin Storefront screen for banners and category photos; home page reads
  `GET /store/home`.
- **Phase 1 — done** (`feat/storefront`):
  - Website pages: `content_page`, V20. Admin → Website pages; served at `/policies/<slug>` and `/<slug>`; the footer
    lists the published ones.
  - `GET /store/shop` feeding `/contact`, the footer and the WhatsApp buttons.
  - `GET /store/products?q=` and `/search`. This page also covers occasions, e.g. `/search?occasion=wedding`.
  - Header and footer only link to pages that exist: Pre Order, Collections and Blog are gone, and Track order goes
    to `/account/orders`.
  - Branded 404, and the drawer title is now "Menu".
- **Phase 2 — done** (`feat/storefront`):
  - Page titles no longer repeat the brand ("Sarees · Vaabhi").
  - Filter panel headings are read once.
  - Card prices and occasion tiles read as separate words.
  - "Prices include all taxes" on listings (the product page already said so).
  - `sitemap.xml` and `robots.txt` built from the API, with absolute canonicals via `SITE_URL`.
  - Fuller homepage description.
  - Footer Shop column lists the categories from `GET /store/categories`; the menu says "Blog".
- **Phase 3 — built** (`feat/storefront`): guest order tracking (Phase 1 item 8).
  - `POST /api/v1/store/orders/track` (order number + phone). It returns status, dates, courier and items, but no
    address, email or phone. A wrong number and a wrong phone get the same 404. After 10 wrong tries an order
    number answers 429 for 15 minutes.
  - Live Shiprocket refresh: for a shipped order whose saved courier status is older than 30 minutes, the lookup
    asks Shiprocket first (at most once per order per 30 minutes). If Shiprocket fails, it shows the saved status.
  - `/track` page (`?order=WEB-1042` fills in the number); the footer's "Track order" goes there.

## What the live site actually shows

- **The catalogue is empty.** Every category says "0 styles". `/new` and `/bestsellers` show their empty states.
  `GET /api/v1/store/home` returns `{"hero":[],"occasions":[],"craft":null}`.
  The audit names four bestseller cards (Emerald Kanjeevaram etc.). Those come from the demo catalogue, so the audit
  was done on a local/demo build, not on live.
- **25 of the 37 linked pages are 404s**, and they show Next's default black-and-white 404 page:
  `/pre-order` `/collections` `/support` `/blog` `/search` `/edit/wedding` `/occasion` `/occasion/{wedding,festive,
  haldi-mehendi,everyday}` `/weavers` `/contact` `/track` `/size-guide` `/care-guide` `/faq` `/returns`
  `/policies/{shipping,cancellation,privacy,terms}` `/about` `/sitemap.xml`.
  These pages work: `/`, `/c/*`, `/new`, `/bestsellers`, `/bag`, `/signup`, `/account`.
- **The home page is hardcoded**, even though the backend already supports managing it:
  - The hero, occasion tiles and craft block are written in `app/page.tsx` and `lib/home-content.ts`.
  - The backend already has managed banners (`storefront` module: HERO / OCCASION / CRAFT slots,
    `GET /api/v1/store/home`, admin CRUD). But **no admin screen** uses them and the storefront doesn't read them.
- **"Join on WhatsApp" goes to `/contact` (a 404).** `NEXT_PUBLIC_WHATSAPP_NUMBER` isn't set in production.

## Audit items: confirmed, wrong, or already done

| # | Audit item | Live / code | Verdict |
|---|---|---|---|
| 1 | "Hey, how are you doing today?" heading | Title of the menu drawer (`site-menu.tsx:26`), an H2 that comes before the H1 in the DOM | **Confirmed**, 1-line fix |
| 1 | Search/bag icons lack aria-labels | They have them: "Search", "Shopping bag", "Account" | **Already done** |
| 1 | No bag badge / account / wishlist entry | Account menu exists; `bag-link.tsx` has a count | **Mostly done**; check the badge once there's stock |
| 1 | "Blogs" vs "Blog" | `site-links.ts` | **Confirmed**, trivial |
| 1 | Instant search with weave chips | `/search` is a 404, and there's no search endpoint | **Confirmed**, needs backend |
| 2 | Hero CTA `/edit/wedding` | 404 | **Confirmed**: fixed by reading the hero from banners (the CTA link comes from admin) |
| 3 | Empty categories | 0 products live | **Confirmed**: data, not code (add products in admin) |
| 3 | Duplicate `<title>` suffix | "Sarees \| Vaabhi · Vaabhi": 9 pages add "\| Vaabhi" and the layout template adds "· Vaabhi" | **Confirmed**, trivial |
| 3 | Repeated filter headings | Filter panel shows `<p>Price</p>` plus `<legend>Price</legend>` | **Confirmed**, small |
| 3 | Occasion/weave facets | The backend already filters by `occasion`, `weave`, `fabric`, `work` within a category; facets show only when products have those attributes | **Backend done**; check after adding products |
| 3 | GST shown | Not shown | **Confirmed**, copy |
| 4 | Craft copy lists 3 clusters, chips show 5 | Hardcoded | **Confirmed**: move to the CRAFT banner |
| 5 | WhatsApp CTA → /contact | 404 | **Confirmed** |
| 6 | Build version in footer | `sr-only` (invisible), and also in `<meta name="app-version">` | **Minor**: drop the sr-only span |
| 6 | Footer missing Salwar suits / Dupattas | Hardcoded in `site-links.ts` | **Confirmed**: build the list from the categories API |
| 6 | Legal pages, `/returns` vs `/policies/*` | All 404 | **Confirmed**: these are Critical |
| 12 | Breadcrumb slash | Already `aria-hidden` | **Already done** |
| 13 | Blouse stitching, pincode check | `delivery-check.tsx`, `stitchingDays` exist on the product page | **Partly done** |
| 15 | Phone / email / Instagram / address | `ShopProfile` stores phone, email and socials, but it isn't public | **Confirmed**: add a public endpoint |
| 16 | "Headings start at H4/H3" | Home has an H1; only the drawer's H2 comes first | **Mostly wrong**: fixed by item 1 |
| 16 | Sitemap | `/sitemap.xml` 404, and `robots.txt` is Cloudflare's | **Confirmed**: add `sitemap.ts` |
| 18 | Bag empty state, ₹1,999 free shipping | Works | **Done**; payments, stock and notifications need a hand smoke test |

## Plan, in happy-path order

### Phase 0: content you can manage (it blocks everything else)
1. **Admin: Storefront → Banners screen.** List, add, edit, reorder and schedule HERO / OCCASION / CRAFT banners,
   using the existing image upload. The backend endpoints already exist.
2. **Storefront home reads `GET /store/home`.** Hero slides (title, subtitle, CTA, image), occasion tiles, craft
   block. Then delete the hardcoded copy in `page.tsx` and `home-content.ts`. When there's no banner, show a plain
   branded hero without a fake CTA.
3. **Owner:** add real products (and mark some Bestseller), categories with images, and banners.

### Phase 1: no more 404s
4. **Content pages** (policies, size guide, care guide, FAQ, about, weavers, shipping, returns):
   - Add a small `content_page` table (slug, title, markdown body, published) to the `storefront` module, next
     migration number.
   - Add an admin editor.
   - Storefront: `GET /store/pages/{slug}`, rendered at `/policies/[slug]` and `/[page]`.
   - Footer and drawer show a link only when its page is published. No placeholder pages.
5. **Occasions:** add a cross-category listing endpoint (`/store/products?occasion=wedding`) and pages `/occasion`
   and `/occasion/[slug]`. The occasion tiles link to these.
6. **Contact:** public `GET /store/shop` (phone, email, WhatsApp, Instagram, address, hours from `ShopProfile`)
   feeding a `/contact` page, the footer and the WhatsApp CTA (`wa.me/<phone>`). This replaces the env var.
7. **Search:** `GET /store/search?q=` (name, weave, category) and a `/search` page with an honest
   "no results" state. Instant search with autocomplete comes later.
8. **Track order:** `/track` with order number plus phone, backed by a new public lookup endpoint. Signed-in users
   already have `/account/orders`.
9. **Header links with no backend** (Pre Order, Collections, Blog, Support): remove them until they exist
   (Support → Contact). Building a blog or pre-order flow is out of scope for now.
10. **Branded `not-found.tsx`** with links back to the shop.

### Phase 2: quick fixes (one small PR)
- Drawer title placeholder → "Menu".
- Remove the "| Vaabhi" from the 9 page titles.
- "Blogs" → "Blog".
- Remove the sr-only version from the footer.
- Remove the duplicate "Price" label in the filter panel.
- `/returns` → `/policies/returns`.
- Footer Shop column built from the categories API.
- "Prices include GST" on the product page and listings.
- `app/sitemap.ts` (categories, products, pages) and `app/robots.ts`.

### Later (not happy path)
- Reviews and ratings.
- Quick view and "add to bag" on product cards.
- Instant search with chips.
- Silk Mark / zari / GSM specs and drape videos.
- COD order cap.
- International shipping.
- GA4 and Meta Pixel.
- Instagram lookbooks.
- Pre-order lead times.

## Owner checks after each phase
- **Phase 0:** add a hero banner in admin → it shows on vaabhi.com within 60 s, and its CTA opens a working page.
  Mark a product Bestseller → it appears on home and `/bestsellers`.
- **Phase 1:** click every header, drawer and footer link → none 404. Publish and unpublish a policy page → its
  footer link appears and disappears. The WhatsApp button opens WhatsApp with the shop number.
- **Phase 2:** browser tab titles read "Sarees · Vaabhi". `/sitemap.xml` lists the live products.
- **Phase 3:** footer → Track order → enter a real order number and its phone → status and items show. A wrong
  phone says "couldn't find". `WEB-` can be left off.
