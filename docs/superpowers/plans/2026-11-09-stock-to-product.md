# Stock → Workshop → Product Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn a stock lot into draft products in one step ("Make products"), move each piece through the existing
workshop board with design photos and WhatsApp sharing, and log who did each stage and what it cost.

**Architecture:** Stock photos live in `inventory`. Pieces live in `tailoring` (it owns the workshop), which now
calls `catalog.AdminProductService` to create draft products and `inventory.ProductMakingService` to take fabric
out of stock and log work, so no module cycle. The board returns pieces next to order items in each column; the
admin builds the share message client-side and uses the Web Share API, falling back to `wa.me`.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Modulith, JdbcClient, Flyway, Postgres, R2 (`ObjectStorage`);
Next.js 16 static export, React 19, Tailwind 4, `@vaabhi/api-client`.

**Spec:** `docs/superpowers/specs/2026-11-09-stock-to-product-design.md`

## Global Constraints

- **Do not run tests** (CLAUDE.md). Tests are written alongside where cheap, never run. Verification = backend
  compile in IntelliJ (Build Project via IntelliJ MCP), `pnpm typecheck` and `pnpm lint` from `frontend/`.
- **No commits** until the owner asks (memory + CLAUDE.md). Every task ends with a check, not a commit.
- Next migration number is **V24**: `V24__stock_photos_and_pieces.sql`. No other migration.
- Don't hand-edit `packages/api-client/src/schema.d.ts` / `openapi.json`; regenerate with `pnpm generate:api`
  against the running backend (keep openapi.json number types, per memory).
- Action name: button **"Make products"**, dialog title **"Make products from this fabric"**, rows are **pieces**.
- Photos: 400 px WebP at `<key>/400.webp`; **up to 6** per stock item and per piece; error "Up to 6 photos".
- Exact error copy: "Only {n} {unit} left", "Add a work stage in Settings first", "Upload the photo again",
  "Publish the product instead", "Photo storage is not configured on this server" (503).
- Admin is a static export: no server code in admin pages; sharing is client-only.
- No hardcoded demo data; empty states are honest.

## Review Focus

- **Stock item with non-metre unit (PCS, ROLL)** — "Make products" must still work and messages say "Only 2 PCS
  left", using `StockItem.unit` the same way `InventoryService.move` does. Test in Task 3.
- **Piece whose stage was removed in Settings** — it must still show (in the removed column) and still be
  movable, same as order items. Test in Task 3.
- **Board staff filter / search** — `staffId` and `q` must filter pieces too, or a tailor's filtered view shows
  everyone's pieces. Test in Task 3.
- **Product deleted or published while its card is open** — move/assign on a piece whose product is no longer
  `DRAFT` returns 404 "That piece is no longer on the board", not a 500. Test in Task 4.
- **Share on a phone where a photo fetch fails (CORS not set yet, offline)** — the share still happens with text
  and that photo as a link; never an unhandled rejection. Test in Task 9.

---

### Task 1: Migration V24

**Files:**
- Create: `backend/src/main/resources/db/migration/V24__stock_photos_and_pieces.sql`

**Interfaces:**
- Produces: tables `stock_item_photo`, `workshop_piece`, `workshop_piece_stage_log`, `workshop_piece_photo`
  exactly as in the spec's "Data" section (copy the SQL verbatim, with a one-line comment above each table in the
  style of `V23__product_making.sql`).

- [ ] **Step 1:** Write the migration from the spec's SQL.
- [ ] **Step 2:** Check: file name is `V24__…`, no `V25`, FK targets exist (`stock_item`, `product`,
  `product_material`, `production_stage`, `staff_account`).

### Task 2: Stock photos (backend, inventory)

**Files:**
- Create: `backend/src/main/java/com/vaabhi/inventory/StockPhotos.java` (service + records)
- Modify: `inventory/StockItem.java` (add `List<StockPhoto> photos` as last component)
- Modify: `inventory/InventoryService.java` (`list`/`get` fill `photos`; `save` returns with photos)
- Modify: `inventory/InventoryController.java` (3 endpoints)
- Test: `backend/src/test/java/com/vaabhi/inventory/StockPhotosTest.java` (unit, mocked `ObjectStorage`)

