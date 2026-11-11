package com.vaabhi.inventory;

import com.vaabhi.party.PartyService;
import com.vaabhi.settings.NumberSequence;
import com.vaabhi.settings.NumberingService;
import com.vaabhi.shared.rupees.Rupees;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Stock items and their movements. What is on hand is the sum of an item's movements. */
@Service
public class InventoryService {

    private static final String ITEMS = """
            SELECT i.id, i.name, i.sku, i.category, i.unit, i.reorder_level, i.cost_rupees,
                   COALESCE(SUM(m.quantity), 0) AS on_hand, i.supplier_name, i.supplier_phone, i.material,
                   i.colour, i.gsm, i.thread_count, i.width_in, i.notes
            FROM stock_item i LEFT JOIN stock_movement m ON m.item_id = i.id
            """;

    private final JdbcClient jdbc;
    private final NumberingService numbering;
    private final PartyService parties;

    InventoryService(JdbcClient jdbc, NumberingService numbering, PartyService parties) {
        this.jdbc = jdbc;
        this.numbering = numbering;
        this.parties = parties;
    }

    private record ItemRow(
            long id,
            String name,
            @Nullable String sku,
            @Nullable String category,
            StockItem.Unit unit,
            BigDecimal reorderLevel,
            BigDecimal costRupees,
            BigDecimal onHand,
            @Nullable String supplierName,
            @Nullable String supplierPhone,
            @Nullable String material,
            @Nullable String colour,
            @Nullable Integer gsm,
            @Nullable Integer threadCount,
            @Nullable BigDecimal widthIn,
            @Nullable String notes) {

        StockItem item(List<StockItem.StockPhoto> photos) {
            return new StockItem(
                    id,
                    name,
                    sku,
                    category,
                    unit,
                    reorderLevel,
                    costRupees,
                    onHand,
                    onHand.compareTo(reorderLevel) <= 0,
                    onHand.signum() <= 0 ? Rupees.ZERO : Rupees.times(costRupees, onHand),
                    supplierName,
                    supplierPhone,
                    material,
                    colour,
                    gsm,
                    threadCount,
                    widthIn,
                    notes,
                    photos);
        }
    }

    private record PhotoRow(long stockItemId, long id, String photoKey) {}

    /** Reference photos of the given items, in order, by item id. One query for any number of items. */
    private Map<Long, List<StockItem.StockPhoto>> photosOf(List<Long> itemIds) {
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        return jdbc.sql("""
                        SELECT stock_item_id, id, photo_key FROM stock_item_photo
                        WHERE stock_item_id = ANY(:ids) ORDER BY position, id
                        """).param("ids", itemIds.toArray(Long[]::new)).query(PhotoRow.class).list().stream()
                .collect(Collectors.groupingBy(
                        PhotoRow::stockItemId,
                        Collectors.mapping(
                                row -> new StockItem.StockPhoto(row.id(), row.photoKey()), Collectors.toList())));
    }

    /** An item's reference photos, in order. */
    @Transactional(readOnly = true)
    public List<StockItem.StockPhoto> photos(long itemId) {
        return photosOf(List.of(itemId)).getOrDefault(itemId, List.of());
    }

    /** Active items, optionally only those running low or matching a search, by category and name. */
    @Transactional(readOnly = true)
    public List<StockItem> list(@Nullable String query, boolean lowOnly) {
        var rows = jdbc.sql(ITEMS + """
                        WHERE i.active
                          AND (CAST(:q AS varchar) IS NULL OR lower(i.name) LIKE :q OR lower(i.sku) LIKE :q
                               OR lower(i.category) LIKE :q)
                        GROUP BY i.id ORDER BY lower(coalesce(i.category, '')), lower(i.name)
                        """)
                .param("q", like(query))
                .query(ItemRow.class)
                .list();
        var photos = photosOf(rows.stream().map(ItemRow::id).toList());
        var items = rows.stream().map(row -> row.item(photos.getOrDefault(row.id(), List.of())));
        return (lowOnly ? items.filter(StockItem::low) : items).toList();
    }

    @Transactional(readOnly = true)
    public StockItem get(long id) {
        return jdbc.sql(ITEMS + " WHERE i.id = :id AND i.active GROUP BY i.id")
                .param("id", id)
                .query(ItemRow.class)
                .optional()
                .map(row -> row.item(photos(row.id())))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock item not found"));
    }

