package com.vaabhi.inventory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
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
            SELECT i.id, i.name, i.sku, i.category, i.unit, i.reorder_level, i.cost_paise,
                   COALESCE(SUM(m.quantity), 0) AS on_hand
            FROM stock_item i LEFT JOIN stock_movement m ON m.item_id = i.id
            """;

    private final JdbcClient jdbc;

    InventoryService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private record ItemRow(
            long id,
            String name,
            @Nullable String sku,
            @Nullable String category,
            StockItem.Unit unit,
            BigDecimal reorderLevel,
            long costPaise,
            BigDecimal onHand) {

        StockItem item() {
            return new StockItem(
                    id,
                    name,
                    sku,
                    category,
                    unit,
                    reorderLevel,
                    costPaise,
                    onHand,
                    onHand.compareTo(reorderLevel) <= 0,
                    onHand.signum() <= 0
                            ? 0
                            : onHand.multiply(BigDecimal.valueOf(costPaise))
                                    .setScale(0, RoundingMode.HALF_UP)
                                    .longValueExact());
        }
    }

    /** Active items, optionally only those running low or matching a search, by category and name. */
    @Transactional(readOnly = true)
    public List<StockItem> list(@Nullable String query, boolean lowOnly) {
        var items = jdbc.sql(ITEMS + """
                        WHERE i.active
                          AND (CAST(:q AS varchar) IS NULL OR lower(i.name) LIKE :q OR lower(i.sku) LIKE :q
                               OR lower(i.category) LIKE :q)
                        GROUP BY i.id ORDER BY lower(coalesce(i.category, '')), lower(i.name)
                        """).param("q", like(query)).query(ItemRow.class).list().stream()
                .map(ItemRow::item);
        return (lowOnly ? items.filter(StockItem::low) : items).toList();
    }

    @Transactional(readOnly = true)
    public StockItem get(long id) {
        return jdbc.sql(ITEMS + " WHERE i.id = :id AND i.active GROUP BY i.id")
                .param("id", id)
                .query(ItemRow.class)
                .optional()
                .map(ItemRow::item)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock item not found"));
    }

    @Transactional
    public StockItem save(@Nullable Long id, StockItem.StockItemForm form) {
        try {
            if (id == null) {
                id = jdbc.sql("""
                                INSERT INTO stock_item (name, sku, category, unit, reorder_level, cost_paise)
                                VALUES (:name, :sku, :category, :unit, :reorder, :cost) RETURNING id
                                """)
                        .param("name", form.name().trim())
                        .param("sku", blankToNull(form.sku()))
                        .param("category", blankToNull(form.category()))
                        .param("unit", form.unit().name())
                        .param("reorder", form.reorderLevel())
                        .param("cost", form.costPaise())
                        .query(Long.class)
                        .single();
            } else {
                get(id);
                jdbc.sql("""
                                UPDATE stock_item SET name = :name, sku = :sku, category = :category, unit = :unit,
                                    reorder_level = :reorder, cost_paise = :cost
                                WHERE id = :id
                                """)
                        .param("name", form.name().trim())
                        .param("sku", blankToNull(form.sku()))
                        .param("category", blankToNull(form.category()))
                        .param("unit", form.unit().name())
                        .param("reorder", form.reorderLevel())
                        .param("cost", form.costPaise())
                        .param("id", id)
                        .update();
            }
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An item with this name or SKU already exists", e);
        }
        return get(id);
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
