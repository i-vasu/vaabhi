# Instagram and WhatsApp catalogue

Publishing a product in the admin puts it on the website straight away. Instagram Shopping and the WhatsApp
Business catalogue both read from **one Meta catalogue**, which fetches the shop's product feed on a schedule:

    https://api.vaabhi.com/api/v1/store/catalog/meta-feed.csv

- One row per variant (colour × size), grouped by product. Price, sale price (when the MRP is higher), stock,
  colour, size, link to the product page and photos.
- Only published products are in it. Archive or unpublish a product and it drops out on the next fetch.
- Meta needs JPEG photos; the shop stores WebP. The backend makes a JPEG copy (`<photo>/feed.jpg`) of each photo of a
  published product every 5 minutes, and a product joins the feed once it has at least one.
- Brand is the shop name from Settings → Shop profile.

## One-time setup in Meta (owner)

1. **Commerce Manager** (business.facebook.com/commerce) → **Add catalogue** → *E-commerce* → *Upload product info*.
2. In the catalogue: **Data sources** → **Add items** → **Data feed** → *Scheduled feed* → paste the URL above →
   repeat **Hourly** → currency INR.
3. **Instagram**: Commerce Manager → *Shops* (or Instagram app → Settings → Business tools → Set up Instagram
   Shopping) → link the catalogue to the Instagram account. Meta reviews the account the first time (a few days).
4. **WhatsApp**: WhatsApp Manager → your number → **Catalogue** → connect the same catalogue. Customers then see
   the catalogue on the business profile and in chats.

After that nothing else is needed: new products appear within about an hour of publishing (the next scheduled
fetch, once the JPEG copies are made). For an immediate update use **Data sources → feed → Fetch now**.
