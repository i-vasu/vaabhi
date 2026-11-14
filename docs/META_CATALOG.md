# Instagram and WhatsApp catalogue

Publishing a product in the admin puts it on the website straight away. Instagram Shopping and the WhatsApp
Business catalogue both read from **one Meta catalogue**. The backend keeps it up to date two ways:

- **Instantly, through the Catalog API** (when `META_CATALOG_ID` and `META_CATALOG_TOKEN` are set): right after a
  product is published, edited, unpublished or archived, or its photos change, and every minute for anything else
  (e.g. stock sold online or in the shop). Only changed items are sent; items no longer for sale are deleted.
  What was last sent is kept in `meta_catalog_item`.
- **As a backup, the scheduled feed** Meta fetches itself:

    https://api.vaabhi.com/api/v1/store/catalog/meta-feed.csv

- One row per variant (colour × size), grouped by product. Price, sale price (when the MRP is higher), stock,
  colour, size, link to the product page and photos.
- Only published products are in it. Archive or unpublish a product and it drops out on the next fetch.
- Meta needs JPEG photos; the shop stores WebP. The backend makes a JPEG copy (`<photo>/feed.jpg`) of each photo of a
  published product (within a minute of upload), and a product is only sent to Meta once it has at least one.
- Brand is the shop name from Settings → Shop profile.

## One-time setup in Meta (owner)

1. **Commerce Manager** (business.facebook.com/commerce) → **Add catalogue** → *E-commerce* → *Upload product info*.
2. In the catalogue: **Data sources** → **Add items** → **Data feed** → *Scheduled feed* → paste the URL above →
   repeat **Hourly** → currency INR.
3. **Instagram**: Commerce Manager → *Shops* (or Instagram app → Settings → Business tools → Set up Instagram
   Shopping) → link the catalogue to the Instagram account. Meta reviews the account the first time (a few days).
4. **WhatsApp**: WhatsApp Manager → your number → **Catalogue** → connect the same catalogue. Customers then see
   the catalogue on the business profile and in chats.

5. **Instant updates**: Business settings (business.facebook.com/settings) → **Users → System users** → add an admin
   system user → **Assign assets** → the catalogue with *Manage catalogue* → **Generate token** for your Meta app
   with the `catalog_management` and `business_management` permissions (never expires). Copy the catalogue id
   (Commerce Manager → catalogue → Settings). Add both as GitHub secrets `META_CATALOG_ID` and
   `META_CATALOG_TOKEN` (repository → Settings → Secrets → Actions) and redeploy.

With step 5 done, a published product reaches Instagram and WhatsApp within a minute or two (Meta's own
processing; per-item problems show under Commerce Manager → Catalogue → Issues). Without it, changes appear at the
next scheduled feed fetch (hourly), or use **Data sources → feed → Fetch now**.
