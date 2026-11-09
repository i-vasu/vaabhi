package com.vaabhi.tailoring;

import com.vaabhi.catalog.AdminProductService;
import com.vaabhi.catalog.ProductForm;
import com.vaabhi.inventory.InventoryService;
import com.vaabhi.inventory.ProductMaking;
import com.vaabhi.inventory.ProductMakingService;
import com.vaabhi.inventory.StockItem;
import com.vaabhi.settings.ProductionStage;
import com.vaabhi.settings.ShopListService;
import com.vaabhi.staff.StaffDirectory;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Pieces cut from stock ("Make products"): each becomes a draft product and moves through the workshop stages like
 * an order item, until the product is published. Leaving a work stage can log who did the work and what it cost on
 * the product.
 */
@Service
public class PieceService {

    static final int MAX_PHOTOS = 6;
    private static final String ID = "id";

    private final JdbcClient jdbc;
    private final AdminProductService products;
    private final InventoryService inventory;
    private final ProductMakingService making;
    private final ShopListService lists;
    private final StaffDirectory directory;
    private final TailoringImage images;
    private final Clock clock;

    PieceService(
            JdbcClient jdbc,
            AdminProductService products,
            InventoryService inventory,
            ProductMakingService making,
            ShopListService lists,
            StaffDirectory directory,
            TailoringImage images,
            Clock clock) {
        this.jdbc = jdbc;
        this.products = products;
        this.inventory = inventory;
        this.making = making;
        this.lists = lists;
        this.directory = directory;
        this.images = images;
        this.clock = clock;
    }

    // --- make products ------------------------------------------------------------------------------------------

    /** Cuts the stock item into pieces: a draft product, its material (out of stock) and a board card for each. */
    @Transactional
    public List<MakePieces.Made> make(MakePieces form, @Nullable String actor) {
        var item = inventory.get(form.stockItemId());
        var total = form.pieces().stream().map(MakePieces.PieceForm::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(item.onHand()) > 0) {
            throw OrderService.bad(
                    "Only " + item.onHand().stripTrailingZeros().toPlainString() + " " + item.unit() + " left");
        }
        var firstStage = lists.stages().stream()
                .filter(stage -> stage.kind() == ProductionStage.Kind.WORK)
                .findFirst()
                .orElseThrow(() -> OrderService.bad("Add a work stage in Settings first"));
        var made = new ArrayList<MakePieces.Made>();
        for (var piece : form.pieces()) {
            var product = products.create(new ProductForm(
                    piece.name().trim(),
                    null,
                    null,
                    null,
                    piece.categorySlug(),
                    null,
                    null,
                    null,
                    false,
                    null,
                    null,
                    item.gsm(),
                    item.threadCount(),
                    List.of(),
                    Map.of(),
                    List.of(),
                    List.of()));
            making.addMaterial(
                    product.id(), new ProductMaking.ProductMaterialForm(item.id(), piece.quantity(), null), actor);
            var pieceId = jdbc.sql("""
                            INSERT INTO workshop_piece (product_id, stock_item_id, material_id, stage_id, note,
                                created_by)
                            VALUES (:product, :item,
                                (SELECT MAX(id) FROM product_material WHERE product_id = :product),
                                :stage, :note, :actor)
                            RETURNING id
                            """)
                    .param("product", product.id())
                    .param("item", item.id())
                    .param("stage", firstStage.id())
                    .param("note", ClientService.blankToNull(piece.note()))
                    .param("actor", actor)
                    .query(Long.class)
                    .single();
            made.add(new MakePieces.Made(pieceId, product.id(), product.name(), product.slug()));
        }
        return made;
    }

    // --- reading ------------------------------------------------------------------------------------------------

    private record PieceRow(
            long pieceId,
            long productId,
            String productName,
            String productSlug,
            String productStatus,
            long stockItemId,
            String stockItemName,
            BigDecimal quantity,
            StockItem.Unit unit,
            @Nullable String photoKey,
            long stageId,
            String stageName,
            ProductionStage.Kind stageKind,
            boolean stageActive,
            OffsetDateTime stageSince,
            @Nullable Long assignedStaffId,
            @Nullable String assignedStaffName,
            @Nullable String assignedPhone,
            @Nullable String note,
            @Nullable String material,
            @Nullable String colour,
            @Nullable BigDecimal widthIn,
            @Nullable Integer gsm,
            @Nullable Integer threadCount) {}

