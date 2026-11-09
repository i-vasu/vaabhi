# Stock → workshop → product

Date: 2026-11-09 · Branch: `feat/admin-polish` (builds on the uncommitted product-making work there)

## Goal

The owner buys a lot of fabric (e.g. 10 m of Kalamkari), cuts it into pieces (three 3 m pieces, each with a
different design) and each piece becomes its own storefront product. The admin should make that one flow:

1. Record the stock with **reference photos**.
2. **Make products** from it: one step creates a draft product per piece and takes the fabric out of stock.
3. Each piece appears on the **workshop board** (same board and stages as customer orders) and moves through
   embroidery / stitching / packing / finished, with who-did-it and cost logged on the way.
4. Each piece can carry **design reference photos**; when it is assigned (e.g. to a tailor for stitching) the
   owner **shares** the photos and product details on WhatsApp.
5. When finished, the owner adds real photos and a price and publishes; the piece leaves the board.

Traceability both ways: the stock page lists what it was made into; the product shows what it was cut from,
what each stage cost and the total making cost.

## Relationship map

```
 Supplier ──sells──▶ STOCK ITEM (10 m Kalamkari, ₹/m, GSM, width, up to 6 reference photos)
                      │
                      │ Make products  (one transaction)
          ┌───────────┼───────────┐
          ▼           ▼           ▼
      PRODUCT A   PRODUCT B   PRODUCT C     catalog: DRAFT, GSM / thread count copied from stock
          ▲           ▲           ▲
   product_material rows (3 m each)          inventory: each = an OUT stock movement (1 m stays)
          ▲           ▲           ▲
      PIECE A     PIECE B     PIECE C       tailoring: workshop_piece (stage, assignee, up to 6 design photos)
          │           │           │
          │  assigned ─▶ Share on WhatsApp (design photos + fabric photo + product details)
          │           │           │
   workshop board: first WORK stage → … → READY ("Finished")
          │  leaving a WORK stage (optional prompt) ─▶ product_work row (who, stage name, ₹)
          ▼
   Product published → piece no longer on the board
```

Cardinality: stock item 1—N product_material; product_material 1—1 product (one piece per product, though a
product may still get extra material rows by hand, e.g. lining); product 1—0..1 workshop_piece;
workshop_piece 1—N stage log rows; workshop_piece 1—0..6 design photos; product 1—N product_work.

## Decisions

- **Same board, same stages** as customer orders (`production_stage` from settings). Pieces start in the first
  `WORK` stage, end in the `READY` stage and never go to `DELIVERED`.
- **Staff stage limits** (`staff_stage`) apply to pieces exactly as to order items.
- **Work log fills itself**: leaving a `WORK` stage with a single-card move may carry an optional
  `{staffId | workerName, costRupees}`; it becomes a `product_work` row with `work_type` = the stage name and
  `done_on` = today. Prefilled with the current assignee. Bulk moves never prompt.
- **Finished ≠ published.** A piece in the `READY` stage shows "Add photos & publish" linking to the product
  editor. The board hides a piece once its product is `PUBLISHED` or `ARCHIVED`. Deleting the product deletes
  the piece (FK cascade).
- **Name of the action:** "Make products" (button), dialog title "Make products from this fabric", each row is
  a "piece".
- **Module placement:** pieces belong to `tailoring` (it owns the workshop). New dependencies:
  `tailoring → catalog` (create draft products) and `tailoring → inventory` (add material, add work).
  Neither catalog nor inventory depends on tailoring today, so no cycle and no events are needed.
- **Sharing goes through the device, not the server.** The admin builds the message and uses the Web Share API
  (`navigator.share` with files) so the phone's share sheet sends photos + text to WhatsApp, to any tailor
  including outside ones. Where file sharing is not supported (desktop browsers), it opens
  `https://wa.me/<assignee phone>?text=…` (or `https://wa.me/?text=…` with no phone) with the text and public
  photo links. MSG91 is not used: its setup here only sends approved text templates, without media, and costs
  per message. Photo links are public, unguessable `media.vaabhi.com` URLs, as product photos already are.
- **Permission:** making products lives under `/api/v1/admin/workshop/**` (WORKSHOP). Stock photos live under
  `/api/v1/admin/stock/**` (STOCK).

## Data (migration `V24__stock_photos_and_pieces.sql`)