**Interfaces:**
- Consumes: `com.vaabhi.shared.storage.ObjectStorage` (`configured()`, `presignPut(String, Duration)`,
  `exists(String)`), pattern of `tailoring/TailoringImage.java`.
- Produces:
  - `record StockItem.StockPhoto(long id, String key)`
  - `record StockPhotos.Upload(String key, String uploadUrl, Instant expiresAt)`
  - `record StockPhotos.PhotoForm(@NotBlank @Size(max = 120) String key)`
  - `StockPhotos.startUpload(long itemId): Upload` — key `stock/<uuid>`; 503 when not configured.
  - `StockPhotos.add(long itemId, PhotoForm form): StockItem` — key must match `^stock/[0-9a-f-]{36}$` and
    `<key>/400.webp` exist, else 400 "Upload the photo again"; 7th → 400 "Up to 6 photos"; position = max+1.
  - `StockPhotos.remove(long itemId, long photoId): StockItem` — 404 if not that item's photo.
  - `public List<StockItem.StockPhoto> InventoryService.photos(long itemId)` (used by tailoring for share).
  - Endpoints: `POST /api/v1/admin/stock/{id}/photos/upload` (`startStockPhotoUpload`),
    `POST /api/v1/admin/stock/{id}/photos` (`addStockPhoto`, 201), `DELETE /api/v1/admin/stock/{id}/photos/{photoId}`
    (`removeStockPhoto`). Method names become the OpenAPI operation ids.