    /** Pieces whose product is still a draft (once published, a piece is off the board). */
    private static final String OPEN_PIECES = """
            SELECT wp.id AS piece_id, p.id AS product_id, p.name AS product_name, p.slug AS product_slug,
                   p.status AS product_status, i.id AS stock_item_id, i.name AS stock_item_name,
                   COALESCE(pm.quantity, 0) AS quantity, i.unit,
                   (SELECT ph.photo_key FROM stock_item_photo ph WHERE ph.stock_item_id = i.id
                    ORDER BY ph.position, ph.id LIMIT 1) AS photo_key,
                   st.id AS stage_id, st.name AS stage_name, st.kind AS stage_kind, st.active AS stage_active,
                   wp.stage_since, wp.assigned_staff_id, s.name AS assigned_staff_name, s.phone AS assigned_phone,
                   wp.note, i.material, i.colour, i.width_in, COALESCE(p.gsm, i.gsm) AS gsm,
                   COALESCE(p.thread_count, i.thread_count) AS thread_count
            FROM workshop_piece wp
            JOIN product p ON p.id = wp.product_id
            JOIN stock_item i ON i.id = wp.stock_item_id
            LEFT JOIN product_material pm ON pm.id = wp.material_id
            JOIN production_stage st ON st.id = wp.stage_id
            LEFT JOIN staff_account s ON s.id = wp.assigned_staff_id
            WHERE p.status = 'DRAFT'
            """;

    private record PhotoRow(long pieceId, long id, String photoKey) {}

    private record DimensionRow(long productId, String label, BigDecimal value, String unit) {}

    private List<WorkPiece> toPieces(List<PieceRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        var pieceIds = rows.stream().map(PieceRow::pieceId).toArray(Long[]::new);
        var productIds = rows.stream().map(PieceRow::productId).toArray(Long[]::new);
        Map<Long, List<WorkPiece.Photo>> photos =
                jdbc.sql("""
                        SELECT piece_id, id, photo_key FROM workshop_piece_photo
                        WHERE piece_id = ANY(:ids) ORDER BY position, id
                        """).param("ids", pieceIds).query(PhotoRow.class).list().stream()
                        .collect(Collectors.groupingBy(
                                PhotoRow::pieceId,
                                Collectors.mapping(
                                        row -> new WorkPiece.Photo(row.id(), row.photoKey()), Collectors.toList())));
        Map<Long, List<WorkPiece.Dimension>> dimensions =
                jdbc.sql("""
                        SELECT product_id, label, value, unit FROM product_dimension
                        WHERE product_id = ANY(:ids) ORDER BY position, code
                        """).param("ids", productIds).query(DimensionRow.class).list().stream()
                        .collect(Collectors.groupingBy(
                                DimensionRow::productId,
                                Collectors.mapping(
                                        row -> new WorkPiece.Dimension(row.label(), row.value(), row.unit()),
                                        Collectors.toList())));
        return rows.stream()
                .map(row -> new WorkPiece(
                        row.pieceId(),
                        row.productId(),
                        row.productName(),
                        row.productSlug(),
                        row.productStatus(),
                        row.stockItemId(),
                        row.stockItemName(),
                        row.quantity(),
                        row.unit(),
                        row.photoKey(),
                        new ProductionStage(row.stageId(), row.stageName(), row.stageKind()),
                        row.stageSince(),
                        row.assignedStaffId() == null
                                ? null
                                : new Assignee(row.assignedStaffId(), String.valueOf(row.assignedStaffName())),
                        row.assignedPhone(),
                        row.note(),
                        photos.getOrDefault(row.pieceId(), List.of()),
                        new WorkPiece.Fabric(row.material(), row.colour(), row.widthIn(), row.gsm(), row.threadCount()),
                        dimensions.getOrDefault(row.productId(), List.of())))
                .toList();
    }

    /** Stage id → whether that stage is still active, for the pieces on the board. */
    record Open(List<WorkPiece> pieces, Map<Long, Boolean> stageActive) {}

    /** Pieces on the board, optionally only one person's or matching a search, oldest first. */
    @Transactional(readOnly = true)
    public Open openPieces(@Nullable Long staffId, @Nullable String query) {
        var rows = jdbc.sql(OPEN_PIECES + """
                          AND (CAST(:staff AS bigint) IS NULL OR wp.assigned_staff_id = :staff)
                          AND (CAST(:q AS varchar) IS NULL OR lower(p.name) LIKE :q OR lower(i.name) LIKE :q)
                        ORDER BY wp.created_at, wp.id
                        """)
                .param("staff", staffId)
                .param("q", ClientService.like(query))
                .query(PieceRow.class)
                .list();
        Map<Long, Boolean> active =
                rows.stream().collect(Collectors.toMap(PieceRow::stageId, PieceRow::stageActive, (a, b) -> a));
        return new Open(toPieces(rows), active);
    }