    @Transactional
    public StockItem save(@Nullable Long id, StockItem.StockItemForm form) {
        try {
            var params = formParams(form);
            if (id == null) {
                // Every item gets a code (STK-0001…) for labels and search, unless one was typed.
                if (params.get("sku") == null) {
                    params.put("sku", numbering.next(NumberSequence.Kind.STOCK_ITEM));
                }
                id = jdbc.sql("""
                                INSERT INTO stock_item (name, sku, category, unit, reorder_level, cost_rupees,
                                    supplier_name, supplier_phone, material, colour, gsm, thread_count, width_in, notes)
                                VALUES (:name, :sku, :category, :unit, :reorder, :cost, :supplierName, :supplierPhone,
                                    :material, :colour, :gsm, :threadCount, :widthIn, :notes) RETURNING id
                                """).params(params).query(Long.class).single();
            } else {
                get(id);
                // A code once given stays when the field is left empty.
                jdbc.sql("""
                                UPDATE stock_item SET name = :name, sku = COALESCE(:sku, sku), category = :category,
                                    unit = :unit,
                                    reorder_level = :reorder, cost_rupees = :cost, supplier_name = :supplierName,
                                    supplier_phone = :supplierPhone, material = :material, colour = :colour,
                                    gsm = :gsm, thread_count = :threadCount, width_in = :widthIn, notes = :notes
                                WHERE id = :id
                                """).params(params).param("id", id).update();
            }
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An item with this name or SKU already exists", e);
        }
        // The supplier is a party (shared with customers and job workers by phone).
        var supplier = form.supplierName() == null || form.supplierName().isBlank()
                ? null
                : Long.valueOf(parties.save(
                        null,
                        PartyService.Details.of(form.supplierName(), form.supplierPhone()),
                        PartyService.Role.SUPPLIER));
        jdbc.sql("UPDATE stock_item SET supplier_party_id = :party WHERE id = :id")
                .param("party", supplier)
                .param("id", id)
                .update();
        return get(id);
    }

    private static Map<String, @Nullable Object> formParams(StockItem.StockItemForm form) {
        Map<String, @Nullable Object> params = new HashMap<>();
        params.put("name", form.name().trim());
        params.put("sku", blankToNull(form.sku()));
        params.put("category", blankToNull(form.category()));
        params.put("unit", form.unit().name());
        params.put("reorder", form.reorderLevel());
        params.put("cost", form.costRupees());
        params.put("supplierName", blankToNull(form.supplierName()));
        params.put("supplierPhone", blankToNull(form.supplierPhone()));
        params.put("material", blankToNull(form.material()));
        params.put("colour", blankToNull(form.colour()));
        params.put("gsm", form.gsm());
        params.put("threadCount", form.threadCount());
        params.put("widthIn", form.widthIn());
        params.put("notes", blankToNull(form.notes()));
        return params;
    }

    /** Takes the item off the lists; its movements stay for the ledger. */
    @Transactional
    public void remove(long id) {
        get(id);
        jdbc.sql("UPDATE stock_item SET active = FALSE WHERE id = :id")
                .param("id", id)
                .update();
    }

    /** Records a movement. Stock cannot go below zero. */
    @Transactional
    public StockMovement move(long itemId, StockMovement.StockMovementForm form, @Nullable String actor) {
        var item = get(itemId);
        var quantity = switch (form.kind()) {
            case IN -> form.quantity().abs();
            case OUT -> form.quantity().abs().negate();
            case ADJUST -> form.quantity();
        };
        if (quantity.signum() == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter how much");
        }
        if (item.onHand().add(quantity).signum() < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only " + item.onHand().stripTrailingZeros().toPlainString() + " " + item.unit() + " in stock");
        }
        if (form.orderId() != null
                && jdbc.sql("SELECT COUNT(*) FROM shop_order WHERE id = :id")
                                .param("id", form.orderId())
                                .query(Long.class)
                                .single()
                        == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That order does not exist");
        }
        long id = jdbc.sql("""
                        INSERT INTO stock_movement (item_id, kind, quantity, order_id, note, moved_by)
                        VALUES (:item, :kind, :quantity, :order, :note, :by) RETURNING id
                        """)
                .param("item", itemId)
                .param("kind", form.kind().name())
                .param("quantity", quantity)
                .param("order", form.orderId())
                .param("note", blankToNull(form.note()))
                .param("by", actor)
                .query(Long.class)
                .single();
        return ledger(itemId).stream().filter(m -> m.id() == id).findFirst().orElseThrow();
    }

    /** One item's movements, newest first, with the balance after each. */
    @Transactional(readOnly = true)
    public List<StockMovement> ledger(long itemId) {
        return jdbc.sql("""
                        SELECT m.id, m.item_id, i.name AS item_name, m.kind, m.quantity,
                               SUM(m.quantity) OVER (ORDER BY m.moved_at, m.id) AS balance,
                               m.order_id, o.number AS order_number, m.note, m.moved_by, m.moved_at
                        FROM stock_movement m JOIN stock_item i ON i.id = m.item_id
                        LEFT JOIN shop_order o ON o.id = m.order_id
                        WHERE m.item_id = :item
                        ORDER BY m.moved_at DESC, m.id DESC
                        """).param("item", itemId).query(StockMovement.class).list();
    }

    /** How many active items are at or below their reorder level (for notifications). */
    @Transactional(readOnly = true)
    public int lowStockCount() {
        return (int) list(null, true).size();
    }

    static @Nullable String like(@Nullable String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return "%"
                + query.trim()
                        .toLowerCase()
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_") + "%";
    }

    static @Nullable String blankToNull(@Nullable String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