- [ ] **Step 1: Write tests** — `rejectsKeyNotIssuedForStock` (key `tailoring/cloth/<uuid>` → 400 "Upload the
  photo again"), `rejectsMissingFile` (`exists` false → 400), `rejectsSeventhPhoto` (6 rows → 400 "Up to 6
  photos"), `startUploadNeedsStorage` (`configured` false → 503).
- [ ] **Step 2: Implement** `StockPhotos` and wire the endpoints; list query loads photos for all returned items
  in one `WHERE stock_item_id = ANY(:ids)` query (no N+1).
- [ ] **Step 3: Check** — IntelliJ Build Project compiles; fix callers of the `StockItem` constructor.

### Task 3: Pieces — make products and the board (backend, tailoring)

**Files:**
- Create: `tailoring/WorkPiece.java`, `tailoring/MakePieces.java` (form records), `tailoring/PieceService.java`
- Modify: `tailoring/BoardColumn.java` (add `List<WorkPiece> pieces`), `tailoring/WorkshopService.java`
  (`board`, `overview` include pieces), `tailoring/WorkshopController.java` (new endpoints)
- Modify: `inventory/ProductMakingService.java` only if `addMaterial` must return the new material id (see below)
- Test: `backend/src/test/java/com/vaabhi/tailoring/PiecesIT.java` (Testcontainers, pattern of `WorkshopIT`)

**Interfaces:**
- Consumes: `catalog.AdminProductService.create(ProductForm): AdminProduct` (`id()`, `name()`, `slug()`);
  `inventory.InventoryService.get(long): StockItem`, `InventoryService.photos(long)` (Task 2);
  `inventory.ProductMakingService.addMaterial(long productId, ProductMaterialForm, String actor)`;
  `settings.ShopListService.stages(): List<ProductionStage>`.
- Produces:
  - `record MakePieces(@Positive long stockItemId, @NotNull @Size(min = 1, max = 20) List<@Valid PieceForm> pieces)`
  - `record MakePieces.PieceForm(@NotBlank @Size(max = 160) String name, @NotBlank String categorySlug,
    @NotNull @DecimalMin("0.01") @DecimalMax("100000") @Digits(integer = 6, fraction = 2) BigDecimal quantity,
    @Nullable @Size(max = 200) String note)`
  - `record MakePieces.Made(long pieceId, long productId, String productName, String productSlug)`
  - `record WorkPiece(long pieceId, long productId, String productName, String productSlug, String productStatus,
    long stockItemId, String stockItemName, BigDecimal quantity, StockItem.Unit unit, @Nullable String photoKey,
    ProductionStage stage, OffsetDateTime stageSince, @Nullable Assignee assignedTo,
    @Nullable String assignedPhone, @Nullable String note, List<Photo> photos, Fabric fabric,
    List<Dimension> dimensions)` with nested `Photo(long id, String key)`,
    `Fabric(@Nullable String material, @Nullable String colour, @Nullable BigDecimal widthIn,
    @Nullable Integer gsm, @Nullable Integer threadCount)`, `Dimension(String label, BigDecimal value, String unit)`.
  - `PieceService.make(MakePieces form, @Nullable String actor): List<Made>` — `@Transactional`.
  - `PieceService.openPieces(@Nullable Long staffId, @Nullable String query): List<WorkPiece>` — product status
    `DRAFT` only; `q` matches product name or stock name (`ClientService.like`); ordered by `created_at, id`.
  - `PieceService.get(long pieceId): WorkPiece`, `PieceService.byProduct(long productId): WorkPiece` (404).
  - Endpoints: `POST /api/v1/admin/workshop/pieces` (`makePieces`, 201),
    `GET /api/v1/admin/workshop/pieces/by-product/{productId}` (`getPieceByProduct`).

- [ ] **Step 1: Write tests** in `PiecesIT`:
  - `makesDraftProductsAndTakesFabric` — 10 M item, 3 × 3 → 3 products `DRAFT` with item's `gsm`/`threadCount`,
    `onHand` = 1, 3 `product_material` rows, 3 pieces in the first WORK stage.
  - `refusesMoreThanOnHand` — 4 × 3 on 10 M → 400 body detail "Only 10 M left"; no product rows created.
  - `refusesInPieces` — PCS item with 2 on hand, pieces 1 + 2 → "Only 2 PCS left".
  - `needsAWorkStage` — all stages non-WORK → 400 "Add a work stage in Settings first".
  - `boardShowsPiecesByStageAndFilters` — pieces appear in `BoardColumn.pieces`; `staffId` of another staff →
    none; `q` matching the stock name → present.
  - `publishedPieceLeavesBoard` — set product `PUBLISHED` → not in board.
  - `removedStageStillShowsPiece` — deactivate the piece's stage → column with `removed = true` holds it.
- [ ] **Step 2: Implement** `make`: compute total, compare with `inventory.get(id).onHand()`, throw
  `OrderService.bad("Only " + onHand.stripTrailingZeros().toPlainString() + " " + unit + " left")`; first WORK
  stage = first of `lists.stages()` with kind `WORK`; per piece build `ProductForm(name, null, null, null,
  categorySlug, null, null, null, false, null, null, item.gsm(), item.threadCount(), List.of(), Map.of(),
  List.of(), List.of())`, `create`, `addMaterial`, then `INSERT workshop_piece … RETURNING id` with
  `material_id` = the latest `product_material.id` for that product (select it; don't change `addMaterial`).
- [ ] **Step 3: Implement** board/overview: `board` groups `openPieces` by stage id alongside items (removed-stage
  logic shared); `overview` stage loads and unassigned count add pieces in WORK stages.
- [ ] **Step 4: Check** — Build Project compiles; `ModularityTests` would pass by inspection (tailoring imports
  only exported types: `AdminProductService`, `ProductForm`, `AdminProduct`, `InventoryService`, `StockItem`,
  `ProductMakingService`, `ProductMaking`). If Modulith needs `@NamedInterface` for these, add it.

### Task 4: Pieces — assign, move with work log, note, design photos (backend)

**Files:**
- Modify: `tailoring/PieceService.java`, `tailoring/WorkshopService.java` (`move`), `tailoring/BulkMove.java`,
  `tailoring/TailoringImage.java` (add `PIECE("workshop/pieces/")`), `tailoring/WorkshopController.java`
- Test: extend `PiecesIT`

**Interfaces:**
- Consumes: Task 3 types; `ProductMakingService.addWork(long productId, ProductWorkForm)`;
  `TailoringImage.start(Purpose)`, `requireUploaded(Purpose, String)`.
- Produces:
  - `BulkMove(@NotNull Long stageId, @NotNull @Size(max = 100) List<@NotNull Long> itemIds,
    @Nullable @Size(max = 100) List<@NotNull Long> pieceIds, @Nullable @Valid StageWork work)`; compact
    constructor defaults `pieceIds` to `List.of()`; `@AssertTrue` "Pick something to move" when both empty
    (replaces `itemIds` `min = 1`).
  - `record StageWork(@Nullable Long staffId, @NotBlank @Size(max = 120) String workerName,
    @NotNull @DecimalMin("0") @DecimalMax("1000000") @Digits(integer = 7, fraction = 2) BigDecimal costRupees)`
  - `PieceService.assign(long pieceId, @Nullable Long staffId): WorkPiece` — same rules as
    `WorkshopService.assign` (WORK stage only, `staff_stage` limits).
  - `PieceService.move(List<Long> pieceIds, long stageId, @Nullable StageWork work, @Nullable String actor)` —
    target `DELIVERED` → 400 "Publish the product instead"; `work` only with exactly one piece, no items, leaving a
    WORK stage, else 400 "Work can only be logged for one piece leaving a work stage"; writes
    `workshop_piece_stage_log` (staff = assignee, actor), sets `stage_since = now()`, clears assignee; with `work`
    calls `addWork(productId, new ProductWorkForm(staffId, workerName, fromStageName, costRupees, today, null))`.
  - `PieceService.setNote(long pieceId, @Nullable String note): WorkPiece`
  - `PieceService.startPhotoUpload(long pieceId)`, `addPhoto(long pieceId, String key): WorkPiece` (≤ 6),
    `removePhoto(long pieceId, long photoId): WorkPiece`.
  - Missing piece or product not `DRAFT` → 404 "That piece is no longer on the board".
  - Endpoints: `PUT /pieces/{id}/assignee` (`assignWorkPiece`, body `AssignForm`), `PUT /pieces/{id}/note`
    (`setWorkPieceNote`, body `record PieceNote(@Nullable @Size(max = 200) String note)`),
    `POST /pieces/{id}/photos/upload` (`startWorkPiecePhotoUpload`), `POST /pieces/{id}/photos`
    (`addWorkPiecePhoto`, body `{key}`), `DELETE /pieces/{id}/photos/{photoId}` (`removeWorkPiecePhoto`); all under
    `/api/v1/admin/workshop`. `items/move` calls `PieceService.move` for `pieceIds` in the same transaction.

- [ ] **Step 1: Write tests** — `movingWithWorkLogsCost` (Embroidery→Stitching with ₹800 → product making cost
  includes 800, work type "Embroidery"); `bulkMoveRejectsWork` (2 pieces + work → 400); `cannotDeliverAPiece`;
  `assignRespectsStaffStages`; `publishedPieceIs404OnMove`; `seventhDesignPhotoRejected`.
- [ ] **Step 2: Implement** per Interfaces.
- [ ] **Step 3: Check** — Build Project compiles.

### Task 5: API client

**Files:**
- Regenerate: `frontend/packages/api-client/openapi.json`, `src/schema.d.ts` (`pnpm generate:api`, backend running)
- Modify: `frontend/packages/api-client/src/index.ts`

**Interfaces:**
- Produces (types): `WorkPiece`, `MadePiece` (= `Schemas["Made"]` or whatever name springdoc gives — use the
  generated name), `StockPhoto`, `PhotoUpload`.
- Produces (`api.admin.*`): `startStockPhotoUpload(itemId)`, `addStockPhoto(itemId, key)`,
  `removeStockPhoto(itemId, photoId)` → `StockItem`; `makePieces({stockItemId, pieces})` → `MadePiece[]`;
  `getPieceByProduct(productId)` → `WorkPiece`; `assignWorkPiece(pieceId, staffId | null)`;
  `setWorkPieceNote(pieceId, note)`; `startWorkPiecePhotoUpload(pieceId)`, `addWorkPiecePhoto(pieceId, key)`,
  `removeWorkPiecePhoto(pieceId, photoId)` → `WorkPiece`; `moveWorkItems` gains optional `pieceIds` and `work`.

- [ ] **Step 1:** Ask the owner to have the backend running with the new code (IntelliJ), then `pnpm generate:api`.
- [ ] **Step 2:** Add the wrappers next to the existing workshop/stock ones (same `send`/`request` helpers).
- [ ] **Step 3: Check** — `pnpm typecheck --filter @vaabhi/api-client`; update `src/index.test.ts` fixtures if
  types changed.

### Task 6: Admin — stock photos

**Files:**
- Create: `frontend/admin/src/components/photo-strip.tsx`
- Modify: `frontend/admin/src/lib/photo-upload.ts` (add `uploadKeyedPhoto`), `app/stock/page.tsx`,
  `app/stock/view/page.tsx`
- Test: `frontend/admin/src/components/photo-strip.test.tsx`

**Interfaces:**
- Produces:
  - `uploadKeyedPhoto(file: File, start: () => Promise<{key: string; uploadUrl: string}>,
    resize = resizeToWebp): Promise<string>` — same body as `uploadTailoringPhoto` but with the injected
    `start`; refactor `uploadTailoringPhoto` to call it.
  - `<PhotoStrip photos={{id, key}[]} max={6} onAdd={(file) => Promise<void>} onRemove={(id) => Promise<void>} />`
    — thumbnails via `mediaUrl(key)`, add tile hidden at `max`, tap to enlarge, errors shown inline. Reused by the
    piece sheet (Task 8).
- [ ] **Step 1: Write tests** — hides add tile at 6; calls `onRemove(id)`; shows the error message when `onAdd`
  rejects.
- [ ] **Step 2: Implement**; stock view shows the strip; stock list row shows first photo thumbnail; stock form
  (edit only — new items have no id yet) shows the strip.
- [ ] **Step 3: Check** — `pnpm typecheck --filter admin && pnpm lint --filter admin`.

### Task 7: Admin — Make products dialog and "Made into"

**Files:**
- Create: `frontend/admin/src/components/make-products.tsx`
- Modify: `app/stock/view/page.tsx`
- Test: `frontend/admin/src/components/make-products.test.tsx`

**Interfaces:**
- Consumes: `api.admin.makePieces`, `api.admin.catalogOptions()` (categories), `formatQuantity`/`unitLabel` from
  `lib/extras.ts`.
- Produces: `<MakeProducts item={StockItem} onMade={(made: MadePiece[]) => void} />` and exported pure helper
  `leftAfter(onHand: number, rows: {quantity: number | null}[]): number`.
- [ ] **Step 1: Write tests** — `leftAfter(10, [{3},{3},{3}]) === 1`; Save disabled when left < 0 or a row lacks
  name/category/quantity; on success lists the created products as links to the product editor and an "Open
  workshop board" link to `/workshop/board/`.
- [ ] **Step 2: Implement**; button "Make products" on stock view (hidden when on hand ≤ 0); after `onMade`
  reload "Made into".
- [ ] **Step 3:** "Made into" rows show product status chip and, for drafts, the board stage (from
  `getPieceByProduct`, ignore 404).
- [ ] **Step 4: Check** — typecheck + lint (admin).

### Task 8: Admin — piece cards, piece sheet, stage-leave prompt

**Files:**
- Create: `frontend/admin/src/components/piece-card.tsx`, `components/piece-sheet.tsx`,
  `components/stage-work-prompt.tsx`
- Modify: `app/workshop/board/page.tsx`, `src/test/tailoring-fixtures.ts` (add pieces to `board`)
- Test: extend `app/workshop/workshop.test.tsx`

**Interfaces:**
- Consumes: `BoardColumn.pieces`, `api.admin.moveWorkItems({stageId, itemIds, pieceIds, work})`,
  `assignWorkPiece`, `setWorkPieceNote`, piece photo calls, `<PhotoStrip>` (Task 6), `sharePiece` (Task 9).
- Produces: `<PieceCard piece selected onSelect onOpen />` ("Stock" chip, fabric thumbnail, name, stock name +
  quantity, assignee; in a READY stage an "Add photos & publish" link to the product editor);
  `<PieceSheet piece onChange onClose />`; `<StageWorkPrompt stageName assignee onSave(work) onSkip />`.
- [ ] **Step 1: Write tests** — board renders a piece card in its column with "Stock"; selecting an order item
  and a piece then bulk-moving sends both id lists and no `work`; moving one piece out of a WORK stage opens the
  prompt prefilled with the assignee, Save sends `work` with the cost, Skip sends none; READY-stage card shows
  "Add photos & publish".
- [ ] **Step 2: Implement**; selection state keeps items and pieces apart (`Set<number>` each).
- [ ] **Step 3: Check** — typecheck + lint (admin).

### Task 9: Admin — WhatsApp share

**Files:**
- Create: `frontend/admin/src/lib/piece-share.ts`
- Modify: `components/piece-sheet.tsx` (Share button), `app/workshop/board/page.tsx` (toast after assign)
- Test: `frontend/admin/src/lib/piece-share.test.ts`

**Interfaces:**
- Produces:
  - `pieceMessage(piece: WorkPiece): string` — exact format from the spec; lines with no data omitted;
    width as `44"`; dimensions joined with ` · `.
  - `sharePiece(piece: WorkPiece, deps = {nav: navigator, fetch, open: window.open}): Promise<"shared" | "link" |
    "cancelled">` — photo URLs = design photos then first stock photo via `mediaUrl`; fetch each to `File`
    (`image/webp`); if `nav.canShare?.({files})` → `nav.share({files, text})`; `AbortError` → `"cancelled"`;
    otherwise `open("https://wa.me/" + digits(assignedPhone ?? "") + "?text=" + encodeURIComponent(text +
    "\n" + urls.join("\n")))`. Failed fetches drop to links appended to the text.
- [ ] **Step 1: Write tests** — message for a full piece equals the spec example; omits Fabric line parts that
  are null; `canShare` true → `share` called with 3 files; one fetch rejects → shares 2 files and text contains
  that photo's URL; no `canShare` → opens `https://wa.me/919876543210?text=…`; AbortError → `"cancelled"`.
- [ ] **Step 2: Implement**; toast "Share with {name} on WhatsApp" after a successful piece assign.
- [ ] **Step 3: Check** — typecheck + lint (admin).

### Task 10: Admin — product editor Making section + CORS note

**Files:**
- Modify: `frontend/admin/src/components/product-making.tsx`, `infra/vps-setup.md` (CORS block)

**Interfaces:**
- Consumes: `api.admin.getPieceByProduct` (404 → no piece), `WorkPiece.photoKey`, `stage`.
- [ ] **Step 1:** Materials row shows the stock thumbnail (`mediaUrl(photoKey)`) beside "Cut from {stock},
  {qty} {unit}"; header shows "In workshop: {stage}" with a link to `/workshop/board/` when a piece exists.
- [ ] **Step 2:** `infra/vps-setup.md` CORS: `"AllowedMethods": ["PUT", "GET"]`, add a line that GET is for
  sharing design photos from the admin. Tell the owner to apply it in the Cloudflare dashboard.
- [ ] **Step 3: Final check** — `pnpm typecheck` and `pnpm lint` from `frontend/` clean; IntelliJ Build Project
  clean. Hand the owner the six manual checks from the spec's Testing section plus "apply R2 CORS GET".