```sql
CREATE TABLE stock_item_photo (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    stock_item_id BIGINT      NOT NULL REFERENCES stock_item (id) ON DELETE CASCADE,
    photo_key     VARCHAR(120) NOT NULL,
    position      INT         NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX stock_item_photo_item ON stock_item_photo (stock_item_id, position);

CREATE TABLE workshop_piece (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id        BIGINT      NOT NULL UNIQUE REFERENCES product (id) ON DELETE CASCADE,
    stock_item_id     BIGINT      NOT NULL REFERENCES stock_item (id),
    material_id       BIGINT      REFERENCES product_material (id) ON DELETE SET NULL,
    stage_id          BIGINT      NOT NULL REFERENCES production_stage (id),
    stage_since       TIMESTAMPTZ NOT NULL DEFAULT now(),
    assigned_staff_id BIGINT      REFERENCES staff_account (id),
    assigned_at       TIMESTAMPTZ,
    note              VARCHAR(200),
    created_by        VARCHAR(120),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX workshop_piece_stage ON workshop_piece (stage_id);

CREATE TABLE workshop_piece_stage_log (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    piece_id      BIGINT      NOT NULL REFERENCES workshop_piece (id) ON DELETE CASCADE,
    from_stage_id BIGINT      REFERENCES production_stage (id),
    to_stage_id   BIGINT      NOT NULL REFERENCES production_stage (id),
    staff_id      BIGINT      REFERENCES staff_account (id),
    actor         VARCHAR(120),
    moved_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX workshop_piece_stage_log_piece ON workshop_piece_stage_log (piece_id, moved_at);

CREATE TABLE workshop_piece_photo (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    piece_id   BIGINT       NOT NULL REFERENCES workshop_piece (id) ON DELETE CASCADE,
    photo_key  VARCHAR(120) NOT NULL,
    position   INT          NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX workshop_piece_photo_piece ON workshop_piece_photo (piece_id, position);
```

## API

### Stock photos (inventory, STOCK)

Same pattern as `TailoringImage`: browser resizes to a 400 px WebP and PUTs it to R2 with a presigned URL.

- `POST /api/v1/admin/stock/{id}/photos/upload` → `{key, uploadUrl, expiresAt}`; key = `stock/<uuid>`.
- `POST /api/v1/admin/stock/{id}/photos` `{key}` → `StockItem`. Rejects a key not issued under `stock/` or
  whose file is missing ("Upload the photo again"), and a 7th photo ("Up to 6 photos").
- `DELETE /api/v1/admin/stock/{id}/photos/{photoId}` → `StockItem`. The R2 object stays (cheap; avoids
  breaking a cached image).
- `StockItem` gains `photos: [{id, key}]` (ordered by position); the list endpoint includes them too.
- Storage not configured → 503 "Photo storage is not configured on this server".

### Make products (tailoring, WORKSHOP)

`POST /api/v1/admin/workshop/pieces`

```json
{ "stockItemId": 12,
  "pieces": [ { "name": "Kalamkari kurti – peacock", "categorySlug": "kurtis", "quantity": 3, "note": null } ] }
```

- 1–20 pieces; quantity 0.01–100000 (same rules as `ProductMaterialForm`).
- Sum of quantities must be ≤ the item's on-hand, else 400 "Only 7 m left".
- One transaction, for each piece:
  1. `AdminProductService.create(ProductForm)`: name, category, empty variants/add-ons/attributes, `gsm` and
     `threadCount` from the stock item, slug generated. Status stays `DRAFT`.
  2. `ProductMakingService.addMaterial(productId, {stockItemId, quantity, note}, actor)`.
  3. Insert `workshop_piece` in the first `WORK` stage (400 "Add a work stage in Settings first" if none).
- Returns `201` with `[{pieceId, productId, productName, productSlug}]`.
- Unknown category → the catalog's existing 400.

### Board (tailoring, WORKSHOP)

- `BoardColumn` gains `pieces: WorkPiece[]` next to `items` (order cards unchanged).
- `WorkPiece`: `pieceId, productId, productName, productSlug, productStatus, stockItemId, stockItemName,
  quantity, unit, photoKey (first stock photo, nullable), stage, stageSince, assignedTo (nullable, includes the
  staff phone), note, photos: [{id, key}] (design references), fabric {material, colour, widthIn, gsm,
  threadCount} and dimensions [{label, value, unit}] (the product's measurements)` — everything the share
  message needs, so sharing needs no extra call.
- Board shows pieces whose product is `DRAFT`. Pieces whose stage was removed go to the "removed" column like
  order items do. The staff filter and search (`q` matches product or stock name) apply to pieces too.
- Overview: stage loads and "unassigned" counts include pieces; overdue/due-today stay order-only.
- `PUT /api/v1/admin/workshop/pieces/{id}/assignee` `{staffId | null}`: same rules as order items (only in a
  `WORK` stage, staff must be allowed that stage).
- `POST /api/v1/admin/workshop/items/move`: `BulkMove` gains `pieceIds` (default empty) and optional
  `work {staffId?, workerName, costRupees}`. `work` is only accepted when exactly one piece and no items are
  moved and the piece is leaving a `WORK` stage. Moving a piece to a `DELIVERED` stage → 400 "Publish the
  product instead". Each piece move writes a stage-log row (staff = assignee at the time, actor = mover),
  resets `stage_since` and clears the assignee (same as order items).