    private PieceRow row(long pieceId) {
        return jdbc.sql(OPEN_PIECES + " AND wp.id = :id")
                .param(ID, pieceId)
                .query(PieceRow.class)
                .optional()
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "That piece is no longer on the board"));
    }

    @Transactional(readOnly = true)
    public WorkPiece get(long pieceId) {
        return toPieces(List.of(row(pieceId))).getFirst();
    }

    /** The piece a product is being made as, while it is on the board. */
    @Transactional(readOnly = true)
    public WorkPiece byProduct(long productId) {
        var pieceId = jdbc.sql(OPEN_PIECES + " AND p.id = :id")
                .param(ID, productId)
                .query(PieceRow.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not in the workshop"))
                .pieceId();
        return get(pieceId);
    }

    // --- assigning and moving -----------------------------------------------------------------------------------

    /** Gives a piece in a work stage to a staff member allowed to work that stage (or takes it back). */
    @Transactional
    public WorkPiece assign(long pieceId, @Nullable Long staffId) {
        var piece = row(pieceId);
        if (staffId != null) {
            if (piece.stageKind() != ProductionStage.Kind.WORK) {
                throw OrderService.bad("Only pieces in a work stage can be given to someone");
            }
            var person = directory.findActive(staffId).orElseThrow(() -> OrderService.bad("Unknown staff member"));
            var allowed = jdbc.sql("SELECT stage_id FROM staff_stage WHERE staff_id = :id")
                    .param(ID, staffId)
                    .query(Long.class)
                    .list();
            if (!allowed.isEmpty() && !allowed.contains(piece.stageId())) {
                throw OrderService.bad(person.name() + " does not work " + piece.stageName());
            }
        }
        jdbc.sql("""
                        UPDATE workshop_piece SET assigned_staff_id = :staff,
                            assigned_at = CASE WHEN CAST(:staff AS bigint) IS NULL THEN NULL ELSE now() END
                        WHERE id = :id
                        """).param("staff", staffId).param(ID, pieceId).update();
        return get(pieceId);
    }

    /**
     * Moves pieces to a stage. With {@code work} (one piece leaving a work stage) the work is logged on the product
     * under the stage's name.
     */
    @Transactional
    public void move(
            Collection<Long> pieceIds, long stageId, MakePieces.@Nullable StageWork work, @Nullable String actor) {
        if (pieceIds.isEmpty()) {
            return;
        }
        var stage = lists.stages().stream()
                .filter(s -> s.id() != null && s.id() == stageId)
                .findFirst()
                .orElseThrow(() -> OrderService.bad("Unknown stage"));
        if (stage.kind() == ProductionStage.Kind.DELIVERED) {
            throw OrderService.bad("Publish the product instead");
        }
        var pieces = pieceIds.stream().distinct().map(this::row).toList();
        if (work != null) {
            if (pieces.size() != 1
                    || pieces.getFirst().stageKind() != ProductionStage.Kind.WORK
                    || Objects.equals(pieces.getFirst().stageId(), stageId)) {
                throw OrderService.bad("Work can only be logged for one piece leaving a work stage");
            }
            var piece = pieces.getFirst();
            making.addWork(
                    piece.productId(),
                    new ProductMaking.ProductWorkForm(
                            work.staffId(),
                            work.workerName().trim(),
                            piece.stageName(),
                            work.costRupees(),
                            LocalDate.now(clock),
                            null));
        }
        var ids = pieces.stream().map(PieceRow::pieceId).toArray(Long[]::new);
        jdbc.sql("""
                        INSERT INTO workshop_piece_stage_log (piece_id, from_stage_id, to_stage_id, staff_id, actor)
                        SELECT id, stage_id, :stage, assigned_staff_id, :actor FROM workshop_piece
                        WHERE id = ANY(:ids) AND stage_id IS DISTINCT FROM :stage
                        """)
                .param("stage", stageId)
                .param("actor", actor)
                .param("ids", ids)
                .update();
        jdbc.sql("""
                        UPDATE workshop_piece SET stage_id = :stage, stage_since = now(), assigned_staff_id = NULL,
                            assigned_at = NULL
                        WHERE id = ANY(:ids) AND stage_id IS DISTINCT FROM :stage
                        """).param("stage", stageId).param("ids", ids).update();
    }

    // --- note and design photos ---------------------------------------------------------------------------------

    @Transactional
    public WorkPiece setNote(long pieceId, @Nullable String note) {
        row(pieceId);
        jdbc.sql("UPDATE workshop_piece SET note = :note WHERE id = :id")
                .param("note", ClientService.blankToNull(note))
                .param(ID, pieceId)
                .update();
        return get(pieceId);
    }

    TailoringImageUpload startPhotoUpload(long pieceId) {
        row(pieceId);
        return images.start(TailoringImage.Purpose.PIECE);
    }

    @Transactional
    public WorkPiece addPhoto(long pieceId, String key) {
        var piece = get(pieceId);
        images.requireUploaded(TailoringImage.Purpose.PIECE, key.trim());
        if (piece.photos().size() >= MAX_PHOTOS) {
            throw OrderService.bad("Up to " + MAX_PHOTOS + " photos");
        }
        jdbc.sql("""
                        INSERT INTO workshop_piece_photo (piece_id, photo_key, position)
                        SELECT :piece, :key, COALESCE(MAX(position), -1) + 1 FROM workshop_piece_photo
                        WHERE piece_id = :piece
                        """).param("piece", pieceId).param("key", key.trim()).update();
        return get(pieceId);
    }

    @Transactional
    public WorkPiece removePhoto(long pieceId, long photoId) {
        row(pieceId);
        if (jdbc.sql("DELETE FROM workshop_piece_photo WHERE id = :id AND piece_id = :piece")
                        .param(ID, photoId)
                        .param("piece", pieceId)
                        .update()
                == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found");
        }
        return get(pieceId);
    }
}