- Design photos, same pattern as stock photos (key prefix `workshop/pieces/`, up to 6):
  `POST /api/v1/admin/workshop/pieces/{id}/photos/upload` → `{key, uploadUrl, expiresAt}`,
  `POST /api/v1/admin/workshop/pieces/{id}/photos` `{key}` → `WorkPiece`,
  `DELETE /api/v1/admin/workshop/pieces/{id}/photos/{photoId}` → `WorkPiece`.
- `PUT /api/v1/admin/workshop/pieces/{id}/note` `{note}` (≤ 200 chars) → `WorkPiece`, for design instructions
  that go in the message.
- `GET /api/v1/admin/products/{id}/making` (inventory) is unchanged; the product editor reads the piece's stage
  from a new `GET /api/v1/admin/workshop/pieces/by-product/{productId}` → `WorkPiece | 404`.

## Admin UI

- **Stock form / view** (`app/stock/page.tsx`, `app/stock/view/page.tsx`): photo strip (add, remove, tap to
  enlarge) reusing the resize + PUT helper from `lib/photo-upload.ts`. Thumbnail on the stock list row.
- **Stock view → "Make products"** dialog: rows of name · category (select from categories API) · quantity,
  "+ piece", live "left after: 1 m"; Save disabled while the sum is more than on hand or a row is incomplete. On
  success shows the created products with links and "Open workshop board".
- **Stock view "Made into"** (exists): adds each product's status and, for drafts, its board stage.
- **Workshop board** (`app/workshop/board/page.tsx`): piece cards with a "Stock" tag, fabric thumbnail,
  product name, stock name + quantity, assignee; drag / select / bulk move / assign like order cards. A single
  piece leaving a `WORK` stage opens the "Who did {stage}? Cost?" prompt (Skip / Save). Cards in the `READY`
  stage show "Add photos & publish" → product editor.
- **Piece sheet** (tap a piece card): design photos strip (add / remove / enlarge), note, fabric + measurements,
  current stage, assignee, and **Share on WhatsApp**. After assigning a piece, a toast offers "Share with
  {name} on WhatsApp".
- **Share** (`lib/piece-share.ts`, client-only so it works in the static export): builds the message

  ```
  🧵 Vaabhi – {stage}
  {product name}
  Fabric: {material}, {colour} · {quantity} {unit} · {width}" wide · {gsm} GSM
  Measurements: {label value unit} · …
  Note: {note}
  ```

  (lines with no data are left out). Fetches the design photos and the first stock photo from the media URL as
  files; if `navigator.canShare({files})` → `navigator.share({files, text})`; otherwise opens `wa.me` with the
  text plus one photo link per line. A failed photo fetch falls back to links for that photo; a cancelled share
  sheet does nothing. Fetching photos as files needs the media bucket to allow `GET` from the admin origin
  (CORS on R2 / `media.vaabhi.com`); check `infra/` Cloudflare config and add the admin origin if missing.
- **Product editor Making section** (`components/product-making.tsx`): source fabric thumbnail next to "Cut from
  {stock}, 3 m" and "In workshop: {stage}" when a piece exists.
- Regenerate the API client (`pnpm generate:api`) after the backend changes.

## Errors

| Case | Response |
| --- | --- |
| Pieces need more than on hand | 400 "Only {n} {unit} left" |
| No WORK stage configured | 400 "Add a work stage in Settings first" |
| Unknown category | 400 (catalog's existing message) |
| Photo key wrong / not uploaded | 400 "Upload the photo again" |
| 7th photo | 400 "Up to 6 photos" |
| Storage not configured | 503 |
| `work` on bulk move or not leaving a WORK stage | 400 |
| Piece moved to DELIVERED | 400 "Publish the product instead" |
| 7th design photo | 400 "Up to 6 photos" |

## Testing

Per CLAUDE.md: add cheap tests alongside (service test for make-products stock check, controller test for the
new endpoints, admin fixture + board render test) but do not run them. Run only `pnpm typecheck` and
`pnpm lint`. The owner checks by hand:

1. Add a 10 m stock item with two photos; both show on the view and list.
2. Make products: 3 × 3 m → 3 drafts in the catalogue, stock shows 1 m, "Made into" lists all three.
3. Try 4 × 3 m → "Only 10 m left"-style error, nothing created.
4. Board: three Stock cards in the first work stage; assign one; move it on → prompt; save ₹800 → product's
   Making section shows the work row and the cost includes it.
5. Open a piece, add two design photos and a note; assign it → "Share with {name}" → on the phone (installed
   admin app) WhatsApp opens with the photos and the message; on desktop wa.me opens with text + links.
6. Move to Finished → "Add photos & publish"; publish → card gone from the board.

## Out of scope

Due dates for pieces, stock photos as product photos, undoing a whole make in one step, a separate stage list for
pieces, piece-rate pay from the piece stage log, sending WhatsApp from the server (MSG91 media templates),
sharing order items (customer orders) the same way.
